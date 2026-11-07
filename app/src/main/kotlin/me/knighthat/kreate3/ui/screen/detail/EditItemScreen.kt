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

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.OpenDocument
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.knighthat.kreate3.R
import me.knighthat.kreate3.ui.component.LocalNotifier
import me.knighthat.kreate3.ui.component.PreviewTheme
import me.knighthat.kreate3.ui.component.ScreenPreviews
import me.knighthat.kreate3.ui.component.Thumbnail
import org.koin.androidx.compose.koinViewModel

/** Changes the title, artists and picture of a song of the library or of a source. */
@Composable
fun EditSongScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    viewModel: EditSongViewModel = koinViewModel(),
) = EditItemScreen(
    R.string.screen_title_edit_song,
    R.string.title,
    onBack,
    modifier,
    contentPadding,
    viewModel
)

/** Changes the title, artists and picture of an album of the library or of a source. */
@Composable
fun EditAlbumScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    viewModel: EditAlbumViewModel = koinViewModel(),
) = EditItemScreen(
    R.string.screen_title_edit_album,
    R.string.title,
    onBack,
    modifier,
    contentPadding,
    viewModel
)

/** Changes the name and picture of an artist of the library or of a source. */
@Composable
fun EditArtistScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    viewModel: EditArtistViewModel = koinViewModel(),
) = EditItemScreen(
    R.string.screen_title_edit_artist,
    R.string.name,
    onBack,
    modifier,
    contentPadding,
    viewModel
)

/**
 * The screen of an [EditItemViewModel]. Nothing is stored until Apply, leaving with changes asks
 * whether to save them. A remote item is stored by Apply, not when the screen opens.
 */
@Composable
private fun EditItemScreen(
    titleRes: Int,
    nameRes: Int,
    onBack: () -> Unit,
    modifier: Modifier,
    contentPadding: PaddingValues,
    viewModel: EditItemViewModel,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val failed by viewModel.failed.collectAsStateWithLifecycle()
    val notifier = LocalNotifier.current
    val resources = LocalResources.current
    var asking by rememberSaveable { mutableStateOf(false) }
    // The file picker: the photo picker only lists what the media index knows, not Downloads
    val pick = rememberLauncherForActivityResult(OpenDocument()) { uri ->
        if (uri != null) viewModel.edit { it.copy(thumbnail = uri) }
    }

    val current = state
    val busy = current?.saving == true
    val leave = { if (current?.dirty == true) asking = true else onBack() }
    // Applies, then goes on with what the user was doing
    val apply = { afterwards: () -> Unit ->
        viewModel.apply { stored ->
            if (stored) {
                notifier.post(resources.getString(R.string.snackbar_changes_saved))
                afterwards()
            } else {
                notifier.post(resources.getString(R.string.error_edit_apply))
            }
        }
    }

    // The item is nowhere to be found, there is nothing to edit
    val onBackLatest by rememberUpdatedState(onBack)
    LaunchedEffect(failed) {
        if (failed) {
            notifier.post(resources.getString(R.string.error_edit_load))
            onBackLatest()
        }
    }

    BackHandler(enabled = current?.dirty == true || busy) { if (!busy) leave() }

    if (current != null)
        EditItemContent(
            title = stringResource(titleRes),
            nameLabel = stringResource(nameRes),
            state = current,
            hasArtists = viewModel.hasArtists,
            localSongId = viewModel.localSongId,
            onChange = viewModel::edit,
            onPickImage = { pick.launch(arrayOf("image/*")) },
            onReset = viewModel::reset,
            onApply = { apply {} },
            onBack = { if (!busy) leave() },
            modifier = modifier,
            contentPadding = contentPadding,
        )

    if (asking)
        PlaylistLeaveDialog(
            onSave = {
                asking = false
                apply(onBack)
            },
            onDiscard = {
                asking = false
                onBack()
            },
            onClose = { asking = false },
            body = R.string.dialog_body_unsaved_edits,
        )
}

