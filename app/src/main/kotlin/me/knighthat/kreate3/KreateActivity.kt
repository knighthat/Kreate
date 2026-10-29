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

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import me.knighthat.kreate3.constant.ContrastLevel
import me.knighthat.kreate3.constant.ThemeMode
import me.knighthat.kreate3.database.ActiveUser
import me.knighthat.kreate3.database.repository.UserRepository
import me.knighthat.kreate3.preferences.Preferences
import me.knighthat.kreate3.provider.Providers
import me.knighthat.kreate3.source.local.LocalMusicScheduler
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
                KreateApp(playerRequested, onPlayerOpened = { openPlayer.value = false })
            } else
                SetupWizardScreen()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == OPEN_PLAYER_ACTION) openPlayer.value = true
    }

    override fun onStop() {
        super.onStop()
        // The process may be killed after this point, and the activity scope is already
        // cancelled by onDestroy, so save on the global scope.
        scope.launch { Preferences.flush() }
    }
}
