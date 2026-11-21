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

package me.knighthat.kreate3.ui.screen.recognition

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.knighthat.kreate3.R
import me.knighthat.kreate3.preferences.Preferences
import me.knighthat.kreate3.recognition.RecognizedSong
import me.knighthat.kreate3.ui.component.ComponentPreviews
import me.knighthat.kreate3.ui.component.ExplicitBadge
import me.knighthat.kreate3.ui.component.PreviewTheme
import me.knighthat.kreate3.ui.component.ThemedTooltip
import me.knighthat.kreate3.ui.component.Thumbnail
import me.knighthat.kreate3.ui.component.rememberOpenLink
import me.knighthat.kreate3.ui.component.thumbnailModel
import me.knighthat.kreate3.utils.hasMicrophonePermission
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * A stretch of the listening time and its color: red while too short, yellow while it may do,
 * green once long enough for a good match. [ListenRing] draws each in its own color.
 */
private class Zone(val from: Duration, val to: Duration, val color: Color)

private val ZONES = listOf(
    Zone(Duration.ZERO, MIN_LISTEN, Color(0xFFE53935)),
    Zone(MIN_LISTEN, GOOD_LISTEN, Color(0xFFFDD835)),
    Zone(GOOD_LISTEN, MAX_LISTEN, Color(0xFF43A047)),
)

/** How bright the part of a zone not reached yet is. */
private const val UNREACHED_ALPHA = 0.3f

/** A denial answered this fast came without a prompt, the system remembers an earlier one. */
private const val NO_PROMPT_MILLIS = 300L

/** Who names the songs, a name never translated. */
private const val RECOGNIZER_NAME = "Shazam"

/** Room between the Songs tab's icon and the bubble above it, clear of the finger. */
private val BUBBLE_GAP = 32.dp

/**
 * The dialog of song recognition, for [KreateApp][me.knighthat.kreate3.ui.screen.KreateApp] to
 * draw over everything. The Songs tab starts it with [rememberHoldToListen] and shows
 * [ListenOverlay] while held.
 *
 * @param onShowMatches opens the search results of a query, the dialog is closed first
 */
@Composable
fun SongRecognitionDialog(viewModel: SongRecognitionViewModel, onShowMatches: (String) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val openLink = rememberOpenLink()
    SongRecognitionDialog(
        state = state,
        onListen = rememberListen(viewModel),
        onStop = viewModel::stop,
        onPlay = viewModel::play,
        onShowMatches = {
            viewModel.close()
            onShowMatches(it.searchQuery)
        },
        // Stays open, to come back to from the browser
        onViewPage = openLink,
        onRetry = viewModel::retry,
        onClose = viewModel::close,
    )
}

/**
 * Starts listening, or asks for the microphone first. Once it's allowed, the next hold listens.
 * While song recognition is off in settings ([Preferences.SHAZAM_ENABLED]) it only says so.
 * A denial from the prompt says why it's needed; one without a prompt (denied for good before)
 * opens the app's settings, the only place left to allow it.
 */
@Composable
fun rememberListen(viewModel: SongRecognitionViewModel): () -> Unit {
    val context = LocalContext.current
    var askedAt by remember { mutableLongStateOf(0L) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (it) return@rememberLauncherForActivityResult
        val withoutPrompt = SystemClock.elapsedRealtime() - askedAt < NO_PROMPT_MILLIS
        if (withoutPrompt) {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.fromParts("package", context.packageName, null))
                .also(context::startActivity)
        } else {
            viewModel.microphoneDenied()
        }
    }
    return remember(viewModel, ask, context) {
        {
            if (!Preferences.SHAZAM_ENABLED.value) {
                viewModel.disabled()
            } else if (context.hasMicrophonePermission()) {
                viewModel.listen()
            } else {
                askedAt = SystemClock.elapsedRealtime()
                ask.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }
}

/**
 * Listens while the element is held: after a long press it buzzes and calls [onStart], letting go
 * calls [onStop]. A tap stays a tap; the hold eats the release, so it doesn't count as one.
 */
@Composable
fun rememberHoldToListen(onStart: () -> Unit, onStop: () -> Unit): Modifier {
    val haptics = LocalHapticFeedback.current
    val start by rememberUpdatedState(onStart)
    val stop by rememberUpdatedState(onStop)
    return remember(haptics) {
        Modifier.pointerInput(Unit) {
            awaitEachGesture {
                // Before the element's own click handling, which mustn't see the hold's release
                val down =
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                // Let go, moved or taken by a scroll before the long press: a tap or not ours
                val endedEarly = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                    var moved = 0f
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id }
                        if (change != null) moved += change.positionChange().getDistance()
                    } while (
                        change != null
                        && !change.changedToUpIgnoreConsumed()
                        && !change.isConsumed
                        && moved <= viewConfiguration.touchSlop
                    )
                    true
                }
                if (endedEarly != null) return@awaitEachGesture

                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                start()
                try {
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        event.changes.forEach { it.consume() }
                    } while (event.changes.any { it.pressed })
                } finally {
                    stop()
                }
            }
        }
    }
}

