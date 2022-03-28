package lavalink.server.io

import com.google.gson.JsonElement
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser
import lavalink.server.source.spotify.SpotifyAudioSourceManager
import okhttp3.Request
import okio.ByteString.Companion.toByteString
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.bind.annotation.RestController
import xyz.gianlu.librespot.core.SearchManager
import xyz.gianlu.librespot.metadata.PlayableId
import xyz.gianlu.librespot.metadata.TrackId

@RestController
class SpotifyRestHandler(private val defaultAudioPlayerManager: DefaultAudioPlayerManager) {
    private final val spotifyAudioSourceManager =
        this.defaultAudioPlayerManager.source(SpotifyAudioSourceManager::class.java)
    private final val log = LoggerFactory.getLogger(SpotifyRestHandler::class.java)

    private fun searchTrack(query: String): JsonElement? {
        return try {
            val spotifySearchManager = spotifyAudioSourceManager
                .spotifySession?.search()?.request(SearchManager.SearchRequest(query))
            val spotifySearchResults = spotifySearchManager?.getAsJsonObject("results")
            val spotifyTracksProperty = spotifySearchResults?.getAsJsonObject("tracks")
            val spotifyHitsProperty = spotifyTracksProperty?.getAsJsonArray("hits")
            spotifyHitsProperty?.get(0)
        } catch (_: Exception) {
            null
        }
    }

    private fun fetchSpotifyLyrics(spotifyTrackElement: JsonElement): SpotifyLyricsData {
        try {
            val trackId = String(
                PlayableId.BASE62.encode(
                    TrackId.fromUri(
                        spotifyTrackElement.asJsonObject.get("uri").asString
                    )
                        .gid
                        .toByteString()
                        .toByteArray()
                )
            )
            val trackName = spotifyTrackElement.asJsonObject.get("name").asString
            val trackArtist =
                spotifyTrackElement.asJsonObject.get("artists").asJsonArray[0].asJsonObject.get("name").asString
            val trackUrl = "https://open.spotify.com/track/$trackId"
            val imageUrl = spotifyTrackElement.asJsonObject.get("image").asString
            val request = Request
                .Builder()
                .url("https://spclient.wg.spotify.com/lyrics/v1/track/$trackId")
                .addHeader(
                    "Authorization",
                    "Bearer " + spotifyAudioSourceManager.spotifySession?.tokens()?.get("playlist-read")
                )
                .addHeader(
                    "User-Agent",
                    spotifyAudioSourceManager.userAgent
                )
                .build()
            val response = spotifyAudioSourceManager.spotifySession?.client()?.newCall(request)?.execute()
            if (response?.code != 200) {
                throw FriendlyException(
                    "Spotify API returned ${response?.code}.",
                    FriendlyException.Severity.COMMON,
                    null
                )
            }
            val data = JsonBrowser.parse(response.body?.string())
            val rawLyricsLines = data["lines"].values()
            val language = data["language"].safeText()
            val lyrics = ArrayList<String>()
            if (rawLyricsLines.isNullOrEmpty()) {
                return SpotifyLyricsData()
            }
            rawLyricsLines.removeIf { lyricsData: JsonBrowser ->
                lyricsData["words"].values()[0]["string"].safeText() == ""
            }
            for (rawData in rawLyricsLines) {
                var toLyrics = rawData["words"].values()[0]["string"].safeText()
                if (toLyrics == "♪") {
                    toLyrics = ""
                }
                lyrics.add(toLyrics)
            }
            return SpotifyLyricsData(trackId, trackName, trackArtist, trackUrl, imageUrl, language, lyrics)
        } catch (_: Exception) {
            return SpotifyLyricsData()
        }
    }


    @GetMapping(value = ["lyrics"], produces = ["application/json"])
    @ResponseBody
    fun getLyrics(@RequestParam title: String): SpotifyLyricsData? {
        log.info("Got request to load lyrics for title: $title")
        try {
            val searchTrack = this.searchTrack(title) ?: return SpotifyLyricsData()
            return this.fetchSpotifyLyrics(searchTrack)
        } catch (_: Exception) {
            return SpotifyLyricsData()
        } finally {
            log.info("Loaded lyrics for title: $title")
        }
    }
}
