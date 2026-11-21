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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withTimeoutOrNull
import me.knighthat.kreate3.notification.MessageDuration
import me.knighthat.kreate3.notification.Notifier

/**
 * The app's [Notifier], for screens to tell the user something. Unless provided, like in
 * previews, it is one that nobody shows.
 */
val LocalNotifier = staticCompositionLocalOf { Notifier() }

/** Where the [Notifier]'s messages show, see [MessageHost]. */
private val SNACKBAR_MARGIN = 12.dp

val LocalMessageHost = staticCompositionLocalOf { SnackbarHostState() }

/** How long a [MessageDuration.SHORT] message stays, shorter than Material's 4 s. */
private const val SHORT_MILLIS = 3_000L

/** How long a [MessageDuration.LONG] message stays, shorter than Material's 10 s. */
private const val LONG_MILLIS = 5_000L

/**
 * Shows the messages of [LocalMessageHost]. The app's scaffold has it above its bottom bar; a
 * screen with a bottom bar of its own puts it in its scaffold instead, or messages cover that bar.
 */
@Composable
fun MessageHost(modifier: Modifier = Modifier) =
    SnackbarHost(LocalMessageHost.current, modifier) { MessageSnackbar(it) }

/** A message in the theme's surface colors with an outline, instead of Material's inverse ones. */
@Composable
private fun MessageSnackbar(data: SnackbarData) {
    val shape = MaterialTheme.shapes.medium
    val borderColor = MaterialTheme.colorScheme.outlineVariant
    Snackbar(
        snackbarData = data,
        // Snackbar keeps 12dp of margin inside its modifier's bounds, so the border is drawn
        // inset by that margin to sit on the snackbar's own edge
        modifier = Modifier.drawWithContent {
            drawContent()
            val inset = SNACKBAR_MARGIN.toPx()
            val stroke = 1.dp.toPx()
            val inner = Size(
                size.width - 2 * inset - stroke,
                size.height - 2 * inset - stroke
            )
            translate(inset + stroke / 2, inset + stroke / 2) {
                drawOutline(
                    outline = shape.createOutline(inner, layoutDirection, this@drawWithContent),
                    color = borderColor,
                    style = Stroke(stroke)
                )
            }
        },
        shape = shape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        actionColor = MaterialTheme.colorScheme.primary,
        actionContentColor = MaterialTheme.colorScheme.primary,
        dismissActionContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * Shows each message of [notifier] in [host] while the app is on screen. A new message replaces
 * the one showing instead of waiting for it.
 */
@Composable
fun ShowMessages(notifier: Notifier, host: SnackbarHostState) {
    val state by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    // Not collected meanwhile, so the notifier sends messages to the system instead
    if (!state.isAtLeast(Lifecycle.State.STARTED)) return
    val accessibility = LocalAccessibilityManager.current

    LaunchedEffect(notifier, host, accessibility) {
        // A message cancelled by the next one is taken off the screen
        notifier.messages.collectLatest { message ->
            host.currentSnackbarData?.dismiss()
            val millis = when (message.duration) {
                // A button needs time to be reached
                MessageDuration.SHORT ->
                    if (message.actionLabel != null) LONG_MILLIS else SHORT_MILLIS

                MessageDuration.LONG -> LONG_MILLIS
                MessageDuration.INDEFINITE -> null
            }
            // Shown as indefinite and taken off when the time runs out, Material's own times
            // being fixed; stretched like Material's by the system's accessibility timeout
            val timeout = millis?.let {
                accessibility?.calculateRecommendedTimeoutMillis(
                    originalTimeoutMillis = it,
                    containsIcons = message.dismissable,
                    containsText = true,
                    containsControls = message.actionLabel != null,
                ) ?: it
            }
            val show = suspend {
                host.showSnackbar(
                    message = message.text,
                    actionLabel = message.actionLabel,
                    withDismissAction = message.dismissable,
                    duration = SnackbarDuration.Indefinite,
                )
            }
            val result =
                if (timeout == null)
                    show()
                else
                    withTimeoutOrNull(timeout) { show() } ?: SnackbarResult.Dismissed
            if (result == SnackbarResult.ActionPerformed) message.onAction()
        }
    }
}

//<editor-fold defaultstate="collapsed" desc="Previews">

private class PreviewSnackbarData(
    message: String,
    actionLabel: String?,
    withDismissAction: Boolean,
) : SnackbarData {

    override val visuals = object : SnackbarVisuals {
        override val message = message
        override val actionLabel = actionLabel
        override val withDismissAction = withDismissAction
        override val duration = SnackbarDuration.Indefinite
    }

    override fun performAction() {}

    override fun dismiss() {}
}

@ComponentPreviews
@Composable
private fun MessagesPreview() = PreviewTheme {
    Surface {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            MessageSnackbar(PreviewSnackbarData("Song added to the queue", null, false))
            MessageSnackbar(PreviewSnackbarData("Removed from the playlist", "Undo", true))
            // PlainTooltip only composes inside a TooltipBox, so its look is copied
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface,
                shadowElevation = 4.dp,
            ) {
                Text(
                    "Add to playlist",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

//</editor-fold>
