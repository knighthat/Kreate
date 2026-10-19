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

package me.knighthat.kreate3.ui.component

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.flow.collectLatest
import me.knighthat.kreate3.notification.MessageDuration
import me.knighthat.kreate3.notification.Notifier

/**
 * The app's [Notifier], for screens to tell the user something. Unless provided, like in
 * previews, it is one that nobody shows.
 */
val LocalNotifier = staticCompositionLocalOf { Notifier() }

/** Where the [Notifier]'s messages show, see [MessageHost]. */
val LocalMessageHost = staticCompositionLocalOf { SnackbarHostState() }

/**
 * Shows the messages of [LocalMessageHost]. The app's scaffold has it above its bottom bar; a
 * screen with a bottom bar of its own puts it in its scaffold instead, or messages cover that bar.
 */
@Composable
fun MessageHost(modifier: Modifier = Modifier) = SnackbarHost(LocalMessageHost.current, modifier)

/**
 * Shows each message of [notifier] in [host] while the app is on screen. A new message replaces
 * the one showing instead of waiting for it.
 */
@Composable
fun ShowMessages(notifier: Notifier, host: SnackbarHostState) {
    val state by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    // Not collected meanwhile, so the notifier sends messages to the system instead
    if (!state.isAtLeast(Lifecycle.State.STARTED)) return

    LaunchedEffect(notifier, host) {
        // A message cancelled by the next one is taken off the screen
        notifier.messages.collectLatest { message ->
            host.currentSnackbarData?.dismiss()
            val result = host.showSnackbar(
                message = message.text,
                actionLabel = message.actionLabel,
                withDismissAction = message.dismissable,
                duration = when (message.duration) {
                    MessageDuration.SHORT -> SnackbarDuration.Short
                    MessageDuration.LONG -> SnackbarDuration.Long
                    MessageDuration.INDEFINITE -> SnackbarDuration.Indefinite
                },
            )
            if (result == SnackbarResult.ActionPerformed) message.onAction()
        }
    }
}
