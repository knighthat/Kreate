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

package me.knighthat.kreate3.ui.screen.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import me.knighthat.kreate3.R
import me.knighthat.kreate3.model.Origin
import me.knighthat.kreate3.notification.Message
import me.knighthat.kreate3.provider.youtube.PlaylistPrivacy
import me.knighthat.kreate3.ui.component.LocalIconOnly
import me.knighthat.kreate3.ui.component.LocalNotifier
import me.knighthat.kreate3.ui.component.MessageHost
import me.knighthat.kreate3.ui.component.PlaybackMiniPlayer
import me.knighthat.kreate3.ui.component.PreviewTheme
import me.knighthat.kreate3.ui.component.ScreenPreviews
import me.knighthat.kreate3.ui.component.icon
import me.knighthat.kreate3.ui.component.labelRes
import me.knighthat.kreate3.ui.component.openSubsonicName
import me.knighthat.kreate3.ui.component.playlistOrigins
import me.knighthat.kreate3.ui.component.rememberMiniPlayerSpace
import me.knighthat.kreate3.ui.component.sourceRes
import me.knighthat.kreate3.ui.screen.detail.DetailScaffold
import org.koin.androidx.compose.koinViewModel

/** The places a playlist can be made, in the order the pills show them. */
private val PLACES = listOf(Origin.YOUTUBE, Origin.LOCAL, Origin.OPEN_SUBSONIC)

/**
 * Makes a playlist: a bar item per place it can be made, a page each with the name and what that
 * place also takes. The pages slide, swiping between them is off. Places that are off or signed
 * out have no item.
 *
 * @param origin where the playlist's songs come from, only the places that can hold songs of it
 * have an item ([playlistOrigins]); `null` for any place
 * @param songId the song the playlist starts with, `null` for an empty one; it comes from [origin]
 */
@Composable
fun CreatePlaylistScreen(
    origin: Origin?,
    songId: String?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CreatePlaylistViewModel = koinViewModel(),
) {
    val places by viewModel.places.collectAsStateWithLifecycle()
    val notifier = LocalNotifier.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val serverName = openSubsonicName()
    var creating by remember { mutableStateOf(false) }

    val choices = PLACES.filter {
        it in places && (origin == null || it in origin.playlistOrigins)
    }

    CreatePlaylistContent(
        choices = choices,
        serverName = serverName,
        creating = creating,
        onCreate = { playlist ->
            scope.launch {
                creating = true
                val outcome = viewModel.create(playlist, songId, origin)
                creating = false

                val name = resources.getString(R.string.quoted, playlist.name.trim())
                when (outcome) {
                    CreateOutcome.CREATED -> {
                        notifier.post(
                            Message(
                                resources.getString(
                                    R.string.snackbar_playlist_created,
                                    name
                                )
                            )
                        )
                        onBack()
                    }

                    CreateOutcome.OPTIONS_NOT_SAVED -> {
                        notifier.post(
                            Message(
                                resources.getString(
                                    R.string.snackbar_playlist_options_not_saved,
                                    name
                                )
                            )
                        )
                        onBack()
                    }

                    CreateOutcome.FAILED ->
                        notifier.post(
                            Message(
                                resources.getString(
                                    R.string.error_create_playlist,
                                    name
                                )
                            )
                        )
                }
            }
        },
        onBack = onBack,
        miniPlayer = { PlaybackMiniPlayer(rememberMiniPlayerSpace()) },
        modifier = modifier,
    )
}

/**
 * @param choices the places to make the playlist at, the pills; a single one shows no pills
 * @param serverName what the OpenSubsonic server is called, see [openSubsonicName]
 * @param creating whether a playlist is being made, everything waits for it
 * @param onCreate gets what was filled in for the picked place
 * @param miniPlayer the mini player above the bar, like on the home screens
 */
