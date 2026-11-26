package com.example

import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Rational
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import com.example.data.YouTubeCatalog
import com.example.model.PlaybackState
import com.example.model.YouTubeVideo
import com.example.player.PlayerManager
import com.example.service.AssistivePreferences
import com.example.service.AssistiveTouchService
import com.example.ui.screens.MainScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.util.ScreenshotHelper

class MainActivity : ComponentActivity() {

    companion object {
        const val ACTION_ENTER_PIP = "com.example.action.ENTER_PIP"
        var currentActivity: MainActivity? = null

        fun updateWindowBrightness(brightness: Float) {
            currentActivity?.runOnUiThread {
                try {
                    val lp = currentActivity?.window?.attributes
                    if (lp != null) {
                        lp.screenBrightness = brightness.coerceIn(0.01f, 1.0f)
                        currentActivity?.window?.attributes = lp
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        fun takeScreenshot() {
            currentActivity?.let {
                ScreenshotHelper.captureActivity(it)
            }
        }
    }

    private var pendingPip = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        currentActivity = this
        enableEdgeToEdge()

        PlayerManager.init(this)
        com.example.player.AudioEqualizerManager.getInstance(this).initAudioEffects()
        handleIntent(intent)
        
        lifecycleScope.launch {
            PlayerManager.playbackState.collectLatest { state ->
                updatePipParams(state == PlaybackState.PLAYING)
            }
        }

        // If user already enabled AssistiveTouch and has permission, ensure it is running
        val prefs = AssistivePreferences(this)
        if (prefs.isEnabled && Settings.canDrawOverlays(this) && !AssistiveTouchService.isRunning) {
            val serviceIntent = Intent(this, AssistiveTouchService::class.java).apply {
                action = AssistiveTouchService.ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        }

        setContent {
            MyApplicationTheme {
                MainScreen(
                    onEnterPiP = { enterPipMode() },
                    onTakeScreenshot = { ScreenshotHelper.captureActivity(this) },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        currentActivity = this
        // Removed hardcoded setupPipParams
        if (pendingPip) {
            pendingPip = false
            window.decorView.postDelayed({
                enterPipMode()
            }, 200)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (currentActivity == this) {
            currentActivity = null
        }
    }

    private fun updatePipParams(autoEnter: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val params = PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .setAutoEnterEnabled(autoEnter)
                    .setSeamlessResizeEnabled(true)
                    .build()
                setPictureInPictureParams(params)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return

        if (intent.action == ACTION_ENTER_PIP) {
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                window.decorView.postDelayed({
                    enterPipMode()
                }, 200)
            } else {
                pendingPip = true
            }
            return
        }

        val dataString = intent.dataString ?: intent.getStringExtra(Intent.EXTRA_TEXT)
        if (!dataString.isNullOrEmpty()) {
            val videoId = YouTubeCatalog.extractVideoId(dataString)
            if (videoId != null) {
                val custom = YouTubeVideo(
                    id = videoId,
                    title = "Shared YouTube Video ($videoId)",
                    channel = "YouTube Shared Link",
                    durationText = "Streaming",
                    durationSeconds = 300,
                    category = "Shared"
                )
                PlayerManager.playVideo(custom)
            }
        }
    }

    fun enterPipMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val aspectRatio = Rational(16, 9)
                val builder = PictureInPictureParams.Builder()
                    .setAspectRatio(aspectRatio)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    builder.setAutoEnterEnabled(true)
                    builder.setSeamlessResizeEnabled(true)
                }
                val entered = enterPictureInPictureMode(builder.build())
                if (!entered) {
                    val enteredFallback = enterPictureInPictureMode(PictureInPictureParams.Builder().build())
                    if (!enteredFallback) {
                        Toast.makeText(this, "PiP failed. Please check app permissions.", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                try {
                    val enteredFallback = enterPictureInPictureMode(PictureInPictureParams.Builder().build())
                    if (!enteredFallback) {
                        Toast.makeText(this, "PiP failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                } catch (ex: Exception) {
                    ex.printStackTrace()
                    Toast.makeText(this, "PiP failed: ${ex.message}", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            Toast.makeText(this, "PiP requires Android 8.0+", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        PlayerManager.setPiPActive(isInPictureInPictureMode)
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // On Android 8 to 11, manual enter is needed when pressing home, but ONLY if playing video
        if (PlayerManager.playbackState.value == PlaybackState.PLAYING) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                enterPipMode()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (PlayerManager.isBackgroundPlayEnabled.value) {
            PlayerManager.resumeTimers()
        }
    }

    override fun onStop() {
        super.onStop()
        if (PlayerManager.isBackgroundPlayEnabled.value) {
            PlayerManager.resumeTimers()
        }
    }
}

