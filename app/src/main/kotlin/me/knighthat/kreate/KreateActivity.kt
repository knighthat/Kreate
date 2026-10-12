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

package me.knighthat.kreate

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import me.knighthat.kreate.constant.ContrastLevel
import me.knighthat.kreate.constant.ThemeMode
import me.knighthat.kreate.database.ActiveUser
import me.knighthat.kreate.database.repository.UserRepository
import me.knighthat.kreate.media.LocalMusicScheduler
import me.knighthat.kreate.preferences.Preferences
import me.knighthat.kreate.screen.SetupWizardScreen
import me.knighthat.kreate.screen.setupCompletedFile
import me.knighthat.kreate.ui.screen.KreateApp
import me.knighthat.kreate.utils.hasAudioPermission
import org.koin.android.ext.android.get
import org.koin.android.ext.android.inject

class KreateActivity : ComponentActivity() {

    companion object {
        const val SETUP_WIZARD_DONE_ACTION = "me.knighthat.kreate.SETUP_WIZARD_DONE_ACTION"

        // Add this to launch option '-a me.knighthat.kreate.SETUP_WIZARD_SKIP_ACTION'
        const val SETUP_WIZARD_SKIP_ACTION = "me.knighthat.kreate.SETUP_WIZARD_SKIP_ACTION"

        //<editor-fold defaultstate="collapsed" desc="Setup Wizard extra fields">
        const val SETUP_WIZARD_TOS_CHECKSUM = "me.knighthat.kreate.SETUP_WIZARD_TOS_CHECKSUM"
        const val SETUP_WIZARD_PROFILE_NAME = "me.knighthat.kreate.SETUP_WIZARD_PROFILE_NAME"
        const val SETUP_WIZARD_THEME_MODE = "me.knighthat.kreate.SETUP_WIZARD_THEME_MODE"
        const val SETUP_WIZARD_CONTRAST_LEVEL = "me.knighthat.kreate.SETUP_WIZARD_CONTRAST_LEVEL"
        //</editor-fold>
    }

    private var isSetup = false
    private var isReady = false

    private val scope: CoroutineScope by inject()

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
                startActivity(
                    Intent(this, KreateActivity::class.java)
                )
                // The system queued the new start already, a fresh process picks it up
                Runtime.getRuntime().exit(0)
            }
            //</editor-fold>

            isReady = true
            return
        }

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

                // Only apply if all args are provided
                if (!tosChecksum.isNullOrBlank() && !profileName.isNullOrBlank() && themeMode != null && contrastLevel != null) {
                    val activeUserId = get<ActiveUser>().id.first { it != null }!!
                    get<UserRepository>().rename(activeUserId, profileName)
                    Preferences.TOS_CHECKSUM.update(tosChecksum)
                    Preferences.THEME_MODE.update(themeMode)
                    Preferences.CONTRAST_LEVEL.update(contrastLevel)
                }
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
            if (isSetup)
                KreateApp()
            else
                SetupWizardScreen()
        }
    }

    override fun onStop() {
        super.onStop()
        // The process may be killed after this point, and the activity scope is already
        // cancelled by onDestroy, so save on the global scope.
        scope.launch { Preferences.flush() }
    }
}
