/*
 * Kreate
 * Copyright (C) 2026 KnightHat
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package me.knighthat.kreate3.preferences

import me.knighthat.kreate3.constant.AutoTapAction
import me.knighthat.kreate3.constant.ContrastLevel
import me.knighthat.kreate3.constant.OpenSubsonicAuth
import me.knighthat.kreate3.constant.PlaybackFailureAction
import me.knighthat.kreate3.constant.ProxyType
import me.knighthat.kreate3.constant.SleepTimerStep
import me.knighthat.kreate3.constant.StreamQuality
import me.knighthat.kreate3.constant.SyncMode
import me.knighthat.kreate3.constant.ThemeMode
import me.knighthat.kreate3.model.LyricsProvider
import me.knighthat.kreate3.model.LyricsSync
import me.knighthat.kreate3.preferences.Preferences.AUTO_ALBUM_TAP
import me.knighthat.kreate3.preferences.Preferences.BACK_EXIT_DISABLED
import me.knighthat.kreate3.preferences.Preferences.EXIT_CONFIRMATION
import me.knighthat.kreate3.preferences.Preferences.EXIT_CONFIRMATION_TIMEOUT
import me.knighthat.kreate3.preferences.Preferences.HOME_SONGS_ORIGINS
import me.knighthat.kreate3.preferences.Preferences.HOME_SONGS_TAP_RADIO
import me.knighthat.kreate3.preferences.Preferences.IMAGE_DISK_CACHE
import me.knighthat.kreate3.preferences.Preferences.MEDIA3_CACHE_SIZE
import me.knighthat.kreate3.preferences.Preferences.NETWORK_CONNECT_TIMEOUT
import me.knighthat.kreate3.preferences.Preferences.NETWORK_PROXY_ENABLED
import me.knighthat.kreate3.preferences.Preferences.NETWORK_PROXY_HOST
import me.knighthat.kreate3.preferences.Preferences.NETWORK_PROXY_PORT
import me.knighthat.kreate3.preferences.Preferences.NETWORK_PROXY_TYPE
import me.knighthat.kreate3.preferences.Preferences.OPEN_SUBSONIC_CERTIFICATE_SHA256
import me.knighthat.kreate3.preferences.Preferences.OPEN_SUBSONIC_PROXY_ENABLED
import me.knighthat.kreate3.preferences.Preferences.OPEN_SUBSONIC_PROXY_HOST
import me.knighthat.kreate3.preferences.Preferences.OPEN_SUBSONIC_PROXY_PORT
import me.knighthat.kreate3.preferences.Preferences.OPEN_SUBSONIC_PROXY_TYPE
import me.knighthat.kreate3.preferences.Preferences.OPEN_SUBSONIC_QUALITY_WIFI
import me.knighthat.kreate3.preferences.Preferences.OPEN_SUBSONIC_SYNC_ALBUMS
import me.knighthat.kreate3.preferences.Preferences.PLAYBACK_BUFFER_AHEAD_SECONDS
import me.knighthat.kreate3.preferences.Preferences.PLAYBACK_STALL_SECONDS
import me.knighthat.kreate3.preferences.Preferences.YOUTUBE_ACCOUNT_ENABLED
import me.knighthat.kreate3.preferences.Preferences.YOUTUBE_PROXY_ENABLED
import me.knighthat.kreate3.preferences.Preferences.YOUTUBE_PROXY_HOST
import me.knighthat.kreate3.preferences.Preferences.YOUTUBE_PROXY_PORT
import me.knighthat.kreate3.preferences.Preferences.YOUTUBE_PROXY_TYPE
import me.knighthat.kreate3.preferences.Preferences.YOUTUBE_QUALITY_WIFI
import me.knighthat.kreate3.preferences.Preferences.YOUTUBE_QUALITY_WIFI_PREMIUM
import me.knighthat.kreate3.preferences.Preferences.YOUTUBE_SYNC_ALBUMS
import me.knighthat.kreate3.preferences.Preferences.YOUTUBE_SYNC_SONGS
import me.knighthat.kreate3.preferences.Preferences.awaitLoaded
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Every setting of the app. Values belong to the active profile.
 *
 * Settings are created on first use. Wait for [awaitLoaded] (for example behind
 * the splash screen) before showing UI, otherwise defaults are shown first.
 *
 * Keys are stored on disk, never rename one without a step in [PROFILE_STEPS].
 */
