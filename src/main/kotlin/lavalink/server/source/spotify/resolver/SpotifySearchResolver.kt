package lavalink.server.source.spotify.resolver

import com.sedmelluq.discord.lavaplayer.tools.FriendlyException
import com.sedmelluq.discord.lavaplayer.track.*
import lavalink.server.source.spotify.SpotifyAudioSourceManager
import lavalink.server.source.spotify.SpotifyAudioTrack
import okio.ByteString.Companion.toByteString
import xyz.gianlu.librespot.core.SearchManager
import xyz.gianlu.librespot.metadata.PlayableId
import xyz.gianlu.librespot.metadata.TrackId

class SpotifySearchResolver(private val spotifyAudioSourceManager: SpotifyAudioSourceManager) {
    fun fetch(query: String): AudioItem {
        try {
            val spotifySearchManager = spotifyAudioSourceManager
                .spotifySession?.search()?.request(SearchManager.SearchRequest(query))
            val spotifySearchResults = spotifySearchManager?.getAsJsonObject("results")
            val spotifyTracksProperty = spotifySearchResults?.getAsJsonObject("tracks")
            val spotifyHitsProperty = spotifyTracksProperty?.getAsJsonArray("hits")
            val spotifyTracks: MutableList<AudioTrack> = ArrayList()
            if (spotifyHitsProperty!!.isEmpty) {
                return AudioReference.NO_TRACK
            }
            for (spotifyTrack in spotifyHitsProperty) {
                val title = spotifyTrack.asJsonObject.get("name").asString
                val author = spotifyTrack.asJsonObject.get("artists").asJsonArray[0].asJsonObject.get("name").asString
                val length = spotifyTrack.asJsonObject.get("duration").asInt.toLong()
                val id = String(
                    PlayableId.BASE62.encode(
                        TrackId.fromUri(
                            spotifyTrack.asJsonObject.get("uri").asString
                        )
                            .gid
                            .toByteString()
                            .toByteArray()
                    )
                )
                val uri = "https://open.spotify.com/track/$id"
                val thumbnail = spotifyTrack.asJsonObject.get("image").asString
                spotifyTracks.add(
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
            if (spotifyTracks.isEmpty()) {
                return AudioReference.NO_TRACK
            }
            return BasicAudioPlaylist("Search result for: $query", spotifyTracks, null, true)
        } catch (e: Throwable) {
            throw FriendlyException(e.message, FriendlyException.Severity.COMMON, e)
        }
    }
}
