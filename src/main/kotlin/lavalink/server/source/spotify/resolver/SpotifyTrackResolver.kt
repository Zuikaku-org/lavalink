package lavalink.server.source.spotify.resolver

import com.sedmelluq.discord.lavaplayer.tools.FriendlyException
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo
import lavalink.server.source.spotify.SpotifyAudioSourceManager
import lavalink.server.source.spotify.SpotifyAudioTrack
import xyz.gianlu.librespot.metadata.ImageId
import xyz.gianlu.librespot.metadata.PlayableId
import xyz.gianlu.librespot.metadata.TrackId

class SpotifyTrackResolver(
    private val spotifyAudioSourceManager: SpotifyAudioSourceManager,
    private val identifier: String
) {
    fun fetch(): AudioTrack {
        try {
            val trackMetadata = spotifyAudioSourceManager
                .spotifySession?.api()?.getMetadata4Track(TrackId.fromUri("spotify:track:$identifier"))
            val title = trackMetadata?.name
            val author = trackMetadata?.artistList?.get(0)?.name
            val length = trackMetadata?.duration?.toLong() ?: 0
            val id = String(PlayableId.BASE62.encode(trackMetadata?.gid?.toByteArray()))
            val uri = "https://open.spotify.com/track/$id"
            val thumbnail = "https://i.scdn.co/image/${ImageId.biggestImage(trackMetadata!!.album.coverGroup)?.hexId()}"
            return SpotifyAudioTrack(
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
        } catch (e: Exception) {
            throw FriendlyException(e.message, FriendlyException.Severity.COMMON, e)
        }
    }
}