object Preferences : KoinComponent {

    private val engine: PreferenceEngine by inject()

    val THEME_MODE by lazy { enum("theme_mode", ThemeMode.THUMBNAIL) }
    val CONTRAST_LEVEL by lazy { enum("contrast_level", ContrastLevel.NORMAL) }

    /**
     * The bottom bar, the origin chips and the quick actions of detail screens show only their
     * icons, no text.
     */
    val ICON_ONLY by lazy { boolean("icon_only", false) }

    /**
     * A playlist with no picture and no song covers to show shows the first letter of its name,
     * on a color picked from the name, instead of a playlist icon. Read by `PlaylistItem` through
     * `LocalPlaylistInitial`.
     */
    val PLAYLIST_INITIAL by lazy { boolean("playlist_initial", false) }

    /**
     * Going back from the first screen asks to go back again before the app closes, see
     * [EXIT_CONFIRMATION_TIMEOUT]. [BACK_EXIT_DISABLED] wins over it.
     */
    val EXIT_CONFIRMATION by lazy { boolean("exit_confirmation", true) }

    /** Seconds [EXIT_CONFIRMATION] waits for the second time, it asks again after. */
    val EXIT_CONFIRMATION_TIMEOUT by lazy { int("exit_confirmation_timeout", 2) }

    /** Going back from the first screen does nothing, the app is never closed that way. */
    val BACK_EXIT_DISABLED by lazy { boolean("back_exit_disabled", false) }

    /**
     * Minutes between two scans of the device's music. The system runs periodic work
     * at most every 15 minutes, so smaller values behave like 15.
     */
    val LOCAL_SCAN_INTERVAL by lazy { int("local_scan_interval", 30) }

    /**
     * SHA-256 checksum of the Terms of Service this profile has accepted,
     * empty until it accepts.
     *
     * It is kept per profile on purpose: when the terms change, every profile
     * has to accept the new version itself, one profile accepting does not
     * cover the others.
     *
     * It is read at every launch, so it is declared eagerly instead of `by lazy`.
     */
    val TOS_CHECKSUM = string("tos_checksum", "")

    //<editor-fold defaultstate="collapsed" desc="Home">
    /**
     * Names of the origins the songs tab shows, every origin while it is empty. The app's
     * `Origin` isn't known here, so the names are kept as they are.
     */
    val HOME_SONGS_ORIGINS by lazy { stringSet("home_songs_origins", emptySet()) }

    /**
     * What tapping a song in the songs tab starts, both a new queue. Off by default: the song
     * plays followed by the songs below it in the list. On: the song plays followed by songs
     * like it, a radio. Albums, artists and playlists always play the list from the song.
     */
    val HOME_SONGS_TAP_RADIO by lazy { boolean("home_songs_tap_radio", false) }

    /** Like [HOME_SONGS_ORIGINS], for the albums tab. */
    val HOME_ALBUMS_ORIGINS by lazy { stringSet("home_albums_origins", emptySet()) }

    /** Like [HOME_SONGS_ORIGINS], for the artists tab. */
    val HOME_ARTISTS_ORIGINS by lazy { stringSet("home_artists_origins", emptySet()) }

    /** Like [HOME_SONGS_ORIGINS], for the playlists tab. */
    val HOME_PLAYLISTS_ORIGINS by lazy { stringSet("home_playlists_origins", emptySet()) }
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="Search">
    /**
     * Name of the origin the search looks in, kept from one search to the next. The app's
     * `Origin` isn't known here, so the name is kept as it is.
     */
    val SEARCH_ORIGIN by lazy { string("search_origin", "LOCAL") }

    /** Like [HOME_SONGS_TAP_RADIO], for a song tapped in the search results. */
    val SEARCH_SONGS_TAP_RADIO by lazy { boolean("search_songs_tap_radio", false) }
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="Network">
    /**
     * Seconds to wait for a connection before giving up. The default is 15.
     *
     * The client is built once, so a new value applies from the next launch.
     */
    val NETWORK_CONNECT_TIMEOUT by lazy { int("network_connect_timeout", 15) }