@Composable
private fun CreatePlaylistContent(
    choices: List<Origin>,
    serverName: String,
    creating: Boolean,
    onCreate: (NewPlaylist) -> Unit,
    onBack: () -> Unit,
    miniPlayer: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Local is where it starts, and where it goes back to if the picked place is gone
    var picked by rememberSaveable { mutableStateOf(Origin.LOCAL) }
    var name by rememberSaveable { mutableStateOf("") }
    // The description is kept when switching between the two places that take one
    var description by rememberSaveable { mutableStateOf("") }
    var pinned by rememberSaveable { mutableStateOf(false) }
    var public by rememberSaveable { mutableStateOf(false) }
    var privacy by rememberSaveable { mutableStateOf(PlaylistPrivacy.PRIVATE) }
    val iconOnly = LocalIconOnly.current

    val shown = if (picked in choices) picked else Origin.LOCAL
    val canCreate = name.isNotBlank() && !creating
    val create = {
        onCreate(
            when (shown) {
                Origin.YOUTUBE -> NewPlaylist.YouTube(name, description, privacy)
                Origin.OPEN_SUBSONIC -> NewPlaylist.OpenSubsonic(name, description, public)
                Origin.LOCAL -> NewPlaylist.Local(name, pinned)
            }
        )
    }

    DetailScaffold(
        title = stringResource(R.string.screen_title_new_playlist),
        onBack = onBack,
        modifier = modifier,
        actions = {
            TextButton(onClick = create, enabled = canCreate) {
                Text(stringResource(R.string.action_create))
            }
        },
        bottomBar = {
            // The mini player sits on the bar, like on the home screens. The page runs under the
            // whole bar already, so its room needs no splitting off
            Column {
                miniPlayer()
                // One place is nothing to pick from
                if (choices.size > 1)
                    NavigationBar {
                        choices.forEach {
                            val label =
                                if (it == Origin.OPEN_SUBSONIC) serverName else stringResource(it.sourceRes)
                            NavigationBarItem(
                                selected = it == shown,
                                onClick = { picked = it },
                                enabled = !creating,
                                icon = {
                                    Icon(
                                        painter = painterResource(it.icon),
                                        // Without the text, the icon has to name the place
                                        contentDescription = if (iconOnly) label else null,
                                    )
                                },
                                label = if (iconOnly) null else ({ Text(label) }),
                            )
                        }
                    }
            }
        },
        // Above the bar, the app's scaffold would put messages over it
        snackbarHost = { MessageHost() },
    ) { padding ->
        // Pages slide toward the pill picked, there is no swiping between them
        AnimatedContent(
            targetState = shown,
            transitionSpec = {
                val toRight = choices.indexOf(targetState) > choices.indexOf(initialState)
                val way = if (toRight)
                    AnimatedContentTransitionScope.SlideDirection.Start
                else
                    AnimatedContentTransitionScope.SlideDirection.End
                slideIntoContainer(way) togetherWith slideOutOfContainer(way)
            },
            label = "CreatePlaylistPage",
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) { page ->
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 16.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.name)) },
                    singleLine = true,
                    enabled = !creating,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { if (canCreate) create() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                )

                if (page != Origin.LOCAL)
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text(stringResource(R.string.description)) },
                        minLines = 3,
                        enabled = !creating,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                    )

                when (page) {
                    Origin.YOUTUBE -> {
                        Text(
                            text = stringResource(R.string.setting_title_playlist_privacy),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                        PlaylistPrivacy.entries.forEach {
                            ListItem(
                                headlineContent = { Text(stringResource(it.labelRes)) },
                                leadingContent = {
                                    RadioButton(
                                        selected = it == privacy,
                                        onClick = null
                                    )
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                modifier = Modifier.clickable(enabled = !creating) { privacy = it },
                            )
                        }
                    }

                    Origin.OPEN_SUBSONIC -> SwitchRow(
                        title = stringResource(R.string.setting_title_public_playlist),
                        description = stringResource(R.string.setting_description_public_playlist),
                        checked = public,
                        enabled = !creating,
                        onChange = { public = it },
                    )

                    Origin.LOCAL -> SwitchRow(
                        title = stringResource(R.string.setting_title_pin_playlist),
                        description = stringResource(R.string.setting_description_pin_playlist),
                        checked = pinned,
                        enabled = !creating,
                        onChange = { pinned = it },
                    )
                }
            }
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
) = ListItem(
    headlineContent = { Text(title) },
    supportingContent = { Text(description) },
    trailingContent = { Switch(checked = checked, onCheckedChange = null, enabled = enabled) },
    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    modifier = Modifier.clickable(enabled = enabled) { onChange(!checked) },
)

@ScreenPreviews
@Composable
private fun CreatePlaylistPreview() = PreviewTheme {
    CreatePlaylistContent(
        choices = PLACES,
        serverName = "Navidrome",
        creating = false,
        onCreate = {},
        onBack = {},
        miniPlayer = {},
    )
}
