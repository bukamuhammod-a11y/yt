package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.player.AudioEqualizerManager
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.YouTubeRed

@Composable
fun EqualizerDialog(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val eqManager = remember { AudioEqualizerManager.getInstance(context) }

    val isEnabled by eqManager.isEnabled.collectAsState()
    val selectedPresetId by eqManager.selectedPresetId.collectAsState()
    val bassStrength by eqManager.bassStrength.collectAsState()
    val bandLevels by eqManager.bandLevels.collectAsState()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(28.dp))
                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(28.dp))
                .testTag("equalizer_dialog"),
            color = Color(0xFFA1E1E24) // Dark frosted glass style
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // --- Header Row ---
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(YouTubeRed.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.GraphicEq,
                                contentDescription = "Equalizer",
                                tint = YouTubeRed,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "YouTube Equalizer",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (isEnabled) "Active sound enhancement" else "Equalizer Disabled",
                                color = if (isEnabled) SuccessGreen else Color.Gray,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(
                            checked = isEnabled,
                            onCheckedChange = { eqManager.setEnabled(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = YouTubeRed,
                                uncheckedThumbColor = Color.Gray,
                                uncheckedTrackColor = Color(0x33FFFFFF)
                            ),
                            modifier = Modifier.testTag("eq_toggle_switch")
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color.White.copy(alpha = 0.7f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // --- Presets Selector Row ---
                Text(
                    text = "PRESETS",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(AudioEqualizerManager.PRESETS) { preset ->
                        val isSelected = selectedPresetId == preset.id
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(
                                    if (isSelected) YouTubeRed else Color(0x26FFFFFF)
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) YouTubeRed else Color(0x1FAAAAAA),
                                    shape = RoundedCornerShape(20.dp)
                                )
                                .clickable {
                                    eqManager.selectPreset(preset.id)
                                }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                                .testTag("eq_preset_${preset.id}")
                        ) {
                            Text(
                                text = preset.name,
                                color = if (isSelected) Color.White else Color.White.copy(alpha = 0.85f),
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // --- Bass Boost Slider Section ---
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0x1FFFFFFF)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Speaker,
                                    contentDescription = "Bass Boost",
                                    tint = YouTubeRed,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Ultra Bass Boost",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Text(
                                text = "${(bassStrength.toFloat() / 10f).toInt()}%",
                                color = YouTubeRed,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Slider(
                            value = bassStrength.toFloat(),
                            onValueChange = { eqManager.setBassStrength(it.toInt().toShort()) },
                            valueRange = 0f..1000f,
                            enabled = isEnabled,
                            colors = SliderDefaults.colors(
                                thumbColor = YouTubeRed,
                                activeTrackColor = YouTubeRed,
                                inactiveTrackColor = Color(0x33FFFFFF)
                            ),
                            modifier = Modifier.testTag("bass_boost_slider")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // --- 5 Band Equalizer Sliders ---
                Text(
                    text = "BAND EQUALIZER (dB)",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                val freqs = eqManager.bandFrequencies
                bandLevels.forEachIndexed { index, levelDb ->
                    if (index in freqs.indices) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = freqs[index],
                                    color = Color.White.copy(alpha = 0.9f),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "${if (levelDb > 0) "+" else ""}${String.format("%.1f", levelDb)} dB",
                                    color = if (levelDb != 0f) YouTubeRed else Color.Gray,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Slider(
                                value = levelDb,
                                onValueChange = { newDb ->
                                    eqManager.setBandLevelDb(index, newDb)
                                },
                                valueRange = -15f..15f,
                                enabled = isEnabled,
                                colors = SliderDefaults.colors(
                                    thumbColor = YouTubeRed,
                                    activeTrackColor = YouTubeRed,
                                    inactiveTrackColor = Color(0x33FFFFFF)
                                ),
                                modifier = Modifier.testTag("eq_band_slider_$index")
                            )
                        }
                    }
                }
            }
        }
    }
}