    /**
     * Seconds to wait for the next bytes of a response before giving up. The default i/s 30.
     *
     * Applies from the next launch, like [NETWORK_CONNECT_TIMEOUT].
     */
    val NETWORK_READ_TIMEOUT by lazy { int("network_read_timeout", 30) }

    /**
     * How many times a request that got a server error is sent again. The default is 3.
     *
     * Applies from the next launch, like [NETWORK_CONNECT_TIMEOUT].
     */
    val NETWORK_MAX_RETRIES by lazy { int("network_max_retries", 3) }

    /**
     * Whether the app's requests, lyrics and sources alike, go through the proxy set by
     * [NETWORK_PROXY_TYPE], [NETWORK_PROXY_HOST] and [NETWORK_PROXY_PORT], signed in with
     * `Secrets.NETWORK_PROXY_USERNAME` when it is set. A source with its own proxy on, like
     * [YOUTUBE_PROXY_ENABLED], goes through that one instead. Off by default. While it is on and
     * the proxy isn't complete, none of the requests it covers is sent. A change applies to the
     * next request.
     */
    val NETWORK_PROXY_ENABLED by lazy { boolean("network_proxy_enabled", false) }

    /** Like [YOUTUBE_PROXY_TYPE], for [NETWORK_PROXY_ENABLED]. */
    val NETWORK_PROXY_TYPE by lazy { enum("network_proxy_type", ProxyType.HTTP) }

    /** Like [YOUTUBE_PROXY_HOST], for [NETWORK_PROXY_ENABLED]. */
    val NETWORK_PROXY_HOST by lazy { string("network_proxy_host", "") }

    /** Like [YOUTUBE_PROXY_PORT], for [NETWORK_PROXY_ENABLED]. */
    val NETWORK_PROXY_PORT by lazy { int("network_proxy_port", 0) }

    /**
     * Whether the OpenSubsonic server is in use. Off by default.
     *
     * While it is off nothing may contact the server, however complete the address and
     * credentials are. Check it before every request.
     */
    val OPEN_SUBSONIC_ENABLED by lazy { boolean("open_subsonic_enabled", false) }

    /**
     * Address of the OpenSubsonic server without `/rest`, like `https://music.example.com`.
     * Empty until the user sets one.
     */
    val OPEN_SUBSONIC_URL by lazy { string("open_subsonic_url", "") }

    /** Which credentials sign in, see [OpenSubsonicAuth]. */
    val OPEN_SUBSONIC_AUTH by lazy { enum("open_subsonic_auth", OpenSubsonicAuth.PASSWORD) }

    /**
     * Whether a server whose certificate chain ends in a self-signed certificate is trusted.
     * Off by default. The first such certificate seen is remembered in
     * [OPEN_SUBSONIC_CERTIFICATE_SHA256], and a different one is refused until the user trusts it.
     */
    val OPEN_SUBSONIC_ALLOW_SELF_SIGNED by lazy {
        boolean(
            "open_subsonic_allow_self_signed",
            false
        )
    }

    /**
     * Songs of the OpenSubsonic server are streamed as they play, never kept in the cache of
     * streamed songs nor read from it. On by default. Downloaded songs are still played from
     * the device.
     */
    val OPEN_SUBSONIC_DIRECT_PLAY by lazy { boolean("open_subsonic_direct_play", true) }

    /**
     * Loudness, in LUFS, the server's files were tagged against, which their ReplayGain brings
     * them to: -18 (ReplayGain 2) by default. `LoudnessNormalizer` follows it while playing.
     */
    val OPEN_SUBSONIC_REPLAY_GAIN_LUFS by lazy { int("open_subsonic_replay_gain_lufs", -18) }

    /**
     * How good the songs of the OpenSubsonic server streamed on Wi-Fi and other networks that
     * aren't metered are. [StreamQuality.ORIGINAL] by default, the file as it is. Other qualities
     * are a highest bitrate: the server converts the songs above it, in the format it picked.
     *
     * A song goes on in the quality it started in, the new one applies from the next song.
     */
    val OPEN_SUBSONIC_QUALITY_WIFI by lazy {
        enum("open_subsonic_quality_wifi", StreamQuality.ORIGINAL)
    }

