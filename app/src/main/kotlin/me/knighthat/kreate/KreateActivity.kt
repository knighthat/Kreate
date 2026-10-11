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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import me.knighthat.kreate.media.AUDIO_PERMISSION
import me.knighthat.kreate.media.LocalMusicScheduler
import me.knighthat.kreate.media.hasAudioPermission
import me.knighthat.kreate.preferences.Preferences
import me.knighthat.kreate.preferences.collectAsStateWithLifecycle
import me.knighthat.kreate.ui.theme.KreateTheme
import org.koin.android.ext.android.inject

class KreateActivity : ComponentActivity() {

    private val scope: CoroutineScope by inject()

    private var preferencesLoaded = false

    private val audioAllowed = MutableStateFlow(false)

    @Composable
    private fun Greeting(name: String, modifier: Modifier = Modifier) {
        Text(
            text = "Hello $name!",
            modifier = modifier
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must be called before super.onCreate, it swaps the splash theme for the app theme
        installSplashScreen().setKeepOnScreenCondition { !preferencesLoaded }
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            Preferences.awaitLoaded()
            preferencesLoaded = true

            // Keep the scan schedule in step with the interval, but only once scanning is allowed
            audioAllowed.first { it }
            Preferences.LOCAL_SCAN_INTERVAL.state.collect {
                LocalMusicScheduler.schedule(this@KreateActivity, it)
            }
        }

        enableEdgeToEdge()

        // A recreated activity (rotation) must not scan again, only the schedule needs to follow it
        if (hasAudioPermission())
            onAudioAllowed(scanNow = savedInstanceState == null)
        else
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                if (granted) onAudioAllowed(scanNow = true)
            }.launch(AUDIO_PERMISSION)
    }

    override fun onStart() {
        super.onStart()

        setContent {
            val themeMode by Preferences.THEME_MODE.collectAsStateWithLifecycle()
            val contrastLevel by Preferences.CONTRAST_LEVEL.collectAsStateWithLifecycle()

            KreateTheme(
                themeMode = themeMode,
                contrastLevel = contrastLevel,
                colorSeed = Color.Unspecified,
            ) {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Column(modifier = Modifier.padding(innerPadding)) {
                        Greeting(name = "Android")
                        TextButton(onClick = {
                            startActivity(
                                Intent(
                                    this@KreateActivity,
                                    ProfilesActivity::class.java
                                )
                            )
                        }) {
                            Text("Profiles")
                        }
                    }
                }
            }
        }
    }

    /**
     * Lets the schedule start, and scans the device's music now when [scanNow] is set.
     */
    private fun onAudioAllowed(scanNow: Boolean) {
        if (scanNow) LocalMusicScheduler.scanNow(this)
        audioAllowed.value = true
    }

    override fun onStop() {
        super.onStop()
        // The process may be killed after this point, and the activity scope is already
        // cancelled by onDestroy, so save on the global scope.
        scope.launch { Preferences.flush() }
    }
}
