package lavalink.server.source.spotify.resolver

import com.sedmelluq.discord.lavaplayer.tools.FriendlyException
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser
import com.sedmelluq.discord.lavaplayer.track.*
import lavalink.server.source.spotify.SpotifyAudioSourceManager
import lavalink.server.source.spotify.SpotifyAudioTrack
import okhttp3.Request
import xyz.gianlu.librespot.metadata.ImageId
import xyz.gianlu.librespot.metadata.PlayableId
import xyz.gianlu.librespot.metadata.TrackId
import java.net.URI

class SpotifySimilarResolver(
    private val spotifyAudioSourceManager: SpotifyAudioSourceManager,
    private val identifier: String
) {
    fun fetch(): AudioItem {
        try {
            val similarMetadata = spotifyAudioSourceManager.spotifySession?.api()
                ?.getRadioForTrack(PlayableId.fromUri("spotify:track:$identifier"))
            if (similarMetadata?.get("total")!!.asNumber == 0) {
                return AudioReference.NO_TRACK
            }
            val similarUri = similarMetadata["mediaItems"]?.asJsonArray?.get(0)?.asJsonObject?.get("uri")!!.asString
            val playableSimilarUri = spotifyAudioSourceManager.spotifyPattern.matcher(similarUri)
            if (!playableSimilarUri.matches()) {
                return AudioReference.NO_TRACK
            }
            val playableSimilarIdentifier = playableSimilarUri.group("identifier")
            val uriRequest = URI.create(
                spotifyAudioSourceManager.spotifyAPI
                        + "playlists/"
                        + playableSimilarIdentifier
            ).toString()
            val request = Request
                .Builder()
                .url(uriRequest)
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
            val json = JsonBrowser.parse(response.body?.string())
            val tracks: MutableList<AudioTrack> = ArrayList()
            var nextURI = json["tracks"]["next"].text()
            val trackItems = json["tracks"]["items"].values()
            var pagePlaylist = -1
            while (
                nextURI != null &&
                (
                        spotifyAudioSourceManager.spotifyPlaylistLoadLimit == 0 ||
                                pagePlaylist < spotifyAudioSourceManager.spotifyPlaylistLoadLimit
                        )
            ) {
                val nextRequest = Request
                    .Builder()
                    .url(nextURI)
                    .addHeader(
                        "Authorization",
                        "Bearer " + spotifyAudioSourceManager.spotifySession?.tokens()?.get("playlist-read")
                    )
                    .addHeader(
                        "User-Agent",
                        spotifyAudioSourceManager.userAgent
                    )
                    .build()
                val nextResponse = spotifyAudioSourceManager.spotifySession?.client()?.newCall(nextRequest)?.execute()
                if (nextResponse?.code != 200) {
                    throw FriendlyException(
                        "Spotify API returned ${nextResponse?.code}.",
                        FriendlyException.Severity.COMMON,
                        null
                    )
                }
                val nextJson = JsonBrowser.parse(nextResponse.body?.string())
                nextURI = nextJson["next"].text()
                val nextTrackItems = nextJson["items"].values()
                for (nextItem in nextTrackItems) {
                    trackItems.add(nextItem)
                }
                pagePlaylist++
            }
            trackItems.removeIf { trackData: JsonBrowser -> trackData["track"].isNull }
            for (item in trackItems) {
                val trackMetadata = spotifyAudioSourceManager
                    .spotifySession?.api()
                    ?.getMetadata4Track(TrackId.fromUri("spotify:track:${item["track"]["id"].safeText()}"))
                val title = trackMetadata?.name
                val author = trackMetadata?.artistList?.get(0)?.name
                val length = trackMetadata?.duration?.toLong() ?: 0
                val id = String(PlayableId.BASE62.encode(trackMetadata?.gid?.toByteArray()))
                val uri = "https://open.spotify.com/track/$id"
                val thumbnail =
                    "https://i.scdn.co/image/${ImageId.biggestImage(trackMetadata!!.album.coverGroup)?.hexId()}"
                tracks.add(
                    SpotifyAudioTrack(
                        AudioTrackInfo(
                            title,
                            author,
                            length,
                            id,
                            false,
                            uri,
                            thumbnail
                        ),
                        spotifyAudioSourceManager
                    )
                )
            }
            return BasicAudioPlaylist(json["name"].safeText(), tracks, null, false)
        } catch (e: Throwable) {
            throw FriendlyException(e.message, FriendlyException.Severity.COMMON, e)
        }
    }
}