    /**
     * Like [OPEN_SUBSONIC_QUALITY_WIFI], on mobile data and other metered networks.
     * [StreamQuality.HIGH] by default.
     */
    val OPEN_SUBSONIC_QUALITY_MOBILE by lazy {
        enum("open_subsonic_quality_mobile", StreamQuality.HIGH)
    }

    /** Like [YOUTUBE_SYNC_SONGS], for the songs starred on the OpenSubsonic server. */
    val OPEN_SUBSONIC_SYNC_SONGS by lazy { boolean("open_subsonic_sync_songs", true) }

    /**
     * Which albums of the OpenSubsonic server show in the library: the starred ones
     * ([SyncMode.LIKED]), every one ([SyncMode.ALL], by default) or none.
     */
    val OPEN_SUBSONIC_SYNC_ALBUMS by lazy { enum("open_subsonic_sync_albums", SyncMode.ALL) }

    /** Like [OPEN_SUBSONIC_SYNC_ALBUMS], for the artists on the OpenSubsonic server. */
    val OPEN_SUBSONIC_SYNC_ARTISTS by lazy { enum("open_subsonic_sync_artists", SyncMode.ALL) }

    /** Like [YOUTUBE_SYNC_ALBUMS], for the playlists on the OpenSubsonic server. */
    val OPEN_SUBSONIC_SYNC_PLAYLISTS by lazy { boolean("open_subsonic_sync_playlists", true) }

    /** Host the remembered certificate belongs to, empty when none is remembered. */
    val OPEN_SUBSONIC_CERTIFICATE_HOST by lazy { string("open_subsonic_certificate_host", "") }

    /** SHA-256 fingerprint of the remembered self-signed certificate, empty when none is. */
    val OPEN_SUBSONIC_CERTIFICATE_SHA256 by lazy { string("open_subsonic_certificate_sha256", "") }

    /**
     * Whether every request to the OpenSubsonic server, streams and covers too, goes through the
     * proxy set by [OPEN_SUBSONIC_PROXY_TYPE], [OPEN_SUBSONIC_PROXY_HOST] and
     * [OPEN_SUBSONIC_PROXY_PORT], in place of [NETWORK_PROXY_ENABLED]'s. Off by default, the
     * server then goes through the app's proxy. While it is on and the proxy isn't complete,
     * nothing is sent to the server. A change applies to the next request.
     */
    val OPEN_SUBSONIC_PROXY_ENABLED by lazy { boolean("open_subsonic_proxy_enabled", false) }

    /** Like [YOUTUBE_PROXY_TYPE], for [OPEN_SUBSONIC_PROXY_ENABLED]. */
    val OPEN_SUBSONIC_PROXY_TYPE by lazy { enum("open_subsonic_proxy_type", ProxyType.HTTP) }

    /** Like [YOUTUBE_PROXY_HOST], for [OPEN_SUBSONIC_PROXY_ENABLED]. */
    val OPEN_SUBSONIC_PROXY_HOST by lazy { string("open_subsonic_proxy_host", "") }

    /** Like [YOUTUBE_PROXY_PORT], for [OPEN_SUBSONIC_PROXY_ENABLED]. */
    val OPEN_SUBSONIC_PROXY_PORT by lazy { int("open_subsonic_proxy_port", 0) }

    /**
     * Whether the YouTube account is in use. Off by default.
     *
     * While it is off every request to YouTube is sent signed out, whatever session is saved:
     * nothing of the user's library is fetched or synced. YouTube itself, like searching, still
     * works. Turning it off leaves what was synced as it is.
     */
    val YOUTUBE_ACCOUNT_ENABLED by lazy { boolean("youtube_account_enabled", false) }

    /**
     * Whether the songs the signed in user liked on YouTube are liked in the profile's library,
     * and the ones they no longer like aren't. On by default. While it is off they aren't
     * fetched, turning it off leaves the likes as they are.
     */
    val YOUTUBE_SYNC_SONGS by lazy { boolean("youtube_sync_songs", true) }

    /**
     * Whether the albums saved in the signed in user's YouTube library show in the profile's
     * library. On by default. They are fetched each time they show, never stored, and not at all
     * while it is off.
     */
    val YOUTUBE_SYNC_ALBUMS by lazy { boolean("youtube_sync_albums", true) }