/**
 * A tooltip bubble above the Songs tab while it's held, with a small [ListenRing]. Placed in
 * the tab's icon, it takes the icon's size as its anchor.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListenOverlay(viewModel: SongRecognitionViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listening = (state as? RecognitionState.Listening)?.takeUnless { it.inDialog }
    val tooltip = rememberTooltipState(isPersistent = true)
    LaunchedEffect(listening != null) {
        if (listening != null) tooltip.show() else tooltip.dismiss()
    }
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
            TooltipAnchorPosition.Above,
            spacingBetweenTooltipAndAnchor = BUBBLE_GAP,
        ),
        tooltip = {
            ThemedTooltip {
                // Keeps the last value while the bubble fades out
                val since = listening?.since
                val heard = if (since == null) MAX_LISTEN else rememberHeard(since).value
                ListenBubbleContent(heard)
            }
        },
        state = tooltip,
        // Only the hold opens it
        enableUserInput = false,
    ) {
        Box(Modifier.size(24.dp))
    }
}

/** The ring with a small microphone in it, inside the bubble above the Songs tab. */
@Composable
private fun ListenBubbleContent(heard: Duration) =
    ListenRing(heard, 40.dp, 3.dp, Modifier.padding(vertical = 4.dp)) {
        Icon(
            painterResource(R.drawable.mic),
            contentDescription = null,
            modifier = Modifier.size(14.dp),
        )
    }

/** How long it has listened since [since], counted each frame up to [MAX_LISTEN]. */
@Composable
private fun rememberHeard(since: Long) = produceState(Duration.ZERO, since) {
    while (value < MAX_LISTEN)
        withFrameMillis {
            value = (SystemClock.elapsedRealtime() - since).milliseconds.coerceAtMost(MAX_LISTEN)
        }
}

/**
 * A ring that fills up clockwise from the top over [MAX_LISTEN], each [Zone] in its color (dim
 * until reached), with [content] in its middle.
 *
 * @param stroke the ring's width
 */
@Composable
private fun ListenRing(
    heard: Duration,
    size: Dp,
    stroke: Dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) = Box(modifier.size(size), contentAlignment = Alignment.Center) {
    Canvas(Modifier.fillMaxSize()) {
        val width = stroke.toPx()
        val radius = (this.size.minDimension - width) / 2
        val topLeft = Offset(center.x - radius, center.y - radius)
        val arcSize = Size(radius * 2, radius * 2)
        val heardDegrees = (heard / MAX_LISTEN).toFloat() * 360f
        ZONES.forEach { zone ->
            val start = (zone.from / MAX_LISTEN).toFloat() * 360f
            val end = (zone.to / MAX_LISTEN).toFloat() * 360f
            drawArc(
                zone.color.copy(alpha = UNREACHED_ALPHA),
                startAngle = start - 90f,
                sweepAngle = end - start,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width),
            )
            val reached = minOf(end, heardDegrees)
            if (reached > start)
                drawArc(
                    zone.color,
                    startAngle = start - 90f,
                    sweepAngle = reached - start,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width),
                )
        }
    }
    content()
}

/**
 * Song recognition's dialog, shown from the end of a hold until closed. It can't be closed while
 * looking up or listening.
 */
