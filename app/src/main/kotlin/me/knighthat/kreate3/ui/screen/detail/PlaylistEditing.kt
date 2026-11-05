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

package me.knighthat.kreate3.ui.screen.detail

import android.content.res.Resources
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import me.knighthat.kreate3.R
import me.knighthat.kreate3.notification.Message
import me.knighthat.kreate3.notification.undoMessage
import me.knighthat.kreate3.ui.component.CoverEditMode
import me.knighthat.kreate3.ui.component.CoverEditOverlay
import me.knighthat.kreate3.ui.component.SongItem
import me.knighthat.kreate3.ui.component.SongMenuAction
import me.knighthat.kreate3.ui.component.songsLabel
import sh.calvin.reorderable.ReorderableCollectionItemScope
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.ReorderableLazyListState
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * Where editing a playlist's songs stands, as its page shows it, see [PlaylistEdit].
 *
 * @property arranging the songs as the user left them, `null` while nothing is being edited
 * @property mode what the covers do while editing
 * @property saving whether the changes are on their way to the server
 * @property deleting whether the playlist is being deleted on the server
 * @property confirmingDelete whether the dialog that asks to delete it shows
 * @property dirty whether there are changes not sent yet, leaving then asks first
 * @property preparing whether the page is getting what editing needs, before it begins
 */
@Stable
data class PlaylistEditState(
    val arranging: List<MediaItem>? = null,
    val mode: CoverEditMode = CoverEditMode.NONE,
    val saving: Boolean = false,
    val deleting: Boolean = false,
    val confirmingDelete: Boolean = false,
    val dirty: Boolean = false,
    val preparing: Boolean = false,
) {

    /** Whether a request of the page waits for the server: the sheet's rows are off. */
    val busy: Boolean get() = saving || preparing

    /**
     * Whether the page is in edit mode or about to enter or leave it: its songs are not the
     * server's, so nothing may fetch them again.
     */
    val editing: Boolean get() = arranging != null || mode != CoverEditMode.NONE || busy || deleting
}

/**
 * What the user can do on a page whose playlist is edited, see [PlaylistEditState].
 *
 * @property onArrange gets the songs after each change
 * @property onModeChange switches the covers, or sends the changes with [CoverEditMode.NONE]
 * @property onRemoved a song was taken out of the list, with how to put it back
 * @property onAskDelete shows the dialog that asks to delete the playlist
 * @property onCancelDelete closes it
 * @property onDelete deletes the playlist on the server, once the user confirmed
 * @property onSaveAndLeave sends the changes, then leaves the page
 * @property onDiscardAndLeave leaves the page without sending anything
 */
@Stable
class PlaylistEditActions(
    val onArrange: (List<MediaItem>) -> Unit = {},
    val onModeChange: (CoverEditMode) -> Unit = {},
    val onRemoved: (song: MediaItem, undo: () -> Unit) -> Unit = { _, _ -> },
    val onAskDelete: () -> Unit = {},
    val onCancelDelete: () -> Unit = {},
    val onDelete: () -> Unit = {},
    val onSaveAndLeave: () -> Unit = {},
    val onDiscardAndLeave: () -> Unit = {},
)

/** [PlaylistEdit]'s flows as the state of the page, collected with its lifecycle. */
@Composable
fun PlaylistEdit.collectState(preparing: Boolean = false): PlaylistEditState {
    val arranging by this.arranging.collectAsStateWithLifecycle()
    val mode by this.mode.collectAsStateWithLifecycle()
    val saving by this.saving.collectAsStateWithLifecycle()
    val deleting by this.deleting.collectAsStateWithLifecycle()
    val confirmingDelete by this.confirmingDelete.collectAsStateWithLifecycle()
    return PlaylistEditState(
        arranging = arranging,
        mode = mode,
        saving = saving,
        deleting = deleting,
        confirmingDelete = confirmingDelete,
        dirty = arranging != null && hasChanges(),
        preparing = preparing,
    )
}

/**
 * The songs of a page while its playlist is edited: moved by their handles, taken out by their
 * covers. They are changed here at once, [PlaylistEditActions.onArrange] is told after each
 * change.
 */
