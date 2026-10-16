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

package me.knighthat.kreate3.ui.navigation

import android.app.Activity
import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import me.knighthat.kreate3.preferences.Preferences
import me.knighthat.kreate3.preferences.collectAsStateWithLifecycle
import kotlin.time.Duration.Companion.seconds

/** Value of the system's navigation mode when the user goes back with a swipe. */
private const val NAVIGATION_MODE_GESTURAL = 2

/**
 * Whether the system is set to gesture navigation, `false` when it can't be told. Only used when
 * the back event itself doesn't say, before predictive back.
 */
private fun Context.usesGestureNavigation(): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && try {
        Settings.Secure.getInt(contentResolver, "navigation_mode", 0) == NAVIGATION_MODE_GESTURAL
    } catch (_: Exception) {
        false
    }

/**
 * Leaves the app the way going back from the first screen does without this handler: from
 * Android 12 the task only moves to the back, before that the activity finishes.
 */
private fun Activity.exit() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || !moveTaskToBack(false)) finish()
}

/**
 * Handles going back from the first screen, where it would close the app, when [atBottom].
 *
 * With [Preferences.BACK_EXIT_DISABLED] going back there does nothing. Otherwise, with
 * [Preferences.EXIT_CONFIRMATION], the first time shows a message on [snackbar] for
 * [Preferences.EXIT_CONFIRMATION_TIMEOUT] seconds and going back again meanwhile leaves the app.
 * Once the message is gone, the next time asks again.
 *
 * Compose it before the screens, so a screen that handles going back itself comes first.
 */
@Composable
fun ExitConfirmation(atBottom: Boolean, snackbar: SnackbarHostState) {
    val exitDisabled by Preferences.BACK_EXIT_DISABLED.collectAsStateWithLifecycle()
    val confirm by Preferences.EXIT_CONFIRMATION.collectAsStateWithLifecycle()
    val timeout by Preferences.EXIT_CONFIRMATION_TIMEOUT.collectAsStateWithLifecycle()

    val activity = LocalActivity.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var asking by remember { mutableStateOf<Job?>(null) }

    PredictiveBackHandler(enabled = atBottom && (exitDisabled || confirm)) { progress ->
        // A swipe reports its progress, a button press goes back at once
        var swiped = false
        progress.collect { swiped = true }

        when {
            exitDisabled -> Unit
            asking?.isActive == true -> {
                asking?.cancel()
                activity?.exit()
            }

            else -> asking = scope.launch {
                val action = if (swiped || context.usesGestureNavigation()) "Swipe" else "Press"
                snackbar.currentSnackbarData?.dismiss()
                withTimeoutOrNull(timeout.coerceAtLeast(1).seconds) {
                    snackbar.showSnackbar(
                        "$action again to exit",
                        duration = SnackbarDuration.Indefinite
                    )
                }
            }
        }
    }
}
