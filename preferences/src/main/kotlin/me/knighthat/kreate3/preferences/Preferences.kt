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

import me.knighthat.kreate3.constant.ContrastLevel
import me.knighthat.kreate3.constant.OpenSubsonicAuth
import me.knighthat.kreate3.constant.ThemeMode
import me.knighthat.kreate3.preferences.Preferences.IMAGE_DISK_CACHE
import me.knighthat.kreate3.preferences.Preferences.NETWORK_CONNECT_TIMEOUT
import me.knighthat.kreate3.preferences.Preferences.OPEN_SUBSONIC_CERTIFICATE_SHA256
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

    val OPEN_SUBSONIC_USERNAME by lazy { string("open_subsonic_username", "") }

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

    /** Like [YOUTUBE_SYNC_SONGS], for the songs starred on the OpenSubsonic server. */
    val OPEN_SUBSONIC_SYNC_SONGS by lazy { boolean("open_subsonic_sync_songs", true) }

    /** Like [YOUTUBE_SYNC_ALBUMS], for the albums on the OpenSubsonic server. */
    val OPEN_SUBSONIC_SYNC_ALBUMS by lazy { boolean("open_subsonic_sync_albums", true) }

    /** Like [YOUTUBE_SYNC_ALBUMS], for the artists on the OpenSubsonic server. */
    val OPEN_SUBSONIC_SYNC_ARTISTS by lazy { boolean("open_subsonic_sync_artists", true) }

    /** Like [YOUTUBE_SYNC_ALBUMS], for the playlists on the OpenSubsonic server. */
    val OPEN_SUBSONIC_SYNC_PLAYLISTS by lazy { boolean("open_subsonic_sync_playlists", true) }

    /** Host the remembered certificate belongs to, empty when none is remembered. */
    val OPEN_SUBSONIC_CERTIFICATE_HOST by lazy { string("open_subsonic_certificate_host", "") }

    /** SHA-256 fingerprint of the remembered self-signed certificate, empty when none is. */
    val OPEN_SUBSONIC_CERTIFICATE_SHA256 by lazy { string("open_subsonic_certificate_sha256", "") }

    /**
     * Page id of the YouTube channel the signed in user acts as, when their Google account has
     * several (brand accounts). Empty for the account's own channel. It only picks a channel, the
     * session itself is in `Secrets.YOUTUBE_COOKIES`.
     */
    val YOUTUBE_PAGE_ID by lazy { string("youtube_page_id", "") }

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

    private inline fun <reified E : Enum<E>> enum(name: String, default: E) =
        Preference(name, default, enumCodec<E>(), engine)
}
