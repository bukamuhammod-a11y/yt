package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.player.PlayerManager
import com.example.ui.components.AssistiveSettingsDialog
import com.example.ui.components.BlackScreenSaverOverlay
import com.example.ui.components.EqualizerDialog
import com.example.ui.components.FullYouTubeWebView
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.YouTubeRed

@Composable
fun MainScreen(
    onEnterPiP: () -> Unit,
    onTakeScreenshot: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isBlackSaverActive by PlayerManager.isBlackScreenSaverActive.collectAsState()
    val isPiP by PlayerManager.isPiPActive.collectAsState()
    val isBgPlay by PlayerManager.isBackgroundPlayEnabled.collectAsState()

    var isVideoActiveOnScreen by remember { mutableStateOf(false) }
    var detectedVideoTitle by remember { mutableStateOf("YouTube Video") }

    var showEqDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            contentWindowInsets = if (isPiP) WindowInsets(0, 0, 0, 0) else WindowInsets.safeDrawing,
            containerColor = DarkBackground
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(if (isPiP) androidx.compose.foundation.layout.PaddingValues(0.dp) else innerPadding)
            ) {
                // --- Full Screen YouTube View (Primary UI with no bottom bar) ---
                Box(modifier = Modifier.fillMaxSize()) {
                    FullYouTubeWebView(
                        onVideoDetected = { hasVideo, title, _ ->
                            isVideoActiveOnScreen = hasVideo
                            if (title.isNotEmpty() && title != "YouTube") {
                                detectedVideoTitle = title.replace("- YouTube", "").trim()
                            }
                        }
                    )

                    // --- Floating Quick Action Icons on Right Edge (Hidden in PiP) ---
                    if (!isPiP) {
                        Column(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 6.dp)
                                .background(Color(0xCC121216), RoundedCornerShape(20.dp))
                                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(20.dp))
                                .padding(horizontal = 4.dp, vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // 1. Background Play Button
                            IconButton(
                                onClick = { PlayerManager.setBackgroundPlayEnabled(!isBgPlay) },
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(if (isBgPlay) SuccessGreen.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.08f))
                                    .testTag("floating_bg_play_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Headphones,
                                    contentDescription = "Background Play",
                                    tint = if (isBgPlay) SuccessGreen else Color.White.copy(alpha = 0.85f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            // 2. Picture-in-Picture Button
                            IconButton(
                                onClick = onEnterPiP,
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.08f))
                                    .testTag("floating_pip_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PictureInPictureAlt,
                                    contentDescription = "PiP Window",
                                    tint = Color.White.copy(alpha = 0.85f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            // 3. YouTube Equalizer (EQ) Launcher Button
                            IconButton(
                                onClick = { showEqDialog = true },
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(YouTubeRed.copy(alpha = 0.25f))
                                    .testTag("floating_eq_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.GraphicEq,
                                    contentDescription = "Equalizer",
                                    tint = YouTubeRed,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            // 4. App Settings Launcher Button
                            IconButton(
                                onClick = { showSettingsDialog = true },
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.08f))
                                    .testTag("floating_settings_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "Settings",
                                    tint = Color.White.copy(alpha = 0.85f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            // 5. Ad-Block Shield Badge
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(top = 2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Shield,
                                    contentDescription = "AdBlock Active",
                                    tint = SuccessGreen,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "AdBlock",
                                    color = SuccessGreen,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        // Equalizer Dialog Modal
        if (showEqDialog) {
            EqualizerDialog(
                onDismiss = { showEqDialog = false }
            )
        }

        // Settings Dialog Modal
        if (showSettingsDialog) {
            AssistiveSettingsDialog(
                onDismiss = { showSettingsDialog = false }
            )
        }

        // Full Screen Lock / Black Screen Saver Overlay
        AnimatedVisibility(
            visible = isBlackSaverActive,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            BlackScreenSaverOverlay(
                onDismiss = { PlayerManager.setBlackScreenSaverActive(false) }
            )
        }
    }
}