@Composable
private fun SongRecognitionDialog(
    state: RecognitionState,
    onListen: () -> Unit,
    onStop: () -> Unit,
    onPlay: (RecognizedSong) -> Unit,
    onShowMatches: (RecognizedSong) -> Unit,
    onViewPage: (String) -> Unit,
    onRetry: () -> Unit,
    onClose: () -> Unit,
) {
    // The Songs tab's hold shows its own ring, the dialog comes once it's let go
    if (state == RecognitionState.Idle) return
    if (state is RecognitionState.Listening && !state.inDialog) return

    val closable = state !is RecognitionState.Listening && state != RecognitionState.Identifying
    val resources = LocalResources.current
    AlertDialog(
        onDismissRequest = onClose,
        properties = DialogProperties(
            dismissOnBackPress = closable,
            dismissOnClickOutside = closable,
        ),
        title = { Text(resources.getString(R.string.dialog_title_recognize_song)) },
        text = {
            when (state) {
                RecognitionState.Identifying -> Identifying()
                is RecognitionState.Found -> Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    FoundSong(state.song)
                    FoundActions(
                        song = state.song,
                        searching = state.searching,
                        notFound = state.notFound,
                        onPlay = onPlay,
                        onShowMatches = onShowMatches,
                        onViewPage = onViewPage,
                    )
                }

                is RecognitionState.Failed -> Text(resources.getString(state.reason))
                // One call for both, so the button holding the finger stays when listening starts
                is RecognitionState.Listening, is RecognitionState.Ready -> HoldButton(
                    since = (state as? RecognitionState.Listening)?.since,
                    tooShort = (state as? RecognitionState.Ready)?.tooShort == true,
                    onListen = onListen,
                    onStop = onStop,
                )

                RecognitionState.Idle -> {}
            }
        },
        confirmButton = {
            when (state) {
                is RecognitionState.Found, is RecognitionState.Failed -> TextButton(onClick = onRetry) {
                    Text(resources.getString(R.string.action_retry))
                }

                else -> {}
            }
        },
        dismissButton = if (closable) ({
            TextButton(onClick = onClose) { Text(resources.getString(R.string.action_close)) }
        }) else null,
    )
}

@Composable
private fun Identifying() =
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
        Text(LocalResources.current.getString(R.string.dialog_body_recognize_song_identifying))
    }

