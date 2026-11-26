package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.session.MediaSession
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.player.PlayerManager

class BackgroundAudioService : Service() {

    companion object {
        const val CHANNEL_ID = "yt_bg_playback_channel"
        const val NOTIFICATION_ID = 101

        const val ACTION_UPDATE_STATE = "com.example.action.UPDATE_STATE"
        const val ACTION_PLAY_PAUSE = "com.example.action.PLAY_PAUSE"
        const val ACTION_NEXT = "com.example.action.NEXT"
        const val ACTION_PREV = "com.example.action.PREV"
        const val ACTION_STOP = "com.example.action.STOP"

        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_CHANNEL = "extra_channel"
        const val EXTRA_IS_PLAYING = "extra_is_playing"
    }

    private var wakeLock: PowerManager.WakeLock? = null
    private var mediaSession: MediaSession? = null
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null

    private var currentTitle: String = "YouTube Background Player"
    private var currentChannel: String = "Playing in Background"
    private var isPlaying: Boolean = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        // Acquire partial wakelock so playback continues when screen is locked
        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        wakeLock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "BackgroundYT::PlaybackWakeLock")
        wakeLock?.setReferenceCounted(false)
        wakeLock?.acquire(12 * 60 * 60 * 1000L) // 12 hours max

        requestAudioFocus()
        initMediaSession()
    }

    private fun requestAudioFocus() {
        try {
            audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val playbackAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
                audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(playbackAttributes)
                    .setAcceptsDelayedFocusGain(true)
                    .setOnAudioFocusChangeListener { /* maintain audio focus */ }
                    .build()
                audioFocusRequest?.let { audioManager?.requestAudioFocus(it) }
            } else {
                @Suppress("DEPRECATION")
                audioManager?.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun releaseAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                audioManager?.abandonAudioFocus(null)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun initMediaSession() {
        mediaSession = MediaSession(this, "BackgroundYTSession").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() {
                    PlayerManager.play()
                }

                override fun onPause() {
                    PlayerManager.pause()
                }

                override fun onSkipToNext() {
                    PlayerManager.next()
                }

                override fun onSkipToPrevious() {
                    PlayerManager.prev()
                }

                override fun onStop() {
                    PlayerManager.pause()
                    stopSelf()
                }
            })
            isActive = true
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        when (action) {
            ACTION_PLAY_PAUSE -> {
                PlayerManager.togglePlayPause()
            }
            ACTION_NEXT -> {
                PlayerManager.next()
            }
            ACTION_PREV -> {
                PlayerManager.prev()
            }
            ACTION_STOP -> {
                PlayerManager.pause()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_UPDATE_STATE, null -> {
                intent?.getStringExtra(EXTRA_TITLE)?.let { currentTitle = it }
                intent?.getStringExtra(EXTRA_CHANNEL)?.let { currentChannel = it }
                if (intent != null && intent.hasExtra(EXTRA_IS_PLAYING)) {
                    isPlaying = intent.getBooleanExtra(EXTRA_IS_PLAYING, false)
                }
            }
        }

        val notification = buildNotification()
        startForeground(NOTIFICATION_ID, notification)

        return START_STICKY
    }

    private fun buildNotification(): Notification {
        val mainIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            0,
            mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Notification Actions
        val prevIntent = Intent(this, BackgroundAudioService::class.java).apply { action = ACTION_PREV }
        val prevPendingIntent = PendingIntent.getService(this, 1, prevIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val playPauseIntent = Intent(this, BackgroundAudioService::class.java).apply { action = ACTION_PLAY_PAUSE }
        val playPausePendingIntent = PendingIntent.getService(this, 2, playPauseIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val nextIntent = Intent(this, BackgroundAudioService::class.java).apply { action = ACTION_NEXT }
        val nextPendingIntent = PendingIntent.getService(this, 3, nextIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val stopIntent = Intent(this, BackgroundAudioService::class.java).apply { action = ACTION_STOP }
        val stopPendingIntent = PendingIntent.getService(this, 4, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val playPauseIcon = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        val playPauseTitle = if (isPlaying) "Pause" else "Play"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(currentTitle)
            .setContentText(currentChannel)
            .setSubText("Screen-Off Playing")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(contentPendingIntent)
            .setOngoing(isPlaying)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(android.R.drawable.ic_media_previous, "Prev", prevPendingIntent)
            .addAction(playPauseIcon, playPauseTitle, playPausePendingIntent)
            .addAction(android.R.drawable.ic_media_next, "Next", nextPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Close", stopPendingIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "YouTube Background Audio",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background streaming and lock-screen media controls"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        stopSelf()
    }

    override fun onDestroy() {
        releaseAudioFocus()
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
        mediaSession?.release()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
