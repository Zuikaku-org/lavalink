package lavalink.server.source.spotify.resolver

import com.sedmelluq.discord.lavaplayer.tools.FriendlyException
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo
import com.sedmelluq.discord.lavaplayer.track.BasicAudioPlaylist
import lavalink.server.source.spotify.SpotifyAudioSourceManager
import lavalink.server.source.spotify.SpotifyAudioTrack
import xyz.gianlu.librespot.metadata.ArtistId
import xyz.gianlu.librespot.metadata.ImageId
import xyz.gianlu.librespot.metadata.PlayableId
import xyz.gianlu.librespot.metadata.TrackId

class SpotifyArtistResolver(
    private val spotifyAudioSourceManager: SpotifyAudioSourceManager,
    private val identifier: String
) {
    fun fetch(): AudioPlaylist {
        try {
            val artistMetadata = spotifyAudioSourceManager
                .spotifySession?.api()?.getMetadata4Artist(ArtistId.fromUri("spotify:artist:$identifier"))
            val artistName = artistMetadata?.name
            val artistTopTrackLists = artistMetadata?.topTrackList!!.iterator()
            val artistAudioTracks: MutableList<AudioTrack> = ArrayList()
            for (artistTopTracks in artistTopTrackLists) {
                val topTrackLists = artistTopTracks.trackList.iterator()
                for (topTracks in topTrackLists) {
                    val topTrack = spotifyAudioSourceManager
                        .spotifySession?.api()?.getMetadata4Track(
                            TrackId
                                .fromBase62(
                                    String(
                                        PlayableId.BASE62.encode(topTracks?.gid?.toByteArray())
                                    )
                                )
                        )
                    val title = topTrack?.name
                    val author = topTrack?.artistList?.get(0)?.name
                    val length = topTrack?.duration?.toLong() ?: 0
                    val id = String(PlayableId.BASE62.encode(topTrack?.gid?.toByteArray()))
                    val uri = "https://open.spotify.com/track/$id"
                    val thumbnail =
                        "https://i.scdn.co/image/${ImageId.biggestImage(topTrack?.album!!.coverGroup)?.hexId()}"
                    artistAudioTracks.add(
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
            }
            return BasicAudioPlaylist(artistName, artistAudioTracks, null, false)
        } catch (e: Throwable) {
            throw FriendlyException(e.message, FriendlyException.Severity.COMMON, e)
        }
    }
}