@Stable
class PlaylistEditing internal constructor(
    private val state: PlaylistEditState,
    private val actions: PlaylistEditActions,
    private val reorder: ReorderableLazyListState,
    private val haptics: androidx.compose.ui.hapticfeedback.HapticFeedback,
    private val lifted: androidx.compose.ui.graphics.Color,
    private val removeLabel: String,
    private val removeDescription: String,
    private val entries: () -> List<MediaItem>?,
    private val setEntries: (List<MediaItem>) -> Unit,
) {

    /** Whether the songs are the user's, being edited. */
    val active: Boolean get() = state.arranging != null

    /** Takes [song] out of the list, with an Undo that puts it back at its place. */
    fun remove(song: MediaItem) {
        if (state.saving) return
        val list = entries() ?: return
        val index = list.indexOfFirst { it.mediaId == song.mediaId }
        if (index < 0) return
        list.toMutableList().apply { removeAt(index) }.also(setEntries).also(actions.onArrange)
        actions.onRemoved(song) {
            val now = entries() ?: return@onRemoved
            if (now.none { it.mediaId == song.mediaId }) {
                now.toMutableList().apply { add(index.coerceAtMost(size), song) }
                    .also(setEntries).also(actions.onArrange)
            }
        }
    }

    /** The rows of the songs being edited. */
    fun LazyListScope.rows(onSongClick: (MediaItem) -> Unit) {
        val moving = entries() ?: return
        items(moving, key = { it.mediaId }) { song ->
            ReorderableItem(reorder, key = song.mediaId) { isDragging ->
                SongItem(
                    item = song,
                    onClick = { onSongClick(song) },
                    // Opaque, the songs it passes over don't show through it
                    modifier = if (isDragging) Modifier.background(lifted) else Modifier,
                    thumbnailOverlay = {
                        CoverEditOverlay(
                            mode = state.mode,
                            handle = handle(),
                            removeDescription = removeDescription,
                            onRemove = { remove(song) },
                        )
                    },
                    menuActions = listOf(
                        SongMenuAction(removeLabel, R.drawable.playlist_remove) { remove(song) }
                    ),
                )
            }
        }
    }

    private fun ReorderableCollectionItemScope.handle(): Modifier = Modifier.draggableHandle(
        enabled = !state.saving,
        onDragStarted = {
            haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
        },
    )
}

/**
 * The editing of a page's playlist, see [PlaylistEditing].
 *
 * @param contentPadding what the list is given, the reorderable list needs it too
 */
@Composable
fun rememberPlaylistEditing(
    state: PlaylistEditState,
    actions: PlaylistEditActions,
    listState: LazyListState,
    contentPadding: PaddingValues,
): PlaylistEditing {
    val haptics = LocalHapticFeedback.current
    val editing = state.arranging != null
    // The user's changes are made here at once, the view model is told after each
    var entries by remember(editing) { mutableStateOf(state.arranging) }
    val reorder = rememberReorderableLazyListState(listState, contentPadding) { from, to ->
        // Their indices count the header too, the songs are found by key
        entries = entries?.toMutableList()?.apply {
            val fromIndex = indexOfFirst { it.mediaId == from.key }
            val toIndex = indexOfFirst { it.mediaId == to.key }
            if (fromIndex >= 0 && toIndex >= 0) add(toIndex, removeAt(fromIndex))
        }
        entries?.let(actions.onArrange)
        haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
    }
    return PlaylistEditing(
        state = state,
        actions = actions,
        reorder = reorder,
        haptics = haptics,
        lifted = MaterialTheme.colorScheme.surfaceContainerHighest,
        removeLabel = stringResource(R.string.menu_remove_from_playlist),
        removeDescription = stringResource(R.string.semantic_remove_from_the_playlist),
        entries = { entries },
        setEntries = { entries = it },
    )
}

/** "Removed "title" from playlist", with an Undo that runs [undo]. */
internal fun removedMessage(
    resources: Resources,
    song: MediaItem,
    undo: () -> Unit,
): Message = undoMessage(
    resources,
    resources.getString(
        R.string.snackbar_removed_from_playlist,
        songsLabel(resources, listOf(song))
    ),
    undo,
)

/**
 * Asks before the page is left with changes not sent, and keeps it while a request waits for the
 * server.
 *
 * @param onAsk the page asks what to do with the changes
 */
@Composable
fun PlaylistEditBackHandler(state: PlaylistEditState, onAsk: () -> Unit) {
    BackHandler(enabled = state.dirty || state.saving || state.deleting) {
        if (!state.saving && !state.deleting) onAsk()
    }
}

/** What to do with changes not sent when the page is left: send, throw away, or stay. */
@Composable
fun PlaylistLeaveDialog(
    onSave: () -> Unit,
    onDiscard: () -> Unit,
    onClose: () -> Unit,
) = AlertDialog(
    onDismissRequest = onClose,
    title = { Text(stringResource(R.string.dialog_title_unsaved_changes)) },
    text = { Text(stringResource(R.string.dialog_body_unsaved_changes)) },
    // One under the other, three labels don't fit side by side
    confirmButton = {
        Column(horizontalAlignment = Alignment.End) {
            TextButton(onClick = onSave) { Text(stringResource(R.string.action_save_changes)) }
            TextButton(onClick = onDiscard) {
                Text(stringResource(R.string.action_discard_changes))
            }
            TextButton(onClick = onClose) { Text(stringResource(R.string.action_close)) }
        }
    },
)

/**
 * Asks to delete the playlist [name]. While [deleting] the server is asked, nothing can be
 * pressed and the dialog stays until it answered.
 */
@Composable
fun PlaylistDeleteDialog(
    name: String,
    deleting: Boolean,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
) = AlertDialog(
    onDismissRequest = onCancel,
    title = { Text(stringResource(R.string.dialog_title_delete_playlist)) },
    text = {
        Text(
            stringResource(
                R.string.dialog_body_delete_playlist,
                stringResource(R.string.quoted, name)
            )
        )
    },
    confirmButton = {
        TextButton(onClick = onDelete, enabled = !deleting) {
            if (deleting)
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
            else
                Text(stringResource(R.string.action_delete))
        }
    },
    dismissButton = {
        TextButton(onClick = onCancel, enabled = !deleting) {
            Text(stringResource(R.string.action_cancel))
        }
    },
)
