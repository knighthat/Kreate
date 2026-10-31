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

package me.knighthat.kreate3

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.StringRes
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import me.knighthat.kreate3.constant.ContrastLevel
import me.knighthat.kreate3.constant.ThemeMode
import me.knighthat.kreate3.database.ActiveUser
import me.knighthat.kreate3.database.repository.UserRepository
import me.knighthat.kreate3.notification.Notifier
import me.knighthat.kreate3.playback.OpenedFiles
import me.knighthat.kreate3.playback.PlaybackController
import me.knighthat.kreate3.playback.PlaybackService
import me.knighthat.kreate3.preferences.Preferences
import me.knighthat.kreate3.provider.Providers
import me.knighthat.kreate3.provider.youtube.YouTubeLink
import me.knighthat.kreate3.provider.youtube.YouTubeLinks
import me.knighthat.kreate3.provider.youtube.YouTubeProvider
import me.knighthat.kreate3.source.local.LocalMusicScheduler
import me.knighthat.kreate3.ui.component.toLiveMediaItem
import me.knighthat.kreate3.ui.navigation.Route
import me.knighthat.kreate3.ui.navigation.YouTubeAlbum
import me.knighthat.kreate3.ui.navigation.YouTubeArtist
import me.knighthat.kreate3.ui.navigation.YouTubePlaylist
import me.knighthat.kreate3.ui.screen.KreateApp
import me.knighthat.kreate3.ui.screen.SetupWizardScreen
import me.knighthat.kreate3.ui.screen.setupCompletedFile
import me.knighthat.kreate3.utils.hasAudioPermission
import org.koin.android.ext.android.get
import org.koin.android.ext.android.inject

class KreateActivity : ComponentActivity() {

    companion object {
        /** Opens the full player, the media notification's tap sends it. */
        const val OPEN_PLAYER_ACTION = "me.knighthat.kreate3.OPEN_PLAYER_ACTION"

        private const val TAG = "KreateActivity"

        /** Addresses of an audio file other apps open with the app, see [OpenedFiles]. */
        private val FILE_SCHEMES =
            setOf(ContentResolver.SCHEME_CONTENT, ContentResolver.SCHEME_FILE)

        /** Addresses of a web link, see [YouTubeLinks]. */
        private val LINK_SCHEMES = setOf("http", "https")

        const val SETUP_WIZARD_DONE_ACTION = "me.knighthat.kreate3.SETUP_WIZARD_DONE_ACTION"

        // Add this to launch option '-a me.knighthat.kreate3.SETUP_WIZARD_SKIP_ACTION'
        const val SETUP_WIZARD_SKIP_ACTION = "me.knighthat.kreate3.SETUP_WIZARD_SKIP_ACTION"

        //<editor-fold defaultstate="collapsed" desc="Setup Wizard extra fields">
        const val SETUP_WIZARD_TOS_CHECKSUM = "me.knighthat.kreate3.SETUP_WIZARD_TOS_CHECKSUM"
        const val SETUP_WIZARD_PROFILE_NAME = "me.knighthat.kreate3.SETUP_WIZARD_PROFILE_NAME"
        const val SETUP_WIZARD_THEME_MODE = "me.knighthat.kreate3.SETUP_WIZARD_THEME_MODE"
        const val SETUP_WIZARD_CONTRAST_LEVEL = "me.knighthat.kreate3.SETUP_WIZARD_CONTRAST_LEVEL"
        //</editor-fold>
    }

    private var isSetup = false
    private var isReady = false

    private val scope: CoroutineScope by inject()

    // Pending until the player is open: a request taken while stopped can be cut off when
    // onStart sets the content again, so it stays to be taken by the new content
    private val openPlayer = MutableStateFlow(false)

    // A screen a link asks for, kept like openPlayer until the content opened it
    private val openRoute = MutableStateFlow<Route?>(null)

    // In the language picked in the app, see AppLanguage
    override fun attachBaseContext(newBase: Context) =
        super.attachBaseContext(AppLanguage.wrap(newBase))

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must be called before super.onCreate, it swaps the splash theme for the app theme
        installSplashScreen().setKeepOnScreenCondition { !isReady }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Requires context, must run after `super.onCreate(savedInstanceState)`
        isSetup = setupCompletedFile().exists()
        // Don't run subsequent tasks if not yet setup
        if (!isSetup) {
            //<editor-fold defaultstate="collapsed" desc="Skip wizard">
            if (savedInstanceState == null && intent.action == SETUP_WIZARD_SKIP_ACTION) {
                setupCompletedFile().createNewFile()
                (applicationContext as KreateApplication).restart()
            }
            //</editor-fold>

            isReady = true
            return
        }

        // A recreated activity (rotation) keeps the player where it was instead
        if (savedInstanceState == null && intent.action == OPEN_PLAYER_ACTION)
            openPlayer.value = true
        // Played once, not again when the activity is recreated
        if (savedInstanceState == null) {
            playVoiceRequest(intent)
            playOpenedFile(intent)
            openYouTubeLink(intent)
        }

        // Made before a screen asks for them, off the main thread: their HTTP clients take long
        // to set up. A screen asking meanwhile waits for these instead of making its own.
        lifecycleScope.launch(Dispatchers.Default) { get<Providers>() }

