package lavalink.server.source.spotify.resolver

import com.sedmelluq.discord.lavaplayer.tools.FriendlyException
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo
import lavalink.server.source.spotify.SpotifyAudioSourceManager
import lavalink.server.source.spotify.SpotifyAudioTrack
import xyz.gianlu.librespot.metadata.EpisodeId
import xyz.gianlu.librespot.metadata.ImageId
import xyz.gianlu.librespot.metadata.PlayableId

class SpotifyEpisodeResolver(
    private val spotifyAudioSourceManager: SpotifyAudioSourceManager,
    private val identifier: String
) {
    fun fetch(): AudioTrack {
        try {
            val episodeMetadata = spotifyAudioSourceManager
                .spotifySession?.api()?.getMetadata4Episode(EpisodeId.fromUri("spotify:episode:$identifier"))
            val title = episodeMetadata?.name
            val length = episodeMetadata?.duration?.toLong() ?: 0
            val id = String(PlayableId.BASE62.encode(episodeMetadata?.gid?.toByteArray()))
            val uri = "https://open.spotify.com/episode/$id"
            val thumbnail = "https://i.scdn.co/image/${ImageId.biggestImage(episodeMetadata!!.coverImage)?.hexId()}"
            return SpotifyAudioTrack(
                AudioTrackInfo(
                    title,
                    "UNKNOWN ARTIST",
                    length,
                    id,
                    false,
                    uri,
                    thumbnail
                ),
                spotifyAudioSourceManager
            )
        } catch (e: Exception) {
            throw FriendlyException(e.message, FriendlyException.Severity.COMMON, e)
        }
    }
}