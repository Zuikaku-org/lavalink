package lavalink.server.source.spotify

import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException
import com.sedmelluq.discord.lavaplayer.track.AudioItem
import com.sedmelluq.discord.lavaplayer.track.AudioReference
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo
import lavalink.server.source.spotify.resolver.*
import org.slf4j.LoggerFactory
import xyz.gianlu.librespot.core.Session
import xyz.gianlu.librespot.mercury.MercuryClient
import java.io.DataInput
import java.io.DataOutput
import java.io.IOException
import java.security.GeneralSecurityException
import java.util.regex.Pattern

class SpotifyAudioSourceManager(
    private var username: String,
    private var password: String,
    var audioQuality: String,
    var spotifyPlaylistLoadLimit: Int,
    private var allowSearch: Boolean
) : AudioSourceManager {
    private val log = LoggerFactory.getLogger(SpotifyAudioSourceManager::class.java)

    private val searchPrefix = "spsearch:"
    private val similarPrefix = "spsimilar:"
    private val spotifyRegex =
        "(?<link>(?:https://open\\.spotify\\.com/(?:user/[A-Za-z0-9]+/)?|spotify:)(?<type>album|playlist|track|artist|episode|show)([/:])(?<identifier>[A-Za-z0-9]+).*\$)"
    val spotifyPattern: Pattern = Pattern.compile(spotifyRegex)
    val spotifyAPI = "https://api.spotify.com/v1/"
    var spotifySession: Session? = null

    val userAgent =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (HTML, like Gecko) Chrome/97.0.4692.99 Safari/537.36"

    override fun getSourceName(): String {
        return "spotify"
    }

    init {
        try {
            createSpotifySession()
        } catch (e: Exception) {
            log.info("Failed to create spotify session, trying to connect again")
            spotifySession = null
        } finally {
            log.info("Spotify session created")
        }
    }

    @Throws(
        Session.SpotifyAuthenticationException::class,
        GeneralSecurityException::class,
        IOException::class,
        MercuryClient.MercuryException::class
    )
    private fun createSpotifySession() {
        spotifySession = null
        val spotifySessionConfiguration = Session.Configuration
            .Builder()
            .setRetryOnChunkError(false)
            .setCacheEnabled(false)
            .setStoreCredentials(true)
            .build()
        val buildSpotifySession = Session.Builder(spotifySessionConfiguration)
            .userPass(username, password)
        spotifySession = buildSpotifySession.create()
        spotifySession?.addCloseListener {
            createSpotifySession()
        }
    }


    override fun loadItem(manager: AudioPlayerManager, reference: AudioReference): AudioItem? {
        if (allowSearch && reference.identifier.startsWith(searchPrefix)) {
            return SpotifySearchResolver(this, reference.identifier.substring(searchPrefix.length)).fetch()
        }
        val matcher = spotifyPattern.matcher(
            if (reference.identifier.startsWith(similarPrefix)) {
                reference.identifier.substring(similarPrefix.length)
            } else {
                reference.identifier
            }
        )
        if (matcher.matches()) {
            val type = matcher.group("type")
            val identifier = matcher.group("identifier")
            if (allowSearch && reference.identifier.startsWith(similarPrefix)) {
                if (!type.equals("track")) {
                    throw FriendlyException("Only accept spotify track uri", FriendlyException.Severity.COMMON, null)
                }
                return SpotifySimilarResolver(this, identifier).fetch()
            }
            if (type == "track") {
                return SpotifyTrackResolver(this, identifier).fetch()
            }
            if (type == "playlist") {
                return SpotifyPlaylistResolver(this, identifier).fetch()
            }
            if (type == "album") {
                return SpotifyAlbumResolver(this, identifier).fetch()
            }
            if (type == "artist") {
                return SpotifyArtistResolver(this, identifier).fetch()
            }
            if (type == "episode") {
                return SpotifyEpisodeResolver(this, identifier).fetch()
            }
            if (type == "show") {
                return SpotifyShowResolver(this, identifier).fetch()
            }
        }
        return null
    }

    override fun isTrackEncodable(track: AudioTrack): Boolean {
        return true
    }

    @Throws(
        IOException::class
    )
    override fun encodeTrack(track: AudioTrack, output: DataOutput) {
        // No custom values that need saving
    }

    @Throws(
        IOException::class
    )
    override fun decodeTrack(trackInfo: AudioTrackInfo, input: DataInput): AudioTrack {
        return SpotifyAudioTrack(trackInfo, this)
    }

    override fun shutdown() {
        try {
            spotifySession?.close()
        } catch (e: Exception) {
            log.error("Error while closing Spotify session", e)
        }
    }
}