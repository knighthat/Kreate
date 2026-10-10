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

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.knighthat.kreate.preferences.Preferences
import me.knighthat.kreate.preferences.collectAsStateWithLifecycle
import me.knighthat.kreate.ui.theme.KreateTheme
import org.koin.android.ext.android.inject

class KreateActivity : ComponentActivity() {

    private val scope: CoroutineScope by inject()

    private var preferencesLoaded = false

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
        }

        enableEdgeToEdge()
        setContent {
            val themeMode by Preferences.THEME_MODE.collectAsStateWithLifecycle()
            val contrastLevel by Preferences.CONTRAST_LEVEL.collectAsStateWithLifecycle()

            KreateTheme(
                themeMode = themeMode,
                contrastLevel = contrastLevel,
                colorSeed = Color.Unspecified,
            ) {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Greeting(
                        name = "Android",
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // The process may be killed after this point, and the activity scope is already
        // cancelled by onDestroy, so save on the global scope.
        scope.launch { Preferences.flush() }
    }
}
