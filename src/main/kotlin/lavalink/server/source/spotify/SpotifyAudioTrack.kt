package lavalink.server.source.spotify

import com.sedmelluq.discord.lavaplayer.container.mp3.Mp3AudioTrack
import com.sedmelluq.discord.lavaplayer.container.mpeg.MpegAudioTrack
import com.sedmelluq.discord.lavaplayer.container.ogg.OggAudioTrack
import com.sedmelluq.discord.lavaplayer.tools.io.NonSeekableInputStream
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo
import com.sedmelluq.discord.lavaplayer.track.DelegatedAudioTrack
import com.sedmelluq.discord.lavaplayer.track.InternalAudioTrack
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor
import com.spotify.metadata.Metadata.*
import lavalink.server.source.spotify.decoder.AacOnlyAudioQuality
import lavalink.server.source.spotify.decoder.Mp3OnlyAudioQuality
import org.slf4j.LoggerFactory
import xyz.gianlu.librespot.audio.decoders.AudioQuality
import xyz.gianlu.librespot.audio.decoders.VorbisOnlyAudioQuality
import xyz.gianlu.librespot.audio.format.AudioQualityPicker
import xyz.gianlu.librespot.audio.format.SuperAudioFormat
import xyz.gianlu.librespot.metadata.EpisodeId
import xyz.gianlu.librespot.metadata.PlayableId
import xyz.gianlu.librespot.metadata.TrackId

class SpotifyAudioTrack(trackInfo: AudioTrackInfo?, private val spotifyAudioSourceManager: SpotifyAudioSourceManager) :
    DelegatedAudioTrack(trackInfo) {
    private val log = LoggerFactory.getLogger(SpotifyAudioTrack::class.java)
    private var audioQualityPreferred = AudioQuality.valueOf(spotifyAudioSourceManager.audioQuality.uppercase())

    @Throws(Exception::class)
    override fun process(executor: LocalAudioTrackExecutor) {
        try {
            val playableURI =
                if (trackInfo.uri.startsWith("https://open.spotify.com/episode")) {
                    "spotify:episode:" + trackInfo.identifier
                } else {
                    "spotify:track:" + trackInfo.identifier
                }
            val metadataTrackOrEpisode =
                if (trackInfo.uri.startsWith("https://open.spotify.com/episode")) {
                    spotifyAudioSourceManager
                        .spotifySession?.api()?.getMetadata4Episode(EpisodeId.fromUri(playableURI))
                } else {
                    spotifyAudioSourceManager
                        .spotifySession?.api()?.getMetadata4Track(TrackId.fromUri(playableURI))
                }
            var audioFile: List<AudioFile> = ArrayList()
            if (metadataTrackOrEpisode is Episode) {
                audioFile = metadataTrackOrEpisode.audioList
            }
            if (metadataTrackOrEpisode is Track) {
                audioFile = metadataTrackOrEpisode.fileList
            }
            val audioFormat: MutableList<SuperAudioFormat> = ArrayList()
            for (audioFileIterator in audioFile.iterator()) {
                audioFormat.add((SuperAudioFormat::get)(audioFileIterator.format))
            }
            var finalAudioFile: AudioFile? = null
            if (
                audioFormat.contains(SuperAudioFormat.VORBIS)
            ) {
                finalAudioFile = VorbisOnlyAudioQuality(audioQualityPreferred).getFile(audioFile)
            } else if (
                !audioFormat.contains(SuperAudioFormat.VORBIS) &&
                audioFormat.contains(SuperAudioFormat.AAC)
            ) {
                finalAudioFile = AacOnlyAudioQuality(audioQualityPreferred).getFile(audioFile)
            } else if (
                !audioFormat.contains(SuperAudioFormat.VORBIS) &&
                !audioFormat.contains(SuperAudioFormat.AAC) &&
                audioFormat.contains(SuperAudioFormat.AAC)
            ) {
                finalAudioFile = Mp3OnlyAudioQuality(audioQualityPreferred).getFile(audioFile)
            }
            val playableId = PlayableId.fromUri(playableURI)
            val loadedStream = spotifyAudioSourceManager
                .spotifySession?.contentFeeder()?.load(
                    playableId,
                    (if (finalAudioFile == null) {
                        VorbisOnlyAudioQuality(audioQualityPreferred)
                    } else {
                        AudioQualityPicker { finalAudioFile }
                    }),
                    true,
                    null
                )
            // TODO(seekable spotify)
            var internalAudioTrack: InternalAudioTrack? = null
            if (loadedStream?.`in`?.codec() == SuperAudioFormat.VORBIS) {
                internalAudioTrack = OggAudioTrack(trackInfo, NonSeekableInputStream(loadedStream.`in`?.stream()))
            } else if (loadedStream?.`in`?.codec() == SuperAudioFormat.AAC) {
                internalAudioTrack = MpegAudioTrack(trackInfo, NonSeekableInputStream(loadedStream.`in`?.stream()))
            } else if (loadedStream?.`in`?.codec() == SuperAudioFormat.MP3) {
                internalAudioTrack = Mp3AudioTrack(trackInfo, NonSeekableInputStream(loadedStream.`in`?.stream()))
            }
            this.processDelegate(internalAudioTrack, executor)
        } catch (e: Exception) {
            log.error("Failed to load track. got error when processing track")
            throw e
        }
    }

    override fun makeShallowClone(): AudioTrack {
        return SpotifyAudioTrack(this.trackInfo, spotifyAudioSourceManager)
    }

    override fun getSourceManager(): SpotifyAudioSourceManager {
        return spotifyAudioSourceManager
    }
}