@Composable
private fun EditItemContent(
    title: String,
    nameLabel: String,
    state: EditItemState,
    hasArtists: Boolean,
    localSongId: Long?,
    onChange: ((ItemDraft) -> ItemDraft) -> Unit,
    onPickImage: () -> Unit,
    onReset: () -> Unit,
    onApply: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val draft = state.draft
    val original = state.original
    val enabled = !state.saving

    DetailScaffold(
        title = title,
        onBack = onBack,
        modifier = modifier,
        contentPadding = contentPadding,
        actions = {
            TextButton(onClick = onReset, enabled = enabled && state.dirty) {
                Text(stringResource(R.string.action_reset))
            }
            TextButton(onClick = onApply, enabled = enabled && state.dirty && state.valid) {
                Text(stringResource(R.string.action_apply))
            }
        },
    ) { padding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(vertical = 16.dp),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth(),
            ) {
                EditablePicture(
                    thumbnail = draft.thumbnail,
                    localSongId = localSongId,
                    enabled = enabled,
                    onPick = onPickImage,
                )
                if (draft.thumbnail != original.thumbnail)
                    TextButton(
                        onClick = { onChange { it.copy(thumbnail = original.thumbnail) } },
                        enabled = enabled,
                    ) { Text(stringResource(R.string.action_use_original)) }
            }

            OutlinedTextField(
                value = draft.name,
                onValueChange = { value -> onChange { it.copy(name = value) } },
                label = { Text(nameLabel) },
                singleLine = true,
                isError = !state.valid,
                trailingIcon = original.takeIf { draft.name.trim() != it.name.trim() }?.let {
                    { UseOriginal(enabled) { onChange { d -> d.copy(name = it.name) } } }
                },
                enabled = enabled,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = if (hasArtists) ImeAction.Next else ImeAction.Done,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            )

            if (hasArtists)
                OutlinedTextField(
                    value = draft.artists,
                    onValueChange = { value -> onChange { it.copy(artists = value) } },
                    label = { Text(stringResource(R.string.artists)) },
                    supportingText = { Text(stringResource(R.string.screen_edit_hint_artists)) },
                    minLines = 2,
                    trailingIcon = original.takeIf { draft.artistList != it.artistList }?.let {
                        { UseOriginal(enabled) { onChange { d -> d.copy(artists = it.artists) } } }
                    },
                    enabled = enabled,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                )
        }
    }
}

/** The button that puts a field's original value back. */
@Composable
private fun UseOriginal(enabled: Boolean, onClick: () -> Unit) =
    IconButton(onClick = onClick, enabled = enabled) {
        Icon(
            painter = painterResource(R.drawable.restart_alt),
            contentDescription = stringResource(R.string.action_use_original),
        )
    }

/**
 * A picture with a pencil on its corner that picks another one, on a background that lets the
 * picture through. Without [onPick] it is only shown, for one that can't be changed.
 */
@Composable
internal fun EditablePicture(
    thumbnail: Uri?,
    enabled: Boolean,
    onPick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    localSongId: Long? = null,
) {
    Box(modifier = modifier) {
        Thumbnail(
            uri = thumbnail,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.size(200.dp),
            localSongId = localSongId,
        )
        // Without [onPick] the picture can't be changed, it only shows
        if (onPick != null) {
            // On the corner, over the picture, it picks another one
            FilledIconButton(
                onClick = onPick,
                enabled = enabled,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                ),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.edit),
                    contentDescription = stringResource(R.string.action_choose_image),
                )
            }
        }
    }
}

@ScreenPreviews
@Composable
private fun EditItemPreview() = PreviewTheme {
    val original = ItemDraft("Afterglow", "Neon Coast\nThe Tides")
    EditItemContent(
        title = stringResource(R.string.screen_title_edit_song),
        nameLabel = stringResource(R.string.title),
        state = EditItemState(original, original, original.copy(name = "Afterglow (live)")),
        hasArtists = true,
        localSongId = null,
        onChange = {},
        onPickImage = {},
        onReset = {},
        onApply = {},
        onBack = {},
    )
}
