package com.example.player

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import com.example.model.PlaybackState
import com.example.model.YouTubeVideo
import com.example.service.BackgroundAudioService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class BrowseVideoRequest(
    val videoId: String,
    val startSeconds: Int,
    val autoPlay: Boolean,
    val requestId: Long = SystemClock.elapsedRealtime()
)

object PlayerManager {

    private val _currentVideo = MutableStateFlow<YouTubeVideo?>(null)
    val currentVideo: StateFlow<YouTubeVideo?> = _currentVideo.asStateFlow()

    private val _playbackState = MutableStateFlow(PlaybackState.IDLE)
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val _currentPosition = MutableStateFlow(0f)
    val currentPosition: StateFlow<Float> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0f)
    val duration: StateFlow<Float> = _duration.asStateFlow()

    private val _isBackgroundPlayEnabled = MutableStateFlow(true)
    val isBackgroundPlayEnabled: StateFlow<Boolean> = _isBackgroundPlayEnabled.asStateFlow()

    private val _isBlackScreenSaverActive = MutableStateFlow(false)
    val isBlackScreenSaverActive: StateFlow<Boolean> = _isBlackScreenSaverActive.asStateFlow()

    private val _isPiPActive = MutableStateFlow(false)
    val isPiPActive: StateFlow<Boolean> = _isPiPActive.asStateFlow()

    private val _isMiniPlayerDismissed = MutableStateFlow(false)
    val isMiniPlayerDismissed: StateFlow<Boolean> = _isMiniPlayerDismissed.asStateFlow()

    private val _isMainOnWatchPage = MutableStateFlow(false)
    val isMainOnWatchPage: StateFlow<Boolean> = _isMainOnWatchPage.asStateFlow()

    private val _isBrowsePlayerActive = MutableStateFlow(false)
    val isBrowsePlayerActive: StateFlow<Boolean> = _isBrowsePlayerActive.asStateFlow()

    private val _browseVideoRequest = MutableStateFlow<BrowseVideoRequest?>(null)
    val browseVideoRequest: StateFlow<BrowseVideoRequest?> = _browseVideoRequest.asStateFlow()

    private var primaryCommandListener: PlayerCommandListener? = null
    private var browseCommandListener: PlayerCommandListener? = null
    private var appContext: Context? = null

    private var lastPlayingTimestampMs: Long = 0L
    private var explicitlyPausedByApp: Boolean = false

    interface PlayerCommandListener {
        fun onCommandPlay()
        fun onCommandPause()
        fun onCommandSeek(seconds: Float)
        fun onCommandLoadVideo(videoId: String)
        fun onCommandLoadVideoWithTime(videoId: String, startSeconds: Int) {
            onCommandLoadVideo(videoId)
        }
        fun onCommandNext() {}
        fun onCommandPrev() {}
        fun onResumeTimers() {}
    }

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun registerCommandListener(listener: PlayerCommandListener) {
        primaryCommandListener = listener
    }

    fun unregisterCommandListener(listener: PlayerCommandListener) {
        if (primaryCommandListener == listener) {
            primaryCommandListener = null
        }
    }

    fun registerBrowseCommandListener(listener: PlayerCommandListener) {
        browseCommandListener = listener
    }

    fun unregisterBrowseCommandListener(listener: PlayerCommandListener) {
        if (browseCommandListener == listener) {
            browseCommandListener = null
        }
    }

    private fun activeListener(): PlayerCommandListener? {
        return if (_isBrowsePlayerActive.value) browseCommandListener else primaryCommandListener
    }

    fun playVideo(video: YouTubeVideo) {
        clearBrowsePlayer()
        explicitlyPausedByApp = false
        _isMiniPlayerDismissed.value = false
        _currentVideo.value = video
        _currentPosition.value = 0f
        _duration.value = video.durationSeconds.toFloat()
        _playbackState.value = PlaybackState.BUFFERING

        primaryCommandListener?.onCommandLoadVideo(video.id)
        updateService()
    }

    fun togglePlayPause() {
        if (_playbackState.value == PlaybackState.PLAYING || _playbackState.value == PlaybackState.BUFFERING) {
            pause()
        } else {
            play()
        }
    }

    fun play() {
        explicitlyPausedByApp = false
        _isMiniPlayerDismissed.value = false
        val video = _currentVideo.value

        if (!_isMainOnWatchPage.value && !_isBrowsePlayerActive.value && video != null) {
            handoffToBrowsePlayer(video.id, _currentPosition.value, autoPlay = true)
            return
        }

        _playbackState.value = PlaybackState.PLAYING
        lastPlayingTimestampMs = SystemClock.elapsedRealtime()
        activeListener()?.onCommandPlay()
        updateService()
    }

    fun pause() {
        explicitlyPausedByApp = true
        _playbackState.value = PlaybackState.PAUSED
        activeListener()?.onCommandPause()
        updateService()
    }

    fun seekTo(seconds: Float) {
        _currentPosition.value = seconds
        activeListener()?.onCommandSeek(seconds)
    }

    fun next() {
        explicitlyPausedByApp = false
        _isMiniPlayerDismissed.value = false
        activeListener()?.onCommandNext()
    }

    fun prev() {
        explicitlyPausedByApp = false
        _isMiniPlayerDismissed.value = false
        activeListener()?.onCommandPrev()
    }

    fun dismissMiniPlayer() {
        _isMiniPlayerDismissed.value = true
        pause()
        clearBrowsePlayer()
    }

    fun openCurrentVideoInMainPlayer() {
        val video = _currentVideo.value ?: return
        val resumeSec = _currentPosition.value.toInt().coerceAtLeast(0)
        clearBrowsePlayer()
        _isMiniPlayerDismissed.value = false
        primaryCommandListener?.onCommandLoadVideoWithTime(video.id, resumeSec)
    }

    fun handoffToBrowsePlayer(videoId: String, positionSec: Float, autoPlay: Boolean) {
        if (videoId.isEmpty()) return
        _isBrowsePlayerActive.value = true
        _isMiniPlayerDismissed.value = false
        _playbackState.value = if (autoPlay) PlaybackState.BUFFERING else PlaybackState.PAUSED
        _browseVideoRequest.value = BrowseVideoRequest(
            videoId = videoId,
            startSeconds = positionSec.toInt().coerceAtLeast(0),
            autoPlay = autoPlay
        )
        updateService()
    }

    fun clearBrowsePlayer() {
        if (_isBrowsePlayerActive.value || _browseVideoRequest.value != null) {
            browseCommandListener?.onCommandPause()
            _isBrowsePlayerActive.value = false
            _browseVideoRequest.value = null
        }
    }

    fun updateMainWebViewWatchState(
        onWatch: Boolean,
        videoId: String,
        isPlaying: Boolean,
        positionSec: Float,
        durationSec: Float,
        title: String,
        channel: String
    ) {
        val wasOnWatch = _isMainOnWatchPage.value
        _isMainOnWatchPage.value = onWatch

        if (onWatch) {
            // Returning to watch page or picking a new video stops browse handoff player
            if (_isBrowsePlayerActive.value) {
                clearBrowsePlayer()
            }

            if (isPlaying) {
                explicitlyPausedByApp = false
                lastPlayingTimestampMs = SystemClock.elapsedRealtime()
            }

            val newState = if (isPlaying) PlaybackState.PLAYING else PlaybackState.PAUSED
            val stateChanged = _playbackState.value != newState
            _playbackState.value = newState
            if (positionSec >= 0f) _currentPosition.value = positionSec
            if (durationSec > 0f) _duration.value = durationSec

            if (videoId.isNotEmpty()) {
                val cleanTitle = title.replace("- YouTube", "").trim().ifEmpty { "YouTube Video" }
                val cleanChannel = channel.trim().ifEmpty { "YouTube" }
                val existing = _currentVideo.value
                val videoChanged = existing?.id != videoId

                if (videoChanged) {
                    _isMiniPlayerDismissed.value = false
                }
                if (videoChanged || existing?.title != cleanTitle || existing.channel != cleanChannel) {
                    _currentVideo.value = YouTubeVideo(
                        id = videoId,
                        title = cleanTitle,
                        channel = cleanChannel,
                        durationText = if (durationSec > 0f) formatSeconds(durationSec.toInt()) else "Video",
                        durationSeconds = if (durationSec > 0f) durationSec.toInt() else 300,
                        category = "YouTube"
                    )
                    updateService()
                } else if (stateChanged) {
                    updateService()
                }
            } else if (stateChanged) {
                updateService()
            }
        } else {
            // User navigated away from /watch to browse Home / Search / Subscriptions
            val recentlyPlaying = (SystemClock.elapsedRealtime() - lastPlayingTimestampMs) <= 1500L
            val activeVideo = _currentVideo.value

            if (wasOnWatch && !_isBrowsePlayerActive.value && !explicitlyPausedByApp && recentlyPlaying && activeVideo != null) {
                handoffToBrowsePlayer(activeVideo.id, _currentPosition.value, autoPlay = true)
            } else if (!_isBrowsePlayerActive.value && activeVideo != null && _playbackState.value == PlaybackState.PLAYING) {
                _playbackState.value = PlaybackState.PAUSED
                updateService()
            }
        }
    }

    fun updateBrowseWebViewState(
        videoId: String,
        isPlaying: Boolean,
        positionSec: Float,
        durationSec: Float,
        title: String,
        channel: String
    ) {
        if (!_isBrowsePlayerActive.value) return

        val currentReq = _browseVideoRequest.value
        val newState = when {
            isPlaying -> PlaybackState.PLAYING
            currentReq?.autoPlay == true && positionSec <= 0.1f && _playbackState.value == PlaybackState.BUFFERING -> PlaybackState.BUFFERING
            else -> PlaybackState.PAUSED
        }

        if (isPlaying) {
            lastPlayingTimestampMs = SystemClock.elapsedRealtime()
        }

        val stateChanged = _playbackState.value != newState
        _playbackState.value = newState
        if (positionSec >= 0f) _currentPosition.value = positionSec
        if (durationSec > 0f) _duration.value = durationSec

        if (videoId.isNotEmpty()) {
            val cleanTitle = title.replace("- YouTube", "").trim().ifEmpty { _currentVideo.value?.title ?: "YouTube Video" }
            val cleanChannel = channel.trim().ifEmpty { _currentVideo.value?.channel ?: "YouTube" }
            val existing = _currentVideo.value
            if (existing?.id != videoId || existing.title != cleanTitle || existing.channel != cleanChannel) {
                _currentVideo.value = YouTubeVideo(
                    id = videoId,
                    title = cleanTitle,
                    channel = cleanChannel,
                    durationText = if (durationSec > 0f) formatSeconds(durationSec.toInt()) else "Video",
                    durationSeconds = if (durationSec > 0f) durationSec.toInt() else 300,
                    category = "YouTube"
                )
                updateService()
            } else if (stateChanged) {
                updateService()
            }
        } else if (stateChanged) {
            updateService()
        }
    }

    fun updateFromWebView(state: PlaybackState, positionSec: Float, durationSec: Float) {
        val stateChanged = _playbackState.value != state
        _playbackState.value = state
        if (state == PlaybackState.PLAYING) {
            lastPlayingTimestampMs = SystemClock.elapsedRealtime()
            explicitlyPausedByApp = false
        }
        if (positionSec >= 0) _currentPosition.value = positionSec
        if (durationSec > 0) _duration.value = durationSec
        if (stateChanged) {
            updateService()
        }
    }

    fun updateCurrentVideoFromWebView(video: YouTubeVideo) {
        if (_currentVideo.value?.id != video.id || _currentVideo.value?.title != video.title) {
            if (_currentVideo.value?.id != video.id) {
                _isMiniPlayerDismissed.value = false
            }
            _currentVideo.value = video
            updateService()
        }
    }

    fun resumeTimers() {
        try {
            primaryCommandListener?.onResumeTimers()
            browseCommandListener?.onResumeTimers()
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
        if (_currentVideo.value == null && _playbackState.value == PlaybackState.IDLE) return

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

    private fun formatSeconds(totalSeconds: Int): String {
        val mins = totalSeconds / 60
        val secs = totalSeconds % 60
        return String.format("%d:%02d", mins, secs)
    }
}

