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
    var spotifyPlaylistLoadLimit: Int
) : AudioSourceManager {
    private val SEARCH_PREFIX = "spsearch:"
    private val SPOTIFY_REGEX =
        "(?<link>(?:https://open\\.spotify\\.com/(?:user/[A-Za-z0-9]+/)?|spotify:)(?<type>album|playlist|track|artist|episode|show)([/:])(?<identifier>[A-Za-z0-9]+).*\$)"
    private val SPOTIFY_URL_PATTERN = Pattern.compile(SPOTIFY_REGEX)

    val spotifyAPI = "https://api.spotify.com/v1/"
    val userAgent =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/97.0.4692.99 Safari/537.36"

    private var spotifyTrackResolver: SpotifyTrackResolver? = null
    private var spotifySearchResolver: SpotifySearchResolver? = null
    private var spotifyPlaylistResolver: SpotifyPlaylistResolver? = null
    private var spotifyAlbumResolver: SpotifyAlbumResolver? = null
    private var spotifyArtistResolver: SpotifyArtistResolver? = null
    private var spotifyEpisodeResolver: SpotifyEpisodeResolver? = null
    private var spotifyShowResolver: SpotifyShowResolver? = null

    var spotifySession: Session? = null

    private val thread: Thread
    private val log = LoggerFactory.getLogger(SpotifyAudioSourceManager::class.java)

    override fun getSourceName(): String {
        return "spotify"
    }

    init {
        thread = Thread {
            while (!spotifySession!!.isValid) {
                try {
                    createSpotifySession()
                } catch (e: Exception) {
                    log.info("Failed to create spotify session, reconnect in 10 seconds", e)
                    spotifySession = null
                    Thread.sleep(10000L)
                } finally {
                    log.info("Spotify session created")
                    Thread.sleep(3600000L)
                }
            }
        }
        thread.isDaemon = true
        thread.start()
        initResolver()
    }

    private fun initResolver() {
        spotifySearchResolver = SpotifySearchResolver(this)
        spotifyTrackResolver = SpotifyTrackResolver(this)
        spotifyPlaylistResolver = SpotifyPlaylistResolver(this)
        spotifyAlbumResolver = SpotifyAlbumResolver(this)
        spotifyArtistResolver = SpotifyArtistResolver(this)
        spotifyEpisodeResolver = SpotifyEpisodeResolver(this)
        spotifyShowResolver = SpotifyShowResolver(this)
    }

    @Throws(
        Session.SpotifyAuthenticationException::class,
        GeneralSecurityException::class,
        IOException::class,
        MercuryClient.MercuryException::class
    )
    private fun createSpotifySession() {
        val spotifySessionConfiguration = Session.Configuration
            .Builder()
            .setRetryOnChunkError(false)
            .setCacheEnabled(false)
            .setStoreCredentials(true)
            .setConnectionTimeout(5)
            .build()
        val buildSpotifySession = Session
            .Builder(spotifySessionConfiguration)
            .userPass(username, password)
        spotifySession = buildSpotifySession.create()
    }

    override fun loadItem(manager: AudioPlayerManager, reference: AudioReference): AudioItem? {
        if (reference.identifier.startsWith(SEARCH_PREFIX)) {
            return spotifySearchResolver!!
                .fetch(reference.identifier.substring(SEARCH_PREFIX.length))
        }
        val matcher = SPOTIFY_URL_PATTERN.matcher(reference.identifier)
        if (!matcher.matches()) {
            throw FriendlyException("Invalid Spotify URL.", FriendlyException.Severity.COMMON, null)
        }
        val type = matcher.group("type")
        val identifier = matcher.group("identifier")
        if (type == "track") {
            return spotifyTrackResolver!!.fetch(identifier)
        }
        if (type == "playlist") {
            return spotifyPlaylistResolver!!.fetch(identifier)
        }
        if (type == "album") {
            return spotifyAlbumResolver!!.fetch(identifier)
        }
        if (type == "artist") {
            return spotifyArtistResolver!!.fetch(identifier)
        }
        if (type == "episode") {
            return spotifyEpisodeResolver!!.fetch(identifier)
        }
        if (type == "show") {
            return spotifyShowResolver!!.fetch(identifier)
        }
        return null
    }

    override fun isTrackEncodable(track: AudioTrack): Boolean {
        return true
    }

    override fun encodeTrack(track: AudioTrack, output: DataOutput) {}
    override fun decodeTrack(trackInfo: AudioTrackInfo, input: DataInput): AudioTrack {
        return SpotifyAudioTrack(trackInfo, this)
    }

    override fun shutdown() {
        thread.interrupt()
        try {
            spotifySession?.close()
        } catch (e: Exception) {
            log.error("Error while closing Spotify session", e)
        }
    }
}