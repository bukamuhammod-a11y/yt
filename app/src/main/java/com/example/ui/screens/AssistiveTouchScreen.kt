package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.service.AssistivePreferences
import com.example.service.AssistiveTouchService
import com.example.service.LockDeviceAdminReceiver
import com.example.service.ScreenRecordHelper
import com.example.service.ScreenRecordPromptActivity
import com.example.ui.theme.AssistiveCyan
import com.example.ui.theme.AssistiveGold
import com.example.ui.theme.DarkCard
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.YouTubeRed
import kotlin.math.roundToInt

@Composable
fun AssistiveTouchScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefs = remember { AssistivePreferences(context) }
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    var isServiceActive by remember { mutableStateOf(AssistiveTouchService.isRunning) }
    var hasOverlayPermission by remember { mutableStateOf(Settings.canDrawOverlays(context)) }

    var opacity by remember { mutableFloatStateOf(prefs.opacity) }
    var sizeDp by remember { mutableIntStateOf(prefs.sizeDp) }
    var testBrightness by remember { mutableFloatStateOf(prefs.overlayBrightness) }

    val isRecording by ScreenRecordHelper.isRecording.collectAsState()

    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }
    var currentVolume by remember { mutableIntStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)) }

    LaunchedEffect(Unit) {
        hasOverlayPermission = Settings.canDrawOverlays(context)
        isServiceActive = AssistiveTouchService.isRunning
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 36.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- 1. Master Toggle Card ---
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("assistive_touch_toggle_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = CircleShape,
                                color = if (isServiceActive) YouTubeRed.copy(alpha = 0.2f) else Color(0x22FFFFFF),
                                modifier = Modifier.size(46.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.TouchApp,
                                        contentDescription = null,
                                        tint = if (isServiceActive) YouTubeRed else TextSecondary,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = "AssistiveTouch Bubble",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = if (isServiceActive) "Active over all apps" else "Turn on to float on screen",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isServiceActive) SuccessGreen else TextSecondary
                                )
                            }
                        }

                        Switch(
                            checked = isServiceActive,
                            onCheckedChange = { enable ->
                                if (enable) {
                                    if (!Settings.canDrawOverlays(context)) {
                                        // Request overlay permission
                                        val intent = Intent(
                                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                            Uri.parse("package:${context.packageName}")
                                        ).apply {
                                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                        }
                                        context.startActivity(intent)
                                    } else {
                                        prefs.isEnabled = true
                                        startAssistiveService(context)
                                        isServiceActive = true
                                    }
                                } else {
                                    prefs.isEnabled = false
                                    stopAssistiveService(context)
                                    isServiceActive = false
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = YouTubeRed,
                                uncheckedThumbColor = TextSecondary,
                                uncheckedTrackColor = DarkSurfaceElevated
                            ),
                            modifier = Modifier.testTag("master_assistive_switch")
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "The semi-transparent bubble stays on screen always (even when the app is closed or in background). Tap it anytime to adjust sound, brightness, or record screen.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        lineHeight = 18.sp
                    )
                }
            }
        }

        // --- 2. Permission Banner (if not granted) ---
        item {
            if (!hasOverlayPermission) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0x33FF9800))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = Color(0xFFFFB74D),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Overlay Permission Required",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFFB74D)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "To keep AssistiveTouch floating over other apps, Android requires the 'Display over other apps' permission.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                val intent = Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}")
                                ).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                context.startActivity(intent)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("grant_overlay_permission_button")
                        ) {
                            Text("Grant Permission", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // --- 2.1 One-Tap Screen Lock (Power Button replacement) ---
        item {
            val isDeviceAdmin = LockDeviceAdminReceiver.isDeviceAdminActive(context)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isDeviceAdmin) DarkSurface else Color(0x33FFB300)
                ),
                border = if (!isDeviceAdmin) androidx.compose.foundation.BorderStroke(1.dp, Color(0x66FFB300)) else null
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = AssistiveGold,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "1-Tap Screen Lock (পাওয়ার বাটন)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = if (isDeviceAdmin) "Hardware lock active - 1 tap locks screen like power button" else "Enable admin permission for hardware screen lock",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isDeviceAdmin) SuccessGreen else AssistiveGold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (!isDeviceAdmin) {
                            Button(
                                onClick = { LockDeviceAdminReceiver.requestDeviceAdminPrompt(context) },
                                colors = ButtonDefaults.buttonColors(containerColor = AssistiveGold),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp)
                                    .testTag("enable_admin_lock_button")
                            ) {
                                Text("Enable Power Lock", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }

                        Button(
                            onClick = {
                                if (!LockDeviceAdminReceiver.lockDevice(context)) {
                                    LockDeviceAdminReceiver.requestDeviceAdminPrompt(context)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isDeviceAdmin) DarkSurfaceElevated else Color(0x44FFFFFF)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                                .testTag("test_screen_lock_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = AssistiveGold,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Test Lock Now", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // --- 3. Live Bubble Visual Customizer ---
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Bubble Appearance",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Customize the semi-transparency and size of the floating orb",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Live Orb Preview Canvas
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(DarkCard)
                            .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        // Background hint text
                        Text(
                            text = "Screen Background Preview",
                            color = Color.White.copy(alpha = 0.15f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )

                        // The simulated AssistiveTouch Bubble
                        Box(
                            modifier = Modifier
                                .size(sizeDp.dp)
                                .alpha(opacity)
                                .clip(CircleShape)
                                .background(Color(0xE61E1E24))
                                .border(2.dp, Color(0x80FFFFFF), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size((sizeDp * 0.55f).dp)
                                    .clip(CircleShape)
                                    .background(Color(0x4DFFFFFF))
                                    .border(2.dp, YouTubeRed, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size((sizeDp * 0.22f).dp)
                                        .clip(CircleShape)
                                        .background(Color.White)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Opacity Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Opacity,
                                contentDescription = null,
                                tint = AssistiveCyan,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Semi-Transparency",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextPrimary
                            )
                        }
                        Text(
                            text = "${(opacity * 100).roundToInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = AssistiveCyan
                        )
                    }

                    Slider(
                        value = opacity,
                        onValueChange = {
                            opacity = it
                            prefs.opacity = it
                            if (isServiceActive) updateAssistiveConfig(context)
                        },
                        valueRange = 0.20f..1.0f,
                        colors = SliderDefaults.colors(
                            thumbColor = AssistiveCyan,
                            activeTrackColor = AssistiveCyan,
                            inactiveTrackColor = Color(0x33FFFFFF)
                        ),
                        modifier = Modifier.testTag("opacity_slider")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Bubble Size Selector
                    Text(
                        text = "Bubble Size",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        listOf(44 to "Small", 54 to "Medium", 64 to "Large").forEach { (size, label) ->
                            val isSelected = sizeDp == size
                            Surface(
                                onClick = {
                                    sizeDp = size
                                    prefs.sizeDp = size
                                    if (isServiceActive) updateAssistiveConfig(context)
                                },
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) YouTubeRed else DarkSurfaceElevated,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "$label (${size}dp)",
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) Color.White else TextSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- 4. Quick Testing Suite (Sound, Brightness, Screen Recording) ---
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Quick Tools & Actions",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "These controls are also directly available in the AssistiveTouch floating menu",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // --- Volume Controls ---
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.VolumeUp,
                                contentDescription = null,
                                tint = YouTubeRed,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Media Sound Volume", color = TextPrimary, fontSize = 14.sp)
                        }
                        val pct = ((currentVolume.toFloat() / maxVolume.coerceAtLeast(1)) * 100).roundToInt()
                        Text("$pct%", color = YouTubeRed, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, 0)
                                currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                            },
                            modifier = Modifier.testTag("test_vol_down")
                        ) {
                            Icon(imageVector = Icons.Default.VolumeDown, contentDescription = "Vol Down", tint = TextPrimary)
                        }

                        Slider(
                            value = currentVolume.toFloat(),
                            onValueChange = {
                                val intVal = it.roundToInt()
                                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, intVal, 0)
                                currentVolume = intVal
                            },
                            valueRange = 0f..maxVolume.toFloat(),
                            colors = SliderDefaults.colors(
                                thumbColor = YouTubeRed,
                                activeTrackColor = YouTubeRed,
                                inactiveTrackColor = Color(0x33FFFFFF)
                            ),
                            modifier = Modifier.weight(1f)
                        )

                        IconButton(
                            onClick = {
                                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, 0)
                                currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                            },
                            modifier = Modifier.testTag("test_vol_up")
                        ) {
                            Icon(imageVector = Icons.Default.VolumeUp, contentDescription = "Vol Up", tint = TextPrimary)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // --- Brightness Controls ---
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.WbSunny,
                                contentDescription = null,
                                tint = AssistiveGold,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Screen Brightness", color = TextPrimary, fontSize = 14.sp)
                        }
                        Text("${(testBrightness * 100).roundToInt()}%", color = AssistiveGold, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }

                    Slider(
                        value = testBrightness,
                        onValueChange = {
                            testBrightness = it
                            prefs.overlayBrightness = it
                        },
                        valueRange = 0.15f..1.0f,
                        colors = SliderDefaults.colors(
                            thumbColor = AssistiveGold,
                            activeTrackColor = AssistiveGold,
                            inactiveTrackColor = Color(0x33FFFFFF)
                        ),
                        modifier = Modifier.testTag("test_brightness_slider")
                    )

                    // Brightness Presets
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(0.25f to "25%", 0.50f to "50%", 0.75f to "75%", 1.0f to "100%").forEach { (valFloat, text) ->
                            Surface(
                                onClick = {
                                    testBrightness = valFloat
                                    prefs.overlayBrightness = valFloat
                                },
                                shape = RoundedCornerShape(8.dp),
                                color = DarkSurfaceElevated,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(34.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(text = text, fontSize = 11.sp, color = TextPrimary)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // --- Screen Recording Button ---
                    Button(
                        onClick = {
                            if (isRecording) {
                                ScreenRecordHelper.stopRecording(context)
                            } else {
                                ScreenRecordPromptActivity.launch(context)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isRecording) Color(0xFFB71C1C) else DarkCard
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("test_screen_record_button")
                    ) {
                        Icon(
                            imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.FiberManualRecord,
                            contentDescription = null,
                            tint = if (isRecording) Color.White else Color(0xFFFF5252),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isRecording) "Stop Screen Recording (Active)" else "Trigger Screen Recording",
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }

        // --- 5. User Guide (Bangla & English) ---
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.HelpOutline,
                            contentDescription = null,
                            tint = AssistiveCyan,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "ব্যবহার বিধি (How it works)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    val guidePoints = listOf(
                        "🔒 Screen Lock Playing: গান প্লে করে ফোনের পাওয়ার বাটন চাপলে বা Black Saver অন করলে ব্যাকগ্রাউন্ডে গান চলবে।" to "Plays audio even when the phone screen is locked or turned off.",
                        "📱 Use Other Apps: অন্য যেকোনো অ্যাপ চালানোর সময় AssistiveTouch বাবল স্ক্রিনে ভাসমান থাকবে।" to "AssistiveTouch floating orb stays visible across all apps.",
                        "🪟 Picture-in-Picture: বাবলে বা প্লেয়ারের নিচে PiP বাটনে চাপ দিলে ভিডিও ভাসমান স্ক্রিনে চলতে থাকবে।" to "Picture-in-Picture pops video into a floating window over other apps.",
                        "🔊 Sound & Brightness: বাবলে চাপ দিলেই সাউন্ড ও ব্রাইটনেস দ্রুত বাড়ানো/কমানো যায়।" to "Tap the bubble anytime to control volume and brightness.",
                        "⏺ Screen Record: বাবল থেকে সরাসরি এক ট্যাপে স্ক্রিন রেকর্ডিং শুরু ও সেভ করা যায়।" to "Start and stop screen recordings instantly from the floating menu."
                    )

                    guidePoints.forEach { (bangla, english) ->
                        Column(modifier = Modifier.padding(vertical = 6.dp)) {
                            Text(
                                text = bangla,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary,
                                lineHeight = 18.sp
                            )
                            Text(
                                text = english,
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun startAssistiveService(context: Context) {
    val intent = Intent(context, AssistiveTouchService::class.java).apply {
        action = AssistiveTouchService.ACTION_START
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.startForegroundService(intent)
    } else {
        context.startService(intent)
    }
}

private fun stopAssistiveService(context: Context) {
    val intent = Intent(context, AssistiveTouchService::class.java).apply {
        action = AssistiveTouchService.ACTION_STOP
    }
    context.startService(intent)
}

private fun updateAssistiveConfig(context: Context) {
    val intent = Intent(context, AssistiveTouchService::class.java).apply {
        action = AssistiveTouchService.ACTION_UPDATE_CONFIG
    }
    context.startService(intent)
}
