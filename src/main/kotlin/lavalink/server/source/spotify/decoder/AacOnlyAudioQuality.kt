package lavalink.server.source.spotify.decoder

import com.spotify.metadata.Metadata
import org.slf4j.LoggerFactory
import xyz.gianlu.librespot.audio.decoders.AudioQuality
import xyz.gianlu.librespot.audio.format.AudioQualityPicker
import xyz.gianlu.librespot.audio.format.SuperAudioFormat
import xyz.gianlu.librespot.common.Utils

class AacOnlyAudioQuality(private val preferred: AudioQuality) : AudioQualityPicker {
    private val log = LoggerFactory.getLogger(AacOnlyAudioQuality::class.java)

    private fun getAacFile(files: List<Metadata.AudioFile>): Metadata.AudioFile? {
        for (file in files) {
            if (file.hasFormat() && SuperAudioFormat.get(file.format) == SuperAudioFormat.AAC) return file
        }
        return null
    }

    override fun getFile(files: List<Metadata.AudioFile>): Metadata.AudioFile? {
        val matches = preferred.getMatches(files)
        var aac = getAacFile(matches)
        if (aac == null) {
            aac = getAacFile(files)
            if (aac != null) {
                log.warn(
                    "Using {} because preferred {} couldn't be found.",
                    aac.format,
                    preferred
                )
            } else {
                log.error("Couldn't find any Aac file, available: {}", Utils.formatsToString(files))
            }
        }
        return aac
    }
}