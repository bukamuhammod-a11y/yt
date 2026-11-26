package com.example.model

data class YouTubeVideo(
    val id: String,
    val title: String,
    val channel: String,
    val durationText: String,
    val durationSeconds: Int,
    val category: String,
    val thumbnailUrl: String = "https://img.youtube.com/vi/$id/hqdefault.jpg"
) {
    val embedUrl: String
        get() = "https://www.youtube.com/embed/$id?enablejsapi=1&autoplay=1&playsinline=1&controls=1"
}

enum class PlaybackState {
    IDLE,
    BUFFERING,
    PLAYING,
    PAUSED,
    ENDED
}