    /** Like [YOUTUBE_SYNC_ALBUMS], for the artists the user subscribed to. */
    val YOUTUBE_SYNC_ARTISTS by lazy { boolean("youtube_sync_artists", true) }

    /** Like [YOUTUBE_SYNC_ALBUMS], for the playlists in the user's library. */
    val YOUTUBE_SYNC_PLAYLISTS by lazy { boolean("youtube_sync_playlists", true) }

    /**
     * How good the YouTube songs streamed on Wi-Fi and other networks that aren't metered are,
     * while nobody is signed in or [YOUTUBE_ACCOUNT_ENABLED] is off. [StreamQuality.HIGH] by
     * default. [StreamQuality.ORIGINAL] isn't offered, it plays as [StreamQuality.HIGH].
     *
     * It applies to the songs read next, what the cache has plays as it was cached.
     */
    val YOUTUBE_QUALITY_WIFI by lazy { enum("youtube_quality_wifi", StreamQuality.HIGH) }

    /**
     * Like [YOUTUBE_QUALITY_WIFI], on mobile data and other metered networks.
     * [StreamQuality.MEDIUM] by default.
     */
    val YOUTUBE_QUALITY_MOBILE by lazy { enum("youtube_quality_mobile", StreamQuality.MEDIUM) }

    /**
     * Like [YOUTUBE_QUALITY_WIFI], while someone is signed in. [StreamQuality.HIGH] by default.
     * [StreamQuality.ORIGINAL] asks in the session, which only Premium gains from: an account
     * without it gets [StreamQuality.HIGH], maybe after a refused try.
     */
    val YOUTUBE_QUALITY_WIFI_PREMIUM by lazy {
        enum("youtube_quality_wifi_premium", StreamQuality.HIGH)
    }

    /**
     * Like [YOUTUBE_QUALITY_WIFI_PREMIUM], on mobile data and other metered networks.
     * [StreamQuality.MEDIUM] by default.
     */
    val YOUTUBE_QUALITY_MOBILE_PREMIUM by lazy {
        enum("youtube_quality_mobile_premium", StreamQuality.MEDIUM)
    }

    /**
     * Whether every request to YouTube, streams and pictures too, goes through the proxy set by
     * [YOUTUBE_PROXY_TYPE], [YOUTUBE_PROXY_HOST] and [YOUTUBE_PROXY_PORT], signed in with
     * `Secrets.YOUTUBE_PROXY_USERNAME` when it is set, in place of [NETWORK_PROXY_ENABLED]'s. Off
     * by default, YouTube then goes through the app's proxy. While it is on and the proxy isn't
     * complete, nothing is sent to YouTube. A change applies to the next request.
     */
    val YOUTUBE_PROXY_ENABLED by lazy { boolean("youtube_proxy_enabled", false) }

    /** The kind of proxy of [YOUTUBE_PROXY_ENABLED], [ProxyType.HTTP] by default. */
    val YOUTUBE_PROXY_TYPE by lazy { enum("youtube_proxy_type", ProxyType.HTTP) }

    /** Host name or IP address of the proxy of [YOUTUBE_PROXY_ENABLED], empty until set. */
    val YOUTUBE_PROXY_HOST by lazy { string("youtube_proxy_host", "") }

    /** Port of the proxy of [YOUTUBE_PROXY_ENABLED], `0` until set. */
    val YOUTUBE_PROXY_PORT by lazy { int("youtube_proxy_port", 0) }
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="Playback">
    /**
     * Largest size, in bytes, the cache of streamed songs may grow to. The default is 1 GiB.
     * Downloads don't count, they have no limit.
     *
     * The size is fixed when the player starts, so a new value applies from the next launch.
     * A smaller value drops the songs read least recently once the next song is cached.
     */
    val MEDIA3_CACHE_SIZE by lazy { long("song_cache", 1024L * 1024 * 1024) }

    /**
     * Largest size, in bytes, the downloaded songs may take together. The default is 4 GiB.
     * It is separate from [MEDIA3_CACHE_SIZE], downloads are never dropped to make room.
     *
     * Nothing downloads songs yet, so nothing reads it yet.
     */
    val MEDIA3_DOWNLOAD_SIZE by lazy { long("song_downloads", 4L * 1024 * 1024 * 1024) }

