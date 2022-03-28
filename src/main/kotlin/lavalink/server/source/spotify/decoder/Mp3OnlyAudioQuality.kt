package lavalink.server.source.spotify.decoder

import com.spotify.metadata.Metadata
import org.slf4j.LoggerFactory
import xyz.gianlu.librespot.audio.decoders.AudioQuality
import xyz.gianlu.librespot.audio.format.AudioQualityPicker
import xyz.gianlu.librespot.audio.format.SuperAudioFormat
import xyz.gianlu.librespot.common.Utils

class Mp3OnlyAudioQuality(private val preferred: AudioQuality) : AudioQualityPicker {
    private val log = LoggerFactory.getLogger(Mp3OnlyAudioQuality::class.java)

    private fun getMp3File(files: List<Metadata.AudioFile>): Metadata.AudioFile? {
        for (file in files) {
            if (file.hasFormat() && SuperAudioFormat.get(file.format) == SuperAudioFormat.MP3) return file
        }
        return null
    }

    override fun getFile(files: List<Metadata.AudioFile>): Metadata.AudioFile? {
        val matches = preferred.getMatches(files)
        var mp3 = getMp3File(matches)
        if (mp3 == null) {
            mp3 = getMp3File(files)
            if (mp3 != null) {
                log.warn(
                    "Using {} because preferred {} couldn't be found.",
                    mp3.format,
                    preferred
                )
            } else {
                log.error("Couldn't find any Mp3 file, available: {}", Utils.formatsToString(files))
            }
        }
        return mp3
    }
}