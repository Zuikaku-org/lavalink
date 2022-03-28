package lavalink.server.io

data class SpotifyLyricsData(
    val trackId: String? = null,
    val trackName: String? = null,
    val trackArtist: String? = null,
    val trackUrl: String? = null,
    val imageUrl: String? = null,
    val language: String? = null,
    val lyrics: List<String>? = ArrayList()
)