package lavalink.server.source.spotify

import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException
import com.sedmelluq.discord.lavaplayer.track.AudioItem
import com.sedmelluq.discord.lavaplayer.track.AudioReference
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo
import lavalink.server.config.SpotifyConfig
import lavalink.server.source.spotify.resolver.*
import org.slf4j.LoggerFactory
import xyz.gianlu.librespot.core.Session
import java.io.DataInput
import java.io.DataOutput
import java.io.File
import java.io.IOException
import java.net.Proxy
import java.util.regex.Pattern

class SpotifyAudioSourceManager(
    var spotifyConfig: SpotifyConfig?,
    var spotifyPlaylistLoadLimit: Int,
    private var allowSearch: Boolean,
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

    private lateinit var thread: Thread
    private val useCredentials = File("credentials.json").exists()

    override fun getSourceName(): String {
        return "spotify"
    }

    init {
        createSpotifySession()
        thread.isDaemon = true
        thread.start()
    }

    private fun createSpotifySession() {
        thread = Thread {
            spotifySession = null
            try {
                val spotifySessionConfiguration = Session.Configuration
                    .Builder()
                    .setRetryOnChunkError(true)
                    .setCacheEnabled(false)
                    .setStoreCredentials(true)
                if (spotifyConfig?.proxy?.useProxy == true) {
                    spotifySessionConfiguration
                        .setProxyEnabled(true)
                        .setProxyAddress(spotifyConfig?.proxy?.proxyAddress)
                        .setProxyPort(spotifyConfig?.proxy?.proxyPort as Int)
                    if (!spotifyConfig?.proxy?.proxyUsername.isNullOrEmpty()) {
                        spotifySessionConfiguration
                            .setProxyAuth(true)
                            .setProxyUsername(spotifyConfig?.proxy?.proxyUsername)
                            .setProxyPassword(spotifyConfig?.proxy?.proxyPassword)
                    }
                    spotifySessionConfiguration
                        .setProxyType(spotifyConfig?.proxy?.proxyType?.let { Proxy.Type.valueOf(it.uppercase()) })
                }
                val buildSpotifyConfiguration = spotifySessionConfiguration.build()
                val buildSpotifySession: Session.Builder = Session.Builder(buildSpotifyConfiguration)
                if (useCredentials) {
                    log.info("Authenticates with stored credentials")
                    buildSpotifySession.stored()
                } else {
                    log.info("Authenticates with username and password")
                    buildSpotifySession.userPass(spotifyConfig?.spotifyUsername!!, spotifyConfig?.spotifyPassword!!)
                }
                spotifySession = buildSpotifySession.create()
                spotifySession?.addCloseListener {
                    log.info("Session closed, trying to connect again in 10 seconds")
                    Thread.sleep(10000)
                    createSpotifySession()
                }
            } catch (e: Exception) {
                log.info("Failed to create spotify session, trying to connect again in 10 seconds")
                Thread.sleep(10000)
                createSpotifySession()
            }
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
            thread.interrupt()
            spotifySession?.close()
        } catch (e: Exception) {
            log.error("Error while closing Spotify session", e)
        }
    }
}