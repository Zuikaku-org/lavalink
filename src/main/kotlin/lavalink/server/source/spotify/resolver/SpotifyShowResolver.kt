package lavalink.server.source.spotify.resolver

import com.sedmelluq.discord.lavaplayer.tools.FriendlyException
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo
import com.sedmelluq.discord.lavaplayer.track.BasicAudioPlaylist
import lavalink.server.source.spotify.SpotifyAudioSourceManager
import lavalink.server.source.spotify.SpotifyAudioTrack
import xyz.gianlu.librespot.metadata.EpisodeId
import xyz.gianlu.librespot.metadata.ImageId
import xyz.gianlu.librespot.metadata.PlayableId
import xyz.gianlu.librespot.metadata.ShowId

class SpotifyShowResolver(private val spotifyAudioSourceManager: SpotifyAudioSourceManager) {
    fun fetch(identifier: String): AudioPlaylist {
        try {
            val showMetadata = spotifyAudioSourceManager
                .spotifySession?.api()?.getMetadata4Show(ShowId.fromUri("spotify:show:$identifier"))
            val showName = showMetadata?.name
            val showEpisodeLists = showMetadata?.episodeList!!.iterator()
            val showAudioTracks: MutableList<AudioTrack> = ArrayList()
            for (showEpisodes in showEpisodeLists) {
                val episodeMetadata = spotifyAudioSourceManager
                    .spotifySession?.api()?.getMetadata4Episode(
                        EpisodeId
                            .fromBase62(
                                String(
                                    PlayableId.BASE62.encode(showEpisodes?.gid?.toByteArray())
                                )
                            )
                    )
                val title = episodeMetadata?.name
                val length = episodeMetadata?.duration?.toLong() ?: 0
                val id = String(PlayableId.BASE62.encode(episodeMetadata?.gid?.toByteArray()))
                val uri = "https://open.spotify.com/episode/$id"
                val thumbnail = "https://i.scdn.co/image/${ImageId.biggestImage(episodeMetadata!!.coverImage)?.hexId()}"
                showAudioTracks.add(
                    SpotifyAudioTrack(
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
                )
            }
            return BasicAudioPlaylist(showName, showAudioTracks.reversed(), null, false)
        } catch (e: Throwable) {
            throw FriendlyException(e.message, FriendlyException.Severity.COMMON, e)
        }
    }
}