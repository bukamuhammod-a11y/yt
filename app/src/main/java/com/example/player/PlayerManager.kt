package com.example.player

import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.data.YouTubeCatalog
import com.example.model.PlaybackState
import com.example.model.YouTubeVideo
import com.example.service.BackgroundAudioService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object PlayerManager {

    private val _currentVideo = MutableStateFlow<YouTubeVideo?>(YouTubeCatalog.sampleVideos.firstOrNull())
    val currentVideo: StateFlow<YouTubeVideo?> = _currentVideo.asStateFlow()

    private val _playbackState = MutableStateFlow(PlaybackState.IDLE)
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val _currentPosition = MutableStateFlow(0f)
    val currentPosition: StateFlow<Float> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(312f)
    val duration: StateFlow<Float> = _duration.asStateFlow()

    private val _isBackgroundPlayEnabled = MutableStateFlow(true)
    val isBackgroundPlayEnabled: StateFlow<Boolean> = _isBackgroundPlayEnabled.asStateFlow()

    private val _isBlackScreenSaverActive = MutableStateFlow(false)
    val isBlackScreenSaverActive: StateFlow<Boolean> = _isBlackScreenSaverActive.asStateFlow()

    private val _isPiPActive = MutableStateFlow(false)
    val isPiPActive: StateFlow<Boolean> = _isPiPActive.asStateFlow()

    private var playerCommandListener: PlayerCommandListener? = null
    private var appContext: Context? = null

    interface PlayerCommandListener {
        fun onCommandPlay()
        fun onCommandPause()
        fun onCommandSeek(seconds: Float)
        fun onCommandLoadVideo(videoId: String)
        fun onCommandNext() {}
        fun onCommandPrev() {}
        fun onResumeTimers() {}
    }

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun registerCommandListener(listener: PlayerCommandListener) {
        playerCommandListener = listener
    }

    fun unregisterCommandListener(listener: PlayerCommandListener) {
        if (playerCommandListener == listener) {
            playerCommandListener = null
        }
    }

    fun playVideo(video: YouTubeVideo) {
        _currentVideo.value = video
        _currentPosition.value = 0f
        _duration.value = video.durationSeconds.toFloat()
        _playbackState.value = PlaybackState.BUFFERING

        playerCommandListener?.onCommandLoadVideo(video.id)
        updateService()
    }

    fun togglePlayPause() {
        if (_playbackState.value == PlaybackState.PLAYING) {
            pause()
        } else {
            play()
        }
    }

    fun play() {
        _playbackState.value = PlaybackState.PLAYING
        playerCommandListener?.onCommandPlay()
        updateService()
    }

    fun pause() {
        _playbackState.value = PlaybackState.PAUSED
        playerCommandListener?.onCommandPause()
        updateService()
    }

    fun seekTo(seconds: Float) {
        _currentPosition.value = seconds
        playerCommandListener?.onCommandSeek(seconds)
    }

    fun next() {
        playerCommandListener?.onCommandNext()
    }

    fun prev() {
        playerCommandListener?.onCommandPrev()
    }

    fun updateFromWebView(state: PlaybackState, positionSec: Float, durationSec: Float) {
        val stateChanged = _playbackState.value != state
        _playbackState.value = state
        if (positionSec >= 0) _currentPosition.value = positionSec
        if (durationSec > 0) _duration.value = durationSec
        if (stateChanged) {
            updateService()
        }
    }

    fun updateCurrentVideoFromWebView(video: YouTubeVideo) {
        if (_currentVideo.value?.id != video.id || _currentVideo.value?.title != video.title) {
            _currentVideo.value = video
            updateService()
        }
    }

    fun resumeTimers() {
        try {
            playerCommandListener?.onResumeTimers()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun setBackgroundPlayEnabled(enabled: Boolean) {
        _isBackgroundPlayEnabled.value = enabled
        if (!enabled) {
            stopService()
        } else {
            updateService()
        }
    }

    fun setBlackScreenSaverActive(active: Boolean) {
        _isBlackScreenSaverActive.value = active
    }

    fun setPiPActive(active: Boolean) {
        _isPiPActive.value = active
    }

    private fun updateService() {
        val context = appContext ?: return
        if (!_isBackgroundPlayEnabled.value) return

        val intent = Intent(context, BackgroundAudioService::class.java).apply {
            action = BackgroundAudioService.ACTION_UPDATE_STATE
            putExtra(BackgroundAudioService.EXTRA_TITLE, _currentVideo.value?.title ?: "YouTube Audio")
            putExtra(BackgroundAudioService.EXTRA_CHANNEL, _currentVideo.value?.channel ?: "YouTube")
            putExtra(BackgroundAudioService.EXTRA_IS_PLAYING, _playbackState.value == PlaybackState.PLAYING)
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (e: Exception) {
            // Service startup handle
        }
    }

    fun stopService() {
        val context = appContext ?: return
        val intent = Intent(context, BackgroundAudioService::class.java)
        context.stopService(intent)
    }
}