    /**
     * Least time, in seconds, a song has to play before it is added to the playback history.
     * Only the time actually playing counts, pauses and loading don't.
     */
    val PLAYBACK_HISTORY_MIN_SECONDS by lazy { int("playback_history_min_seconds", 1) }

    /** Stops adding plays to the playback history while on. Plays already there are kept. */
    val PLAYBACK_HISTORY_PAUSED by lazy { boolean("playback_history_paused", false) }

    /**
     * Seconds of the next song the player loads while the one before it plays, `0` to load
     * nothing ahead. Loading it finds its stream, so its format and gain are known before it
     * starts; the rest loads once the player gets to it. `PlaybackService` applies it to the
     * player.
     */
    val PLAYBACK_PRELOAD_SECONDS by lazy { int("playback_preload_seconds", 5) }

    /**
     * Evens out the loudness of songs with their measured gain, on by default. Off, every song
     * plays as it is. `LoudnessNormalizer` follows it while playing.
     */
    val LOUDNESS_NORMALIZATION by lazy { boolean("loudness_normalization", true) }

    /**
     * Loudness songs are evened out to, in LUFS, from -18 to -10, -14 (YouTube's) by default.
     * Louder means more songs get a boost, which stops at 6 dB. `LoudnessNormalizer` follows it
     * while playing.
     */
    val LOUDNESS_TARGET_LUFS by lazy { int("loudness_target_lufs", -14) }

    /**
     * What the player does when a song fails to play, or loads longer than
     * [PLAYBACK_STALL_SECONDS]. [PlaybackFailureAction.RETRY_THEN_SKIP] by default. `PlaybackRecovery` reads
     * it at each failure.
     */
    val PLAYBACK_FAILURE_ACTION by lazy {
        enum("playback_failure_action", PlaybackFailureAction.RETRY_THEN_SKIP)
    }

    /**
     * Seconds a song may load without getting further while it should be playing before it counts
     * as failed, `0` to wait as long as the network does. A slow load that keeps adding to what is
     * loaded never fails. `PlaybackRecovery` reads it each time loading starts.
     */
    val PLAYBACK_STALL_SECONDS by lazy { int("playback_stall_seconds", 15) }

    /**
     * Seconds of the song playing the player loads ahead of where it is, from 1 to 60, `0` for
     * the whole song. Loading stops once that much is in, and goes on as it plays out.
     * `SongLoadControl` reads it each time the player decides whether to keep loading.
     */
    val PLAYBACK_BUFFER_AHEAD_SECONDS by lazy { int("playback_buffer_ahead_seconds", 50) }

    /**
     * Seconds that must be loaded before a song starts, or goes on after a seek, from 1 to 60,
     * at most [PLAYBACK_BUFFER_AHEAD_SECONDS]. `SongLoadControl` reads it each time the player is
     * waiting to play.
     */
    val PLAYBACK_BUFFER_TO_START_SECONDS by lazy { int("playback_buffer_to_start_seconds", 1) }

    /**
     * Keeps up to a minute of what was just played loaded, so seeking back doesn't load it again.
     * `PlaybackModule` reads it when the player is made, a change applies to the next player.
     */
    val PLAYBACK_KEEP_PLAYED by lazy { boolean("playback_keep_played", false) }

    /**
     * Keeps the queue in the database, with where the song playing was, and puts it back in the
     * player when the player is made again. Turning it off stops saving, the saved queue stays.
     */
    val PERSISTENT_QUEUE_ENABLED by lazy { boolean("persistent_queue", true) }

    /**
     * Seconds between two saves of where the song playing is, while it plays. Changes to the
     * queue and moving to another song are saved at once.
     */
    val PERSISTENT_QUEUE_SAVE_INTERVAL by lazy { int("persistent_queue_interval", 10) }

    /**
     * What the remove button of the sleep timer, in the full player and its notification, takes
     * off the time left. [SleepTimerStep.FIVE_MINUTES] by default.
     */
    val SLEEP_TIMER_REMOVE_STEP by lazy {
        enum("sleep_timer_remove_step", SleepTimerStep.FIVE_MINUTES)
    }

