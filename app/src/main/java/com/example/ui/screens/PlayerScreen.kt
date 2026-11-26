package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.SurroundSound
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.YouTubeCatalog
import com.example.model.PlaybackState
import com.example.model.YouTubeVideo
import com.example.player.PlayerManager
import com.example.ui.components.YouTubePlayerWebView
import com.example.ui.theme.AssistiveCyan
import com.example.ui.theme.DarkCard
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.YouTubeRed
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    onEnterPiP: () -> Unit,
    onEnterBlackSaver: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentVideo by PlayerManager.currentVideo.collectAsState()
    val playbackState by PlayerManager.playbackState.collectAsState()
    val currentPos by PlayerManager.currentPosition.collectAsState()
    val duration by PlayerManager.duration.collectAsState()
    val isBgPlay by PlayerManager.isBackgroundPlayEnabled.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("Trending") }
    val focusManager = LocalFocusManager.current

    val displayedVideos = remember(searchQuery, selectedCategory) {
        if (searchQuery.isNotEmpty()) {
            YouTubeCatalog.searchVideos(searchQuery)
        } else if (selectedCategory == "Trending") {
            YouTubeCatalog.sampleVideos
        } else {
            YouTubeCatalog.sampleVideos.filter { it.category.equals(selectedCategory, ignoreCase = true) }
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        // --- 1. Video Player Container ---
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(Color.Black),
                shape = RoundedCornerShape(0.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Black)
            ) {
                currentVideo?.let { video ->
                    YouTubePlayerWebView(
                        initialVideoId = video.id,
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("youtube_player_webview")
                    )
                }
            }
        }

        // --- 2. Now Playing Title & Quick Actions Row ---
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkSurface)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = currentVideo?.title ?: "Select a song or video",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = currentVideo?.channel ?: "YouTube Stream",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                            Text(
                                text = " • ",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                            Text(
                                text = if (isBgPlay) "Background Active 🎧" else "In-App Only",
                                color = if (isBgPlay) AssistiveCyan else TextSecondary,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    // PiP Button
                    IconButton(
                        onClick = onEnterPiP,
                        modifier = Modifier.testTag("pip_mode_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PictureInPictureAlt,
                            contentDescription = "Picture in Picture",
                            tint = AssistiveCyan
                        )
                    }

                    // Black Screen Saver Button
                    IconButton(
                        onClick = onEnterBlackSaver,
                        modifier = Modifier.testTag("black_saver_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Nightlight,
                            contentDescription = "Screen Lock Saver",
                            tint = Color(0xFFFFD54F)
                        )
                    }
                }

                // Progress Bar & Duration
                Spacer(modifier = Modifier.height(10.dp))
                Slider(
                    value = currentPos.coerceIn(0f, duration.coerceAtLeast(1f)),
                    onValueChange = { PlayerManager.seekTo(it) },
                    valueRange = 0f..duration.coerceAtLeast(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = YouTubeRed,
                        activeTrackColor = YouTubeRed,
                        inactiveTrackColor = Color(0x33FFFFFF)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(20.dp)
                        .testTag("playback_slider")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatTime(currentPos.toInt()),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                    Text(
                        text = formatTime(duration.toInt()),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }

                // Playback Controls Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            PlayerManager.setBackgroundPlayEnabled(!isBgPlay)
                        },
                        modifier = Modifier.testTag("toggle_bg_audio_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Headphones,
                            contentDescription = "Toggle Background Audio",
                            tint = if (isBgPlay) YouTubeRed else TextSecondary
                        )
                    }

                    IconButton(
                        onClick = { PlayerManager.prev() },
                        modifier = Modifier.testTag("prev_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = "Previous Song",
                            tint = TextPrimary,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    // Play/Pause Big Button
                    Surface(
                        onClick = { PlayerManager.togglePlayPause() },
                        shape = CircleShape,
                        color = YouTubeRed,
                        modifier = Modifier
                            .size(54.dp)
                            .testTag("play_pause_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (playbackState == PlaybackState.PLAYING) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (playbackState == PlaybackState.PLAYING) "Pause" else "Play",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = { PlayerManager.next() },
                        modifier = Modifier.testTag("next_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Next Song",
                            tint = TextPrimary,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    IconButton(
                        onClick = onEnterBlackSaver,
                        modifier = Modifier.testTag("quick_black_lock_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Nightlight,
                            contentDescription = "Black Screen Mode",
                            tint = TextSecondary
                        )
                    }
                }
            }
        }

        // --- 3. YouTube Search & URL Input ---
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            "Search songs, lofi, or paste YouTube link...",
                            fontSize = 13.sp,
                            color = TextSecondary
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = YouTubeRed
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear",
                                    tint = TextSecondary
                                )
                            }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        val videoId = YouTubeCatalog.extractVideoId(searchQuery)
                        if (videoId != null) {
                            val custom = YouTubeVideo(
                                id = videoId,
                                title = "Playing YouTube Link ($videoId)",
                                channel = "YouTube Stream",
                                durationText = "Stream",
                                durationSeconds = 300,
                                category = "Custom"
                            )
                            PlayerManager.playVideo(custom)
                        }
                        focusManager.clearFocus()
                    }),
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = DarkSurfaceElevated,
                        unfocusedContainerColor = DarkSurfaceElevated,
                        focusedBorderColor = YouTubeRed,
                        unfocusedBorderColor = Color(0x33FFFFFF),
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("youtube_search_input")
                )

                // Paste Link Quick Action Hint
                val extracted = YouTubeCatalog.extractVideoId(searchQuery)
                if (extracted != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        onClick = {
                            val custom = YouTubeVideo(
                                id = extracted,
                                title = "YouTube Link ($extracted)",
                                channel = "YouTube Stream",
                                durationText = "Stream",
                                durationSeconds = 300,
                                category = "Custom"
                            )
                            PlayerManager.playVideo(custom)
                            focusManager.clearFocus()
                        },
                        color = YouTubeRed.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = YouTubeRed,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Play detected YouTube video: $extracted",
                                color = YouTubeRed,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // --- 4. Category Pills ---
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                items(YouTubeCatalog.categories) { category ->
                    val isSelected = selectedCategory == category
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            selectedCategory = category
                            searchQuery = ""
                        },
                        label = {
                            Text(
                                text = category,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = YouTubeRed,
                            selectedLabelColor = Color.White,
                            containerColor = DarkSurfaceElevated,
                            labelColor = TextSecondary
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = if (isSelected) YouTubeRed else Color(0x22FFFFFF)
                        )
                    )
                }
            }
        }

        // --- 5. Songs / Videos List ---
        item {
            Text(
                text = "$selectedCategory (${displayedVideos.size})",
                style = MaterialTheme.typography.titleSmall,
                color = TextSecondary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )
        }

        items(displayedVideos) { video ->
            val isPlayingThis = currentVideo?.id == video.id

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 5.dp)
                    .clickable { PlayerManager.playVideo(video) }
                    .testTag("video_item_${video.id}"),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isPlayingThis) DarkCard else DarkSurfaceElevated
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Video Thumbnail with Duration badge
                    Box(
                        modifier = Modifier
                            .size(width = 100.dp, height = 58.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black)
                    ) {
                        AsyncImage(
                            model = video.thumbnailUrl,
                            contentDescription = video.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Surface(
                            color = Color.Black.copy(alpha = 0.75f),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(4.dp)
                        ) {
                            Text(
                                text = video.durationText,
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // Title & Channel
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = video.title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isPlayingThis) FontWeight.Bold else FontWeight.Normal,
                            color = if (isPlayingThis) YouTubeRed else TextPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = video.channel,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            maxLines = 1
                        )
                    }

                    // Play indicator / button
                    IconButton(
                        onClick = { PlayerManager.playVideo(video) }
                    ) {
                        Icon(
                            imageVector = if (isPlayingThis && playbackState == PlaybackState.PLAYING) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Play",
                            tint = if (isPlayingThis) YouTubeRed else TextSecondary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun formatTime(seconds: Int): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return String.format("%02d:%02d", mins, secs)
}
