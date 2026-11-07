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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.knighthat.kreate3.R
import me.knighthat.kreate3.ui.component.LocalNotifier
import me.knighthat.kreate3.ui.component.PreviewTheme
import me.knighthat.kreate3.ui.component.ScreenPreviews
import me.knighthat.kreate3.ui.screen.home.SwitchRow
import org.koin.androidx.compose.koinViewModel

/**
 * Changes a playlist of this device: its picture, name and pin. Nothing is stored until Apply,
 * leaving with changes asks whether to save them.
 */
@Composable
fun EditPlaylistScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    viewModel: EditPlaylistViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
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
                notifier.post(resources.getString(R.string.snackbar_playlist_changes_saved))
                afterwards()
            } else {
                notifier.post(resources.getString(R.string.error_playlist_apply_changes))
            }
        }
    }

    BackHandler(enabled = current?.dirty == true || busy) { if (!busy) leave() }

    if (current != null)
        EditPlaylistContent(
            state = current,
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
        )
}

@Composable
private fun EditPlaylistContent(
    state: EditPlaylistState,
    onChange: ((PlaylistDraft) -> PlaylistDraft) -> Unit,
    onPickImage: () -> Unit,
    onReset: () -> Unit,
    onApply: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val draft = state.draft
    val enabled = !state.saving

    DetailScaffold(
        title = stringResource(R.string.screen_title_edit_playlist),
        onBack = onBack,
        modifier = modifier,
        contentPadding = contentPadding,
        actions = {
            TextButton(onClick = onReset, enabled = enabled && state.dirty) {
                Text(stringResource(R.string.action_reset))
            }
            TextButton(onClick = onApply, enabled = enabled && state.dirty && draft.valid) {
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
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
                EditablePicture(
                    thumbnail = draft.thumbnail,
                    enabled = enabled,
                    onPick = onPickImage,
                )
            }

            OutlinedTextField(
                value = draft.name,
                onValueChange = { value -> onChange { it.copy(name = value) } },
                label = { Text(stringResource(R.string.name)) },
                singleLine = true,
                isError = draft.name.isBlank(),
                enabled = enabled,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            )

            SwitchRow(
                title = stringResource(R.string.setting_title_pin_playlist),
                description = stringResource(R.string.setting_description_pin_playlist),
                checked = draft.pinned,
                enabled = enabled,
                onChange = { value -> onChange { it.copy(pinned = value) } },
            )
        }
    }
}

@ScreenPreviews
@Composable
private fun EditPlaylistPreview() = PreviewTheme {
    val saved = PlaylistDraft(name = "Road trip", pinned = true)
    EditPlaylistContent(
        state = EditPlaylistState(saved, saved.copy(name = "Road trip 2")),
        onChange = {},
        onPickImage = {},
        onReset = {},
        onApply = {},
        onBack = {},
    )
}
