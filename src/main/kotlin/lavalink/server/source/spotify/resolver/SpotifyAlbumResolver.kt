package lavalink.server.source.spotify.resolver

import com.sedmelluq.discord.lavaplayer.tools.FriendlyException
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo
import com.sedmelluq.discord.lavaplayer.track.BasicAudioPlaylist
import lavalink.server.source.spotify.SpotifyAudioSourceManager
import lavalink.server.source.spotify.SpotifyAudioTrack
import xyz.gianlu.librespot.metadata.AlbumId
import xyz.gianlu.librespot.metadata.ImageId
import xyz.gianlu.librespot.metadata.PlayableId
import xyz.gianlu.librespot.metadata.TrackId

class SpotifyAlbumResolver(private val spotifyAudioSourceManager: SpotifyAudioSourceManager) {
    fun fetch(identifier: String): AudioPlaylist {
        try {
            val albumMetadata = spotifyAudioSourceManager
                .spotifySession?.api()?.getMetadata4Album(AlbumId.fromUri("spotify:album:$identifier"))
            val albumName = albumMetadata?.name
            val albumDiscLists = albumMetadata?.discList!!.iterator()
            val albumAudioTracks: MutableList<AudioTrack> = ArrayList()
            for (albumDiscs in albumDiscLists) {
                val albumTrackLists = albumDiscs.trackList.iterator()
                for (albumTracks in albumTrackLists) {
                    val albumTrack = spotifyAudioSourceManager
                        .spotifySession?.api()?.getMetadata4Track(
                            TrackId
                                .fromBase62(
                                    String(
                                        PlayableId.BASE62.encode(albumTracks?.gid?.toByteArray())
                                    )
                                )
                        )
                    val title = albumTrack?.name
                    val author = albumTrack?.artistList?.get(0)?.name
                    val length = albumTrack?.duration?.toLong() ?: 0
                    val id = String(PlayableId.BASE62.encode(albumTrack?.gid?.toByteArray()))
                    val uri = "https://open.spotify.com/track/$id"
                    val thumbnail = "https://i.scdn.co/image/${ImageId.biggestImage(albumMetadata.coverGroup)?.hexId()}"
                    albumAudioTracks.add(
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
            return BasicAudioPlaylist(albumName, albumAudioTracks, null, false)
        } catch (e: Throwable) {
            throw FriendlyException(e.message, FriendlyException.Severity.COMMON, e)
        }
    }
}