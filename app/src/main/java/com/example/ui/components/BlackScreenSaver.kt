package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.PlaybackState
import com.example.player.PlayerManager
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BlackScreenSaverOverlay(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentVideo by PlayerManager.currentVideo.collectAsState()
    val playbackState by PlayerManager.playbackState.collectAsState()

    var currentTimeStr by remember { mutableStateOf("") }
    var showControls by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            currentTimeStr = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
            delay(1000)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("black_screen_saver_overlay")
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { onDismiss() },
                    onTap = { showControls = !showControls }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        // Ambient Faint Clock & Info (OLED Energy Saving)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(24.dp)
        ) {
            Text(
                text = currentTimeStr,
                color = Color.White.copy(alpha = 0.35f),
                fontSize = 54.sp,
                fontWeight = FontWeight.Thin,
                fontFamily = FontFamily.SansSerif
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.4f),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = currentVideo?.title ?: "Background Playing",
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 14.sp,
                    maxLines = 1
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Screen Locked • Audio Active",
                color = Color.Red.copy(alpha = 0.5f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Faint unlock indicator
            Surface(
                color = Color.White.copy(alpha = 0.08f),
                shape = RoundedCornerShape(20.dp),
                onClick = { onDismiss() },
                modifier = Modifier.testTag("exit_saver_button")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.LockOpen,
                        contentDescription = "Unlock",
                        tint = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "  Tap to Unlock Screen",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 12.sp
                    )
                }
            }
        }

        // Optional Quick Mini Controls on Tap
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp)
        ) {
            Surface(
                color = Color(0x991E1E24),
                shape = CircleShape,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    IconButton(
                        onClick = { PlayerManager.prev() },
                        modifier = Modifier.testTag("saver_prev_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = "Previous",
                            tint = Color.White
                        )
                    }

                    IconButton(
                        onClick = { PlayerManager.togglePlayPause() },
                        modifier = Modifier.testTag("saver_play_pause_button")
                    ) {
                        Icon(
                            imageVector = if (playbackState == PlaybackState.PLAYING) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Play/Pause",
                            tint = Color.White
                        )
                    }

                    IconButton(
                        onClick = { PlayerManager.next() },
                        modifier = Modifier.testTag("saver_next_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Next",
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }
}
