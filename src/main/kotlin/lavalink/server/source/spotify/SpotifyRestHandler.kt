package lavalink.server.source.spotify

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist
import lavalink.server.source.spotify.resolver.SpotifyPlaylistResolver
import lavalink.server.util.Util
import okhttp3.Request
import okio.ByteString.Companion.toByteString
import org.json.JSONArray
import org.json.JSONObject
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
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

    @GetMapping(value = ["lyrics"], produces = ["application/json"])
    @ResponseBody
    fun getLyrics(@RequestParam title: String): ResponseEntity<String> {
        log.info("Got request to load lyrics for title: $title")
        try {
            val searchTrack =
                this.searchTrack(title) ?: return ResponseEntity(spotifyLyricsData().toString(), HttpStatus.OK)
            return ResponseEntity(fetchSpotifyLyrics(searchTrack).toString(), HttpStatus.OK)
        } catch (_: Exception) {
            return ResponseEntity(spotifyLyricsData().toString(), HttpStatus.OK)
        } finally {
            log.info("Loaded lyrics for title: $title")
        }
    }

    @GetMapping(value = ["loadspotifyrecommendations"], produces = ["application/json"])
    @ResponseBody
    fun loadSpotifyRecommendations(@RequestParam url: String): ResponseEntity<String> {
        log.info("Got request to load spotify recommendations for identifier: $url")

        val matcher = spotifyAudioSourceManager.SPOTIFY_URL_PATTERN.matcher(url)
        if (!matcher.matches()) {
            return ResponseEntity(
                spotifyRestException("Invalid Spotify URL.", Severity.COMMON).toString(),
                HttpStatus.OK
            )
        }
        if (!matcher.group("type").equals("track")) {
            return ResponseEntity(
                spotifyRestException("Only accept spotify track uri", Severity.COMMON).toString(),
                HttpStatus.OK
            )
        }
        val identifier = matcher.group("identifier")
        val playableURI =
            if (identifier.startsWith("https://open.spotify.com/episode")) {
                "spotify:episode:$identifier"
            } else {
                "spotify:track:$identifier"
            }
        try {
            val resp = spotifyAudioSourceManager.spotifySession?.api()?.send(
                "GET",
                "/inspiredby-mix/v2/seed_to_playlist/$playableURI?response-format=json",
                null,
                null
            )
            val radioMetadata = JsonParser.parseReader(resp?.body?.charStream()).asJsonObject
            if (radioMetadata["total"].asNumber == 0) {
                return ResponseEntity(spotifyRestException().toString(), HttpStatus.OK)
            }
            val playableRadioUri = radioMetadata["mediaItems"].asJsonArray[0].asJsonObject["uri"].asString
            val matcherPlayableRadioUri = spotifyAudioSourceManager.SPOTIFY_URL_PATTERN.matcher(playableRadioUri)
            if (!matcherPlayableRadioUri.matches()) {
                return ResponseEntity(
                    spotifyRestException("Invalid Spotify URL.", Severity.COMMON).toString(),
                    HttpStatus.OK
                )
            }
            val playableRadioIdentifier = matcherPlayableRadioUri.group("identifier")
            val requestSpotifyPlaylist =
                SpotifyPlaylistResolver(spotifyAudioSourceManager, playableRadioIdentifier).fetch()
            return ResponseEntity(spotifyRecommendationsData(requestSpotifyPlaylist).toString(), HttpStatus.OK)
        } catch (e: Exception) {
            return ResponseEntity(spotifyRestException(e.message, Severity.COMMON).toString(), HttpStatus.OK)
        } finally {
            log.info("Loaded spotify recommendations for identifier: $url")
        }
    }

    private fun spotifyRestException(
        message: String? = "Spotify Rest got an exception",
        severity: Severity? = Severity.COMMON
    ): JSONObject {
        val json = JSONObject()
        val playlist = JSONObject()
        val exception = JSONObject()
        val tracks = JSONArray()

        json
            .put("playlistInfo", playlist)
            .put("loadType", "LOAD_FAILED")
            .put("tracks", tracks)

        exception
            .put("message", message)
            .put("severity", severity.toString())

        json.put("exception", exception)
        return json
    }

    private fun spotifyLyricsData(
        trackId: String? = null,
        trackName: String? = null,
        trackArtist: String? = null,
        trackUrl: String? = null,
        imageUrl: String? = null,
        language: String? = null,
        lyrics: List<String> = ArrayList()
    ): JSONObject {
        return JSONObject()
            .put("trackId", trackId)
            .put("trackName", trackName)
            .put("trackArtist", trackArtist)
            .put("trackUrl", trackUrl)
            .put("imageUrl", imageUrl)
            .put("language", language)
            .put("lyrics", lyrics)
    }

    private fun spotifyRecommendationsData(result: AudioPlaylist): JSONObject {
        val json = JSONObject()
        val playlist = JSONObject()
        val tracks = JSONArray()

        playlist
            .put("name", result.name)
            .put("selectedTrack", result.selectedTrack)

        result.tracks.forEach {
            val track = JSONObject()
            val trackInfo = JSONObject()

            trackInfo
                .put("identifier", it.info.identifier)
                .put("thumbnail", it.info.artworkUrl)
                .put("isSeekable", it.isSeekable)
                .put("author", it.info.author)
                .put("length", it.info.length)
                .put("isStream", it.info.isStream)
                .put("sourceName", spotifyAudioSourceManager.sourceName)
                .put("position", it.position)
                .put("title", it.info.title)
                .put("uri", it.info.uri)

            val encoded = Util.encodeAudioTrack(defaultAudioPlayerManager, it)

            track
                .put("track", encoded)
                .put("info", trackInfo)

            tracks
                .put(track)
        }

        json
            .put("playlistInfo", playlist)
            .put("loadType", "PLAYLIST_LOADED")
            .put("tracks", tracks)

        return json
    }

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

    private fun fetchSpotifyLyrics(spotifyTrackElement: JsonElement): JSONObject {
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
                return spotifyRestException("Spotify API returned ${response?.code}.", Severity.COMMON)
            }
            val data = JsonBrowser.parse(response.body?.string())
            val rawLyricsLines = data["lines"].values()
            val language = data["language"].safeText()
            val lyrics = ArrayList<String>()
            if (rawLyricsLines.isNullOrEmpty()) {
                return spotifyLyricsData()
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
            return spotifyLyricsData(trackId, trackName, trackArtist, trackUrl, imageUrl, language, lyrics)
        } catch (_: Exception) {
            return spotifyLyricsData()
        }
    }
}

