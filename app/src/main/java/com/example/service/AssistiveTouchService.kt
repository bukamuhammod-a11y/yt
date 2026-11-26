package com.example.service

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.model.PlaybackState
import com.example.player.PlayerManager
import com.example.util.ScreenshotHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

class AssistiveTouchService : Service() {

    companion object {
        const val CHANNEL_ID = "assistive_touch_channel"
        const val NOTIFICATION_ID = 202

        const val ACTION_START = "com.example.assistive.START"
        const val ACTION_STOP = "com.example.assistive.STOP"
        const val ACTION_UPDATE_CONFIG = "com.example.assistive.UPDATE_CONFIG"

        var isRunning = false
            private set
    }

    private lateinit var windowManager: WindowManager
    private lateinit var audioManager: AudioManager
    private lateinit var prefs: AssistivePreferences

    private var floatingBubbleView: View? = null
    private var menuOverlayView: View? = null
    private var dimOverlayView: View? = null

    private var bubbleLayoutParams: WindowManager.LayoutParams? = null
    private var dimLayoutParams: WindowManager.LayoutParams? = null

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var playerCollectorJob: Job? = null

    private var screenWidth = 1080
    private var screenHeight = 1920

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        prefs = AssistivePreferences(this)

        updateScreenDimensions()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())

        if (Settings.canDrawOverlays(this)) {
            setupFloatingBubble()
        }

        observePlayerState()
    }

    private fun updateScreenDimensions() {
        val metrics = DisplayMetrics()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.currentWindowMetrics.bounds
            screenWidth = bounds.width()
            screenHeight = bounds.height()
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getMetrics(metrics)
            screenWidth = metrics.widthPixels
            screenHeight = metrics.heightPixels
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "AssistiveTouch Floating Quick Controls",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Always-on AssistiveTouch floating button on screen"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AssistiveTouch Active")
            .setContentText("Floating controls for Sound, Brightness & Recording")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            resources.displayMetrics
        ).toInt()
    }

    /**
     * Controls actual Android system brightness and active window brightness
     */
    private fun getSystemBrightness(): Float {
        return try {
            val curVal = Settings.System.getInt(
                contentResolver,
                Settings.System.SCREEN_BRIGHTNESS
            )
            (curVal / 255f).coerceIn(0.02f, 1.0f)
        } catch (e: Exception) {
            prefs.overlayBrightness.coerceIn(0.02f, 1.0f)
        }
    }

    private fun updateSystemBrightness(brightness: Float) {
        val clamped = brightness.coerceIn(0.02f, 1.0f)
        prefs.overlayBrightness = clamped

        // 1. Update active app window brightness
        MainActivity.updateWindowBrightness(clamped)

        // 2. Control Android OS system brightness
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (Settings.System.canWrite(this)) {
                try {
                    Settings.System.putInt(
                        contentResolver,
                        Settings.System.SCREEN_BRIGHTNESS_MODE,
                        Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
                    )
                    val sysVal = (clamped * 255f).roundToInt().coerceIn(5, 255)
                    Settings.System.putInt(
                        contentResolver,
                        Settings.System.SCREEN_BRIGHTNESS,
                        sysVal
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            } else {
                Toast.makeText(this, "Enable 'Modify system settings' for system brightness", Toast.LENGTH_SHORT).show()
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                        data = Uri.parse("package:$packageName")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        } else {
            try {
                val sysVal = (clamped * 255f).roundToInt().coerceIn(5, 255)
                Settings.System.putInt(
                    contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS,
                    sysVal
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun takeScreenshotFromService() {
        closeAssistiveMenu()
        serviceScope.launch {
            delay(250) // Allow overlay to disappear cleanly
            val activity = MainActivity.currentActivity
            if (activity != null && !activity.isFinishing && !activity.isDestroyed) {
                activity.runOnUiThread {
                    ScreenshotHelper.captureActivity(activity)
                }
            } else {
                val intent = Intent(this@AssistiveTouchService, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                startActivity(intent)
                delay(500)
                MainActivity.currentActivity?.let { act ->
                    act.runOnUiThread {
                        ScreenshotHelper.captureActivity(act)
                    }
                }
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupFloatingBubble() {
        if (floatingBubbleView != null) return

        val sizePx = dpToPx(prefs.sizeDp)
        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        bubbleLayoutParams = WindowManager.LayoutParams(
            sizePx,
            sizePx,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 24
            y = screenHeight / 3
        }

        // Create the Assistive Touch bubble view
        val bubble = FrameLayout(this).apply {
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#E61E1E24")) // Frosted dark
                setStroke(dpToPx(2), Color.parseColor("#80FFFFFF"))
            }
            background = bg
            alpha = prefs.opacity

            // Inner circle ring for AssistiveTouch visual identity
            val innerView = View(context).apply {
                val innerBg = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor("#4DFFFFFF"))
                    setStroke(dpToPx(2), Color.parseColor("#FFFF0033")) // YouTube red accent
                }
                background = innerBg
            }
            val innerSize = (sizePx * 0.55f).roundToInt()
            val innerParams = FrameLayout.LayoutParams(innerSize, innerSize).apply {
                gravity = Gravity.CENTER
            }
            addView(innerView, innerParams)

            // Center dot
            val dotView = View(context).apply {
                val dotBg = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.WHITE)
                }
                background = dotBg
            }
            val dotSize = (sizePx * 0.22f).roundToInt()
            val dotParams = FrameLayout.LayoutParams(dotSize, dotSize).apply {
                gravity = Gravity.CENTER
            }
            addView(dotView, dotParams)
        }

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var touchStartTime = 0L
        var lastBubbleTapTime = 0L

        bubble.setOnTouchListener { _, event ->
            val params = bubbleLayoutParams ?: return@setOnTouchListener false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    touchStartTime = System.currentTimeMillis()
                    // Animate to full opacity on press
                    bubble.alpha = 1.0f
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = (event.rawX - initialTouchX).toInt()
                    val deltaY = (event.rawY - initialTouchY).toInt()
                    params.x = initialX + deltaX
                    params.y = initialY + deltaY
                    try {
                        windowManager.updateViewLayout(bubble, params)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    bubble.alpha = prefs.opacity
                    val deltaX = abs(event.rawX - initialTouchX)
                    val deltaY = abs(event.rawY - initialTouchY)
                    val duration = System.currentTimeMillis() - touchStartTime

                    // Detect Tap vs Drag
                    if (deltaX < 15 && deltaY < 15 && duration < 350) {
                        val now = System.currentTimeMillis()
                        if (now - lastBubbleTapTime < 350) {
                            // Double-Tap on Bubble -> Instant Hardware / AMOLED Power Button Screen Lock!
                            lastBubbleTapTime = 0L
                            if (!LockDeviceAdminReceiver.lockDevice(this@AssistiveTouchService)) {
                                PlayerManager.setBlackScreenSaverActive(true)
                                val lockIntent = Intent(this@AssistiveTouchService, MainActivity::class.java).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                                }
                                startActivity(lockIntent)
                                LockDeviceAdminReceiver.requestDeviceAdminPrompt(this@AssistiveTouchService)
                            }
                        } else {
                            lastBubbleTapTime = now
                            openAssistiveMenu()
                        }
                    } else {
                        // Snap to nearest edge
                        snapBubbleToEdge(params, bubble)
                    }
                    true
                }
                else -> false
            }
        }

        floatingBubbleView = bubble
        try {
            windowManager.addView(bubble, bubbleLayoutParams)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun snapBubbleToEdge(params: WindowManager.LayoutParams, bubble: View) {
        updateScreenDimensions()
        val currentX = params.x
        val bubbleWidth = bubble.width
        val targetX = if (currentX + bubbleWidth / 2 < screenWidth / 2) {
            dpToPx(12) // snap left
        } else {
            screenWidth - bubbleWidth - dpToPx(12) // snap right
        }

        val animator = ValueAnimator.ofInt(currentX, targetX).apply {
            this.duration = 250
            interpolator = DecelerateInterpolator()
            addUpdateListener { animation ->
                params.x = animation.animatedValue as Int
                try {
                    windowManager.updateViewLayout(bubble, params)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        animator.start()
    }

    /**
     * Opens the rich AssistiveTouch Quick Control Menu (iOS Theme)
     */
    private fun openAssistiveMenu() {
        if (menuOverlayView != null) return

        updateScreenDimensions()
        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val menuParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        // Fullscreen container with dim backdrop
        val rootLayout = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#4D000000")) // Subtle dim
            setOnClickListener { closeAssistiveMenu() }
            alpha = 0f
            animate().alpha(1f).setDuration(250).start()
        }

        // Center AssistiveTouch Card (iOS Frosted Glass look)
        val cardWidth = (screenWidth * 0.85f).roundToInt().coerceAtMost(dpToPx(340))
        val cardLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val cardBg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(36).toFloat()
                setColor(Color.parseColor("#E61C1C1E")) // Deep dark iOS glass style
            }
            background = cardBg
            setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16))
            setOnClickListener { /* prevent dismiss */ }
            
            // Entry animation
            scaleX = 0.8f
            scaleY = 0.8f
            alpha = 0f
            animate().scaleX(1f).scaleY(1f).alpha(1f)
                .setDuration(250)
                .setInterpolator(android.view.animation.OvershootInterpolator(1.2f))
                .start()
        }

        val cardParams = FrameLayout.LayoutParams(
            cardWidth,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.CENTER
        }

        // --- 1. Sound / Volume Control Section (iOS Slider Style) ---
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)

        val volumeBox = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(16).toFloat()
                setColor(Color.parseColor("#33FFFFFF"))
            }
            background = bg
            setPadding(dpToPx(12), dpToPx(12), dpToPx(12), dpToPx(12))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dpToPx(12)
            }
        }
        val volIcon = android.widget.ImageView(this).apply {
            setImageResource(R.drawable.ic_volume)
            setColorFilter(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(dpToPx(24), dpToPx(24))
        }
        val volumeSeekBar = SeekBar(this).apply {
            max = maxVolume
            progress = currentVolume
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dpToPx(8)
                marginEnd = dpToPx(8)
            }
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, progress, 0)
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        volumeBox.addView(volIcon)
        volumeBox.addView(volumeSeekBar)
        cardLayout.addView(volumeBox)

        // --- 2. Now Playing Audio Widget (iOS Style) ---
        val ytBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(20).toFloat()
                setColor(Color.parseColor("#33FFFFFF"))
            }
            background = bg
            setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dpToPx(12)
            }
        }

        val ytTitle = TextView(this).apply {
            text = PlayerManager.currentVideo.value?.title ?: "Not Playing"
            setTextColor(Color.WHITE)
            textSize = 15f
            maxLines = 1
            paint.isFakeBoldText = true
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dpToPx(12)
            }
        }
        ytBox.addView(ytTitle)

        // YT Controls Row
        val ytButtonsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        val isYtPlaying = PlayerManager.playbackState.value == PlaybackState.PLAYING

        val prevBtn = createIconActionButton("Prev", R.drawable.ic_prev_simple) {
            PlayerManager.prev()
            ytTitle.text = PlayerManager.currentVideo.value?.title ?: "Not Playing"
        }
        val playPauseBtn = createIconActionButton(if (isYtPlaying) "Pause" else "Play", if (isYtPlaying) R.drawable.ic_pause_simple else R.drawable.ic_play_simple) { v ->
            PlayerManager.togglePlayPause()
            val playingAfter = PlayerManager.playbackState.value == PlaybackState.PLAYING
            val parentLayout = v as? LinearLayout
            parentLayout?.let {
                val iconView = it.getChildAt(0) as? android.widget.ImageView
                val textView = it.getChildAt(1) as? TextView
                iconView?.setImageResource(if (playingAfter) R.drawable.ic_pause_simple else R.drawable.ic_play_simple)
                textView?.text = if (playingAfter) "Pause" else "Play"
            }
        }
        val nextBtn = createIconActionButton("Next", R.drawable.ic_next_simple) {
            PlayerManager.next()
            ytTitle.text = PlayerManager.currentVideo.value?.title ?: "Not Playing"
        }
        
        ytButtonsRow.addView(prevBtn)
        ytButtonsRow.addView(playPauseBtn)
        ytButtonsRow.addView(nextBtn)
        ytBox.addView(ytButtonsRow)
        cardLayout.addView(ytBox)

        // --- 3. Grid of Quick Actions (3 columns x 2 rows) ---
        val gridLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        val row1 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dpToPx(8)
            }
        }
        val row2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        val lockScreenBtn = createIconActionButton("Lock", R.drawable.ic_lock_simple) {
            closeAssistiveMenu()
            if (!LockDeviceAdminReceiver.lockDevice(this@AssistiveTouchService)) {
                PlayerManager.setBlackScreenSaverActive(true)
                val intent = Intent(this@AssistiveTouchService, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                startActivity(intent)
                LockDeviceAdminReceiver.requestDeviceAdminPrompt(this@AssistiveTouchService)
            }
        }

        val blackScreenBtn = createIconActionButton("Saver", R.drawable.ic_saver) {
            closeAssistiveMenu()
            PlayerManager.setBlackScreenSaverActive(true)
            val intent = Intent(this@AssistiveTouchService, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(intent)
        }

        val screenshotBtn = createIconActionButton("Capture", R.drawable.ic_screenshot) {
            takeScreenshotFromService()
        }

        val pipBtn = createIconActionButton("PiP", R.drawable.ic_pip) {
            closeAssistiveMenu()
            val pipIntent = Intent(this@AssistiveTouchService, MainActivity::class.java).apply {
                action = MainActivity.ACTION_ENTER_PIP
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(pipIntent)
        }
        
        val isRecordingNow = ScreenRecordHelper.isRecording.value
        val recordBtn = createIconActionButton("Record", if (isRecordingNow) R.drawable.ic_record_stop else R.drawable.ic_record) {
            closeAssistiveMenu()
            if (ScreenRecordHelper.isRecording.value) {
                ScreenRecordHelper.stopRecording(this@AssistiveTouchService)
            } else {
                ScreenRecordPromptActivity.launch(this@AssistiveTouchService)
            }
        }

        val homeBtn = createIconActionButton("Home", R.drawable.ic_home) {
            closeAssistiveMenu()
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(homeIntent)
        }

        val row3 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dpToPx(8)
            }
        }

        val eqBtn = createIconActionButton("EQ", R.drawable.ic_music) {
            closeAssistiveMenu()
            val intent = Intent(this@AssistiveTouchService, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(intent)
        }

        val settingsBtn = createIconActionButton("Settings", R.drawable.ic_settings) {
            closeAssistiveMenu()
            val intent = Intent(this@AssistiveTouchService, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(intent)
        }

        val appBtn = createIconActionButton("App", R.drawable.ic_youtube) {
            closeAssistiveMenu()
            val intent = Intent(this@AssistiveTouchService, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(intent)
        }

        row1.addView(homeBtn)
        row1.addView(lockScreenBtn)
        row1.addView(screenshotBtn)
        
        row2.addView(recordBtn)
        row2.addView(blackScreenBtn)
        row2.addView(pipBtn)

        row3.addView(eqBtn)
        row3.addView(settingsBtn)
        row3.addView(appBtn)
        
        gridLayout.addView(row1)
        gridLayout.addView(row2)
        gridLayout.addView(row3)
        cardLayout.addView(gridLayout)

        rootLayout.addView(cardLayout, cardParams)
        menuOverlayView = rootLayout

        try {
            windowManager.addView(rootLayout, menuParams)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun createIconActionButton(label: String, iconResId: Int, onClick: (View) -> Unit): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(16).toFloat()
                setColor(Color.parseColor("#33FFFFFF"))
            }
            background = bg
            setPadding(dpToPx(8), dpToPx(12), dpToPx(8), dpToPx(12))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dpToPx(4)
                marginEnd = dpToPx(4)
            }
            
            val iconView = android.widget.ImageView(this@AssistiveTouchService).apply {
                setImageResource(iconResId)
                layoutParams = LinearLayout.LayoutParams(dpToPx(24), dpToPx(24))
                setColorFilter(Color.WHITE)
            }
            
            val textView = TextView(this@AssistiveTouchService).apply {
                text = label
                setTextColor(Color.WHITE)
                textSize = 12f
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = dpToPx(6)
                }
            }
            
            addView(iconView)
            addView(textView)
            setOnClickListener { onClick(it) }
        }
    }

    private fun closeAssistiveMenu() {
        val root = menuOverlayView ?: return
        val card = (root as? FrameLayout)?.getChildAt(0)
        
        card?.animate()?.scaleX(0.8f)?.scaleY(0.8f)?.alpha(0f)?.setDuration(200)?.start()
        root.animate()?.alpha(0f)?.setDuration(200)?.withEndAction {
            if (menuOverlayView != null) {
                try {
                    windowManager.removeView(menuOverlayView)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                menuOverlayView = null
            }
        }?.start()
    }

    private fun observePlayerState() {
        playerCollectorJob?.cancel()
        playerCollectorJob = serviceScope.launch {
            PlayerManager.currentVideo.collectLatest { video ->
                // Update notification if needed
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_UPDATE_CONFIG -> {
                floatingBubbleView?.let { bubble ->
                    bubble.alpha = prefs.opacity
                    val sizePx = dpToPx(prefs.sizeDp)
                    bubbleLayoutParams?.let { params ->
                        params.width = sizePx
                        params.height = sizePx
                        try {
                            windowManager.updateViewLayout(bubble, params)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }
        }
        return START_STICKY
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
        isRunning = false
        playerCollectorJob?.cancel()
        closeAssistiveMenu()

        floatingBubbleView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        floatingBubbleView = null

        dimOverlayView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        dimOverlayView = null

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