@Composable
private fun FoundSong(song: RecognizedSong) =
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Thumbnail(
            models = listOfNotNull(thumbnailModel(song.cover, null)),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.size(64.dp),
            placeholder = {
                Icon(
                    painterResource(R.drawable.music_note),
                    contentDescription = null,
                    modifier = Modifier.align(Alignment.Center),
                )
            },
        )
        Column {
            if (song.explicit) ExplicitBadge()
            Text(
                song.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOfNotNull(song.artist, song.album).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }

/**
 * What to do with a song found: play it (the first song YouTube finds, a spinner while it looks,
 * a line under the button when it found none), search for it, or see its page on [RECOGNIZER_NAME]. */
@Composable
private fun FoundActions(
    song: RecognizedSong,
    searching: Boolean,
    notFound: Boolean,
    onPlay: (RecognizedSong) -> Unit,
    onShowMatches: (RecognizedSong) -> Unit,
    onViewPage: (String) -> Unit,
) = Column(
    verticalArrangement = Arrangement.spacedBy(8.dp),
    modifier = Modifier.fillMaxWidth(),
) {
    val resources = LocalResources.current
    Button(
        onClick = { onPlay(song) },
        enabled = !searching,
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (searching) {
            CircularProgressIndicator(
                modifier = Modifier.size(ButtonDefaults.IconSize),
                strokeWidth = 2.dp,
            )
            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
            Text(resources.getString(R.string.action_play))
        } else {
            ActionLabel(R.drawable.play_arrow, resources.getString(R.string.action_play))
        }
    }
    if (notFound)
        Text(
            resources.getString(
                R.string.dialog_body_recognize_song_not_on_youtube,
                resources.getString(R.string.source_ytm),
            ),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
        )
    FilledTonalButton(onClick = { onShowMatches(song) }, modifier = Modifier.fillMaxWidth()) {
        ActionLabel(R.drawable.search, resources.getString(R.string.action_show_matches))
    }
    song.pageUrl?.let { url ->
        OutlinedButton(onClick = { onViewPage(url) }, modifier = Modifier.fillMaxWidth()) {
            ActionLabel(
                R.drawable.open_in_new,
                resources.getString(R.string.action_view_on, RECOGNIZER_NAME),
            )
        }
    }
}

@Composable
private fun ActionLabel(@DrawableRes icon: Int, label: String) {
    Icon(
        painterResource(icon),
        contentDescription = null,
        modifier = Modifier.size(ButtonDefaults.IconSize),
    )
    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
    Text(label)
}

/**
 * The big microphone button that listens while held, like the Songs tab.
 *
 * @param since when listening started, `null` while not listening
 * @param tooShort the last hold ended too early
 */
@Composable
private fun HoldButton(
    since: Long?,
    tooShort: Boolean,
    onListen: () -> Unit,
    onStop: () -> Unit,
) {
    val resources = LocalResources.current
    val hold = rememberHoldToListen(onListen, onStop)
    val description = resources.getString(R.string.semantic_recognize_song)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        val heard = if (since == null) Duration.ZERO else rememberHeard(since).value
        ListenRing(heard, 152.dp, 4.dp) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(120.dp)
                    .semantics {
                        contentDescription = description
                        role = Role.Button
                    }
                    .then(hold),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painterResource(R.drawable.mic),
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                    )
                }
            }
        }
        val seconds = MIN_LISTEN.inWholeSeconds.toInt()
        Text(
            when {
                since != null -> resources.getString(R.string.dialog_body_recognize_song_listening)
                tooShort -> resources.getString(
                    R.string.snackbar_recognize_song_too_short,
                    resources.getQuantityString(R.plurals.second, seconds, seconds),
                )

                else -> resources.getString(
                    R.string.dialog_body_recognize_song_hold,
                    resources.getQuantityString(R.plurals.second, seconds, seconds),
                )
            },
            color = if (tooShort && since == null)
                MaterialTheme.colorScheme.error
            else
                MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

//<editor-fold defaultstate="collapsed" desc="Previews">
private val previewSong = RecognizedSong(
    "Midnight Drive",
    "Neon Coast",
    "Afterglow",
    explicit = true,
    pageUrl = "https://example.com",
)

/** The bubble's ring and the dialog's in each zone. */
@OptIn(ExperimentalMaterial3Api::class)
@ComponentPreviews
@Composable
private fun ListenProgressPreview() = PreviewTheme {
    Surface {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(16.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(4.seconds, 10.seconds, 14.seconds).forEach {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ) {
                        Box(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                            ListenBubbleContent(it)
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(4.seconds, 10.seconds, 14.seconds).forEach {
                    ListenRing(it, 88.dp, 4.dp) {
                        Icon(painterResource(R.drawable.mic), contentDescription = null)
                    }
                }
            }
        }
    }
}

@ComponentPreviews
@Composable
private fun IdentifyingPreview() = PreviewTheme {
    SongRecognitionDialog(RecognitionState.Identifying, {}, {}, {}, {}, {}, {}, {})
}

@ComponentPreviews
@Composable
private fun FoundPreview() = PreviewTheme {
    SongRecognitionDialog(RecognitionState.Found(previewSong), {}, {}, {}, {}, {}, {}, {})
}

/** Play found nothing on YouTube. */
@ComponentPreviews
@Composable
private fun NotFoundPreview() = PreviewTheme {
    SongRecognitionDialog(
        RecognitionState.Found(previewSong, notFound = true),
        {}, {}, {}, {}, {}, {}, {},
    )
}

@ComponentPreviews
@Composable
private fun FailedPreview() = PreviewTheme {
    SongRecognitionDialog(
        RecognitionState.Failed(R.string.dialog_body_recognize_song_no_match),
        {}, {}, {}, {}, {}, {}, {},
    )
}

/** After Retry, the last hold too short. */
@ComponentPreviews
@Composable
private fun ReadyPreview() = PreviewTheme {
    SongRecognitionDialog(RecognitionState.Ready(tooShort = true), {}, {}, {}, {}, {}, {}, {})
}
//</editor-fold>
