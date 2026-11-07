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
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
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
import me.knighthat.kreate3.model.Origin
import me.knighthat.kreate3.provider.youtube.PlaylistPrivacy
import me.knighthat.kreate3.ui.component.LocalNotifier
import me.knighthat.kreate3.ui.component.PreviewTheme
import me.knighthat.kreate3.ui.component.ScreenPreviews
import me.knighthat.kreate3.ui.screen.home.PrivacyChoices
import me.knighthat.kreate3.ui.screen.home.SwitchRow
import org.koin.androidx.compose.koinViewModel

/**
 * Changes the name, description and visibility of a playlist of a source. Nothing is sent until
 * Apply, leaving with changes asks whether to send them.
 */
@Composable
fun EditOnlinePlaylistScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    viewModel: EditOnlinePlaylistViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val failed by viewModel.failed.collectAsStateWithLifecycle()
    val notifier = LocalNotifier.current
    val resources = LocalResources.current
    var asking by rememberSaveable { mutableStateOf(false) }

    val current = state
    val busy = current?.saving == true
    val leave = { if (current?.dirty == true) asking = true else onBack() }
    // Sends, then goes on with what the user was doing
    val apply = { afterwards: () -> Unit ->
        viewModel.apply { sent ->
            if (sent) {
                notifier.post(resources.getString(R.string.snackbar_changes_saved))
                afterwards()
            } else {
                notifier.post(resources.getString(R.string.error_edit_apply))
            }
        }
    }

    // A playlist that isn't there or isn't the user's has nothing to edit
    val onBackLatest by rememberUpdatedState(onBack)
    LaunchedEffect(failed) {
        if (failed) {
            notifier.post(resources.getString(R.string.error_edit_load))
            onBackLatest()
        }
    }

    BackHandler(enabled = current?.dirty == true || busy) { if (!busy) leave() }

    if (current != null)
        EditOnlinePlaylistContent(
            origin = viewModel.origin,
            state = current,
            onChange = viewModel::edit,
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
private fun EditOnlinePlaylistContent(
    origin: Origin,
    state: EditOnlinePlaylistState,
    onChange: ((OnlineDraft) -> OnlineDraft) -> Unit,
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
            // Shown as it is, neither source takes a picture for a playlist
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth(),
            ) {
                EditablePicture(thumbnail = draft.thumbnail, enabled = enabled, onPick = null)
            }

            OutlinedTextField(
                value = draft.name,
                onValueChange = { value -> onChange { it.copy(name = value) } },
                label = { Text(stringResource(R.string.name)) },
                singleLine = true,
                isError = !state.valid,
                enabled = enabled,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Next,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            )
            OutlinedTextField(
                value = draft.description,
                onValueChange = { value -> onChange { it.copy(description = value) } },
                label = { Text(stringResource(R.string.description)) },
                minLines = 3,
                enabled = enabled,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            )

            if (origin == Origin.YOUTUBE)
                PrivacyChoices(
                    selected = draft.privacy,
                    enabled = enabled,
                    onSelect = { value -> onChange { it.copy(privacy = value) } },
                )
            else
                SwitchRow(
                    title = stringResource(R.string.setting_title_public_playlist),
                    description = stringResource(R.string.setting_description_public_playlist),
                    checked = draft.privacy == PlaylistPrivacy.PUBLIC,
                    enabled = enabled,
                    onChange = { on ->
                        onChange {
                            it.copy(privacy = if (on) PlaylistPrivacy.PUBLIC else PlaylistPrivacy.PRIVATE)
                        }
                    },
                )
        }
    }
}

@ScreenPreviews
@Composable
private fun EditOnlinePlaylistPreview() = PreviewTheme {
    val saved = OnlineDraft("Road trip", "Songs for the long drive", PlaylistPrivacy.UNLISTED)
    EditOnlinePlaylistContent(
        origin = Origin.YOUTUBE,
        state = EditOnlinePlaylistState(saved, saved.copy(name = "Road trip 2")),
        onChange = {},
        onReset = {},
        onApply = {},
        onBack = {},
    )
}