    /**
     * What the add button of the sleep timer, in the full player and its notification, adds to
     * the time left. [SleepTimerStep.FIVE_MINUTES] by default.
     */
    val SLEEP_TIMER_ADD_STEP by lazy { enum("sleep_timer_add_step", SleepTimerStep.FIVE_MINUTES) }
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="Android Auto">
    /**
     * What tapping an album in Android Auto's Albums tab does, read by the library the playback
     * service shows there. [AutoTapAction.OPEN] by default, like the app.
     */
    val AUTO_ALBUM_TAP by lazy { enum("auto_album_tap", AutoTapAction.OPEN) }

    /** Like [AUTO_ALBUM_TAP], for an artist in the Artists tab. */
    val AUTO_ARTIST_TAP by lazy { enum("auto_artist_tap", AutoTapAction.OPEN) }

    /** Like [AUTO_ALBUM_TAP], for a playlist in the Playlists tab. */
    val AUTO_PLAYLIST_TAP by lazy { enum("auto_playlist_tap", AutoTapAction.OPEN) }
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="Lyrics">
    /**
     * The lyrics provider whose lyrics show, its stored ones when there are, until the user picks
     * another on the lyrics page. [LyricsProvider.LRCLIB] by default.
     */
    val LYRICS_SOURCE by lazy { enum("lyrics_source", LyricsProvider.LRCLIB) }

    /**
     * How finely the user wants lyrics timed, [LyricsSync.WORD] by default. No lyrics are shown
     * more finely timed than this, and a search of every source stops at the first lyrics timed
     * this way.
     */
    val LYRICS_SYNC by lazy { enum("lyrics_sync", LyricsSync.WORD) }

    /**
     * Lyrics are fetched from the source every time they show, never read from or saved to the
     * library. Off by default. Lyrics already saved stay.
     */
    val LYRICS_ALWAYS_LIVE by lazy { boolean("lyrics_always_live", false) }

    /** Size of the lyrics' text in percent of its normal size, `100` by default. */
    val LYRICS_TEXT_SIZE by lazy { int("lyrics_text_size", 100) }
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="Coil3">
    /**
     * Largest size, in bytes, the image disk cache may grow to. The default is 128 MiB.
     *
     * The size is fixed when the image loader is built, so a new value applies from the next
     * launch. A smaller value does not shrink the files already stored, the cache trims
     * itself as new images are written.
     */
    val IMAGE_DISK_CACHE by lazy { long("image_disk_cache", 128L * 1024 * 1024) }

    /**
     * Share of the app's memory class that decoded images in the memory cache may use,
     * from `0.0` to `1.0`. The default is `0.15` (15%).
     *
     * Applies from the next launch, like [IMAGE_DISK_CACHE].
     */
    val IMAGE_MEMORY_CACHE_PERCENT by lazy { double("image_memory_cache_percent", .15) }
    //</editor-fold>

    /**
     * Suspends until the active profile's settings are loaded for the first time.
     */
    suspend fun awaitLoaded() = engine.awaitLoaded()

    /**
     * Suspends until the settings of [profileId] are loaded, like after switching to it.
     */
    suspend fun awaitProfile(profileId: String) = engine.awaitProfile(profileId)

    /**
     * Saves pending changes now, call it when the app is about to close.
     */
    suspend fun flush() = engine.flush()

    private fun int(name: String, default: Int) =
        Preference(name, default, codec<Int, Int>({ it }, { it }), engine)

    private fun string(name: String, default: String) =
        Preference(name, default, codec<String, String>({ it }, { it }), engine)

    private fun boolean(name: String, default: Boolean) =
        Preference(name, default, codec<Boolean, Boolean>({ it }, { it }), engine)

    private fun long(name: String, default: Long) =
        Preference(name, default, codec<Long, Long>({ it }, { it }), engine)

    private fun double(name: String, default: Double) =
        Preference(name, default, codec<Double, Double>({ it }, { it }), engine)

    private fun stringSet(name: String, default: Set<String>) =
        Preference(
            name,
            default,
            codec<Set<String>, Set<*>>({ it }, { it.filterIsInstance<String>().toSet() }),
            engine,
        )

    private inline fun <reified E : Enum<E>> enum(name: String, default: E) =
        Preference(name, default, enumCodec<E>(), engine)
}