        lifecycleScope.launch {
            Preferences.awaitLoaded()
            //<editor-fold defaultstate="collapsed" desc="Load values from wizard if available">
            if (savedInstanceState == null && intent.action == SETUP_WIZARD_DONE_ACTION) {
                val tosChecksum = intent.getStringExtra(SETUP_WIZARD_TOS_CHECKSUM)
                val profileName = intent.getStringExtra(SETUP_WIZARD_PROFILE_NAME)
                val themeMode = ThemeMode.entries.firstOrNull {
                    it.name == intent.getStringExtra(SETUP_WIZARD_THEME_MODE)
                }
                val contrastLevel = ContrastLevel.entries.firstOrNull {
                    it.name == intent.getStringExtra(SETUP_WIZARD_CONTRAST_LEVEL)
                }

                // Each one alone, a restored backup brings its own profile and settings
                if (!tosChecksum.isNullOrBlank())
                    Preferences.TOS_CHECKSUM.update(tosChecksum)
                if (!profileName.isNullOrBlank()) {
                    val activeUserId = get<ActiveUser>().id.first { it != null }!!
                    get<UserRepository>().rename(activeUserId, profileName)
                }
                themeMode?.let { Preferences.THEME_MODE.update(it) }
                contrastLevel?.let { Preferences.CONTRAST_LEVEL.update(it) }
            }
            //</editor-fold>
            isReady = true

            //<editor-fold defaultstate="collapsed" desc="Scan local media">
            // Without the permission there is nothing to scan, so nothing is asked or scheduled
            if (!hasAudioPermission()) return@launch
            // A recreated activity (rotation) must not scan again, only the schedule needs to follow it
            if (savedInstanceState == null) LocalMusicScheduler.scanNow(this@KreateActivity)
            // Keep the scan schedule in step with the interval
            Preferences.LOCAL_SCAN_INTERVAL.state.collect {
                LocalMusicScheduler.schedule(this@KreateActivity, it)
            }
            //</editor-fold>
        }
    }

    override fun onStart() {
        super.onStart()

        setContent {
            if (isSetup) {
                val playerRequested by openPlayer.collectAsState()
                val routeRequested by openRoute.collectAsState()
                KreateApp(
                    openPlayer = playerRequested,
                    onPlayerOpened = { openPlayer.value = false },
                    openRoute = routeRequested,
                    onRouteOpened = { openRoute.value = null },
                )
            } else
                SetupWizardScreen()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == OPEN_PLAYER_ACTION) openPlayer.value = true
        if (isSetup) {
            playVoiceRequest(intent)
            playOpenedFile(intent)
            openYouTubeLink(intent)
        }
    }

    /**
     * Hands a voice request of the Assistant ("play … on Kreate") to [PlaybackService], which
     * finds what to play.
     */
    private fun playVoiceRequest(intent: Intent) {
        if (intent.action != MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH) return
        startService(
            Intent(this, PlaybackService::class.java)
                .setAction(PlaybackService.VOICE_SEARCH_ACTION)
                .putExtras(intent.extras ?: Bundle.EMPTY)
        )
    }

    /**
     * Plays an audio file another app opened with this one ("Open with"), see [OpenedFiles], and
     * opens the player on it. It is added to the queue like a song tapped in the app.
     */
    private fun playOpenedFile(intent: Intent) {
        if (intent.action != Intent.ACTION_VIEW) return
        val uri = intent.data?.takeIf { it.scheme in FILE_SCHEMES } ?: return
        lifecycleScope.launch {
            val item = get<OpenedFiles>().item(uri)
            if (item == null) {
                postMessage(R.string.error_open_file)
                return@launch
            }
            get<PlaybackController>().play(item)
            openPlayer.value = true
        }
    }

    /**
     * Opens a YouTube Music link another app opened or shared with this one, see
     * [YouTubeLinks.parse]: a song plays at once, like a song tapped in the app, an album, an
     * artist or a playlist opens its screen. Shared text without such a link says so.
     */
    private fun openYouTubeLink(intent: Intent) {
        val link = when (intent.action) {
            Intent.ACTION_VIEW -> intent.data?.takeIf { it.scheme in LINK_SCHEMES } ?: return
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
            else -> return
        }.toString().let(YouTubeLinks::find)

        when (link) {
            null -> postMessage(R.string.error_open_link)
            is YouTubeLink.Album -> openRoute.value = YouTubeAlbum(link.albumId)
            is YouTubeLink.Artist -> openRoute.value = YouTubeArtist(link.artistId)
            is YouTubeLink.Playlist -> openRoute.value = YouTubePlaylist(link.playlistId)
            is YouTubeLink.Song -> lifecycleScope.launch {
                val song = try {
                    get<YouTubeProvider>().songDetails(link.songId)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Couldn't find the song of $link", e)
                    null
                }
                if (song == null) {
                    postMessage(R.string.error_open_link)
                    return@launch
                }
                get<PlaybackController>().play(song.toLiveMediaItem())
                openPlayer.value = true
            }
        }
    }

    /** Shows [text] once a screen shows messages, for what an intent asked at startup. */
    private fun postMessage(@StringRes text: Int) = lifecycleScope.launch {
        val notifier = get<Notifier>()
        notifier.awaitScreen()
        notifier.post(getString(text))
    }

    override fun onStop() {
        super.onStop()
        // The process may be killed after this point, and the activity scope is already
        // cancelled by onDestroy, so save on the global scope.
        scope.launch { Preferences.flush() }
    }
}
