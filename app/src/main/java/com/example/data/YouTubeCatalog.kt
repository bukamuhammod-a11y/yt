package com.example.data

import com.example.model.YouTubeVideo
import java.util.regex.Pattern

object YouTubeCatalog {

    val categories = listOf(
        "Trending",
        "Bangla Hits",
        "Lofi & Chill",
        "Pop & Global",
        "Bollywood",
        "Acoustic & Calm",
        "Podcasts"
    )

    val sampleVideos = listOf(
        // Bangla Hits
        YouTubeVideo(
            id = "w7k1WqE8jY4",
            title = "Abar Hashimukh - Shironamhin",
            channel = "Shironamhin Band",
            durationText = "5:12",
            durationSeconds = 312,
            category = "Bangla Hits"
        ),
        YouTubeVideo(
            id = "X1eHjQ0n3kE",
            title = "Bondho Janala - Shironamhin (Acoustic)",
            channel = "Shironamhin",
            durationText = "4:45",
            durationSeconds = 285,
            category = "Bangla Hits"
        ),
        YouTubeVideo(
            id = "pkwkMhV2y3Y",
            title = "Moner Manush - Anupam Roy",
            channel = "SVF Music",
            durationText = "4:18",
            durationSeconds = 258,
            category = "Bangla Hits"
        ),
        YouTubeVideo(
            id = "kJQP7kiw5Fk",
            title = "Despacito - Luis Fonsi ft. Daddy Yankee",
            channel = "Luis Fonsi",
            durationText = "4:41",
            durationSeconds = 281,
            category = "Pop & Global"
        ),
        // Lofi & Chill
        YouTubeVideo(
            id = "jfKfPfyJRdk",
            title = "lofi hip hop radio 📚 - beats to relax/study to",
            channel = "Lofi Girl",
            durationText = "LIVE",
            durationSeconds = 7200,
            category = "Lofi & Chill"
        ),
        YouTubeVideo(
            id = "5qap5aO4i9A",
            title = "lofi hip hop radio 💤 - beats to sleep/chill to",
            channel = "Lofi Girl",
            durationText = "LIVE",
            durationSeconds = 7200,
            category = "Lofi & Chill"
        ),
        YouTubeVideo(
            id = "DWcJFNfaw9c",
            title = "Night Drive Lofi - Synthwave & Chill Vibes",
            channel = "Chillhop Music",
            durationText = "48:20",
            durationSeconds = 2900,
            category = "Lofi & Chill"
        ),
        // Pop & Global
        YouTubeVideo(
            id = "JGwWNGJdvx8",
            title = "Shape of You - Ed Sheeran",
            channel = "Ed Sheeran",
            durationText = "4:23",
            durationSeconds = 263,
            category = "Pop & Global"
        ),
        YouTubeVideo(
            id = "fJ9rUzIMcZQ",
            title = "Bohemian Rhapsody - Queen",
            channel = "Queen Official",
            durationText = "5:59",
            durationSeconds = 359,
            category = "Pop & Global"
        ),
        YouTubeVideo(
            id = "4NRXx6U8ABQ",
            title = "Blinding Lights - The Weeknd",
            channel = "The Weeknd",
            durationText = "4:20",
            durationSeconds = 260,
            category = "Pop & Global"
        ),
        // Bollywood
        YouTubeVideo(
            id = "284Ov7ysmfA",
            title = "Tum Hi Ho - Arijit Singh | Aashiqui 2",
            channel = "T-Series",
            durationText = "4:22",
            durationSeconds = 262,
            category = "Bollywood"
        ),
        YouTubeVideo(
            id = "YxWlaYCA8MU",
            title = "Kesariya - Brahmāstra | Arijit Singh",
            channel = "Sony Music India",
            durationText = "4:28",
            durationSeconds = 268,
            category = "Bollywood"
        ),
        YouTubeVideo(
            id = "IltsZZ_mXvg",
            title = "Apna Bana Le - Bhediya | Arijit Singh",
            channel = "Zee Music Company",
            durationText = "4:21",
            durationSeconds = 261,
            category = "Bollywood"
        ),
        // Acoustic & Calm
        YouTubeVideo(
            id = "1ZYbU82GVz4",
            title = "Relaxing Guitar & Piano Music for Deep Sleep",
            channel = "Soothing Relaxation",
            durationText = "3:01:25",
            durationSeconds = 10885,
            category = "Acoustic & Calm"
        ),
        YouTubeVideo(
            id = "lTRiuFIWV54",
            title = "Deep Focus Piano Music - Ambient Concentration",
            channel = "Yellow Brick Cinema",
            durationText = "3:00:00",
            durationSeconds = 10800,
            category = "Acoustic & Calm"
        ),
        // Podcasts
        YouTubeVideo(
            id = "3qHkcs3kG44",
            title = "Huberman Lab - Focus & Neuroscience Secrets",
            channel = "Andrew Huberman",
            durationText = "2:15:30",
            durationSeconds = 8130,
            category = "Podcasts"
        )
    )

    /**
     * Extracts an 11-character YouTube video ID from various URL formats or returns the input if already an ID.
     */
    fun extractVideoId(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.length == 11 && trimmed.matches(Regex("[a-zA-Z0-9_-]{11}"))) {
            return trimmed
        }
        val pattern = "(?:https?:\\/\\/)?(?:www\\.|m\\.)?(?:youtube\\.com\\/(?:watch\\?v=|embed\\/|v\\/|shorts\\/)|youtu\\.be\\/)([a-zA-Z0-9_-]{11})"
        val compiled = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE)
        val matcher = compiled.matcher(trimmed)
        return if (matcher.find()) {
            matcher.group(1)
        } else null
    }

    /**
     * Searches videos locally or builds a dynamic video model from query.
     */
    fun searchVideos(query: String): List<YouTubeVideo> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return sampleVideos

        // Check if user pasted a link or ID
        val extractedId = extractVideoId(trimmed)
        if (extractedId != null) {
            val customVideo = YouTubeVideo(
                id = extractedId,
                title = "Custom YouTube Stream ($extractedId)",
                channel = "YouTube Direct Link",
                durationText = "Custom",
                durationSeconds = 300,
                category = "User URL"
            )
            return listOf(customVideo) + sampleVideos.filter { it.id != extractedId }
        }

        // Search in sample catalogue
        val matches = sampleVideos.filter {
            it.title.contains(trimmed, ignoreCase = true) ||
            it.channel.contains(trimmed, ignoreCase = true) ||
            it.category.contains(trimmed, ignoreCase = true)
        }

        if (matches.isNotEmpty()) {
            return matches
        }

        // If no direct catalogue match, synthesize search results so user can play query
        val searchItem = YouTubeVideo(
            id = "search_${System.currentTimeMillis()}",
            title = trimmed,
            channel = "YouTube Search Result",
            durationText = "Streaming",
            durationSeconds = 240,
            category = "Search"
        )
        return listOf(searchItem) + sampleVideos
    }
}
