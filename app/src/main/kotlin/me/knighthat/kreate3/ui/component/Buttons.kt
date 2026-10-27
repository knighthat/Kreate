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

import android.content.Context
import android.content.Intent
import androidx.annotation.DrawableRes
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import me.knighthat.kreate3.ProfilesActivity
import me.knighthat.kreate3.R
import me.knighthat.kreate3.preferences.Preferences
import org.koin.core.context.GlobalContext

/**
 * An icon button whose only content is [icon], described to screen readers as [description].
 */
@Composable
private fun IconAction(
    @DrawableRes icon: Int,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(painterResource(icon), contentDescription = description)
    }
}

/**
 * A large button with [icon] above [title] and [subtitle]. While [busy] the icon is a spinning
 * progress indicator and the button is off.
 */
@Composable
fun TileButton(
    @DrawableRes icon: Int,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    busy: Boolean = false,
    colors: ButtonColors = ButtonDefaults.filledTonalButtonColors(),
    contentPadding: PaddingValues = PaddingValues(horizontal = 24.dp, vertical = 40.dp),
) {
    FilledTonalButton(
        onClick = onClick,
        enabled = !busy,
        shape = RoundedCornerShape(16.dp),
        contentPadding = contentPadding,
        colors = colors,
        modifier = modifier,
    ) {
        // From the top, so the icons of tiles side by side line up
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxHeight(),
        ) {
            if (busy)
                CircularProgressIndicator(Modifier.size(56.dp))
            else
            // The button already says what it does
                Icon(
                    painterResource(icon),
                    contentDescription = null,
                    modifier = Modifier.size(56.dp)
                )

            Spacer(Modifier.height(24.dp))

            Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun BackButton(onClick: () -> Unit, modifier: Modifier = Modifier) =
    IconAction(R.drawable.arrow_back, stringResource(R.string.semantic_back), onClick, modifier)

/** Opens every item of a row that shows only some, see [SectionTitle]. */
@Composable
fun SeeMoreButton(onClick: () -> Unit, modifier: Modifier = Modifier) =
    IconAction(
        R.drawable.arrow_forward,
        stringResource(R.string.semantic_see_more),
        onClick,
        modifier
    )

@Composable
fun SearchButton(onClick: () -> Unit, modifier: Modifier = Modifier) =
    IconAction(R.drawable.search, stringResource(R.string.semantic_search), onClick, modifier)

@Composable
fun ProfileButton(modifier: Modifier = Modifier) =
    IconAction(
        icon = R.drawable.person,
        description = stringResource(R.string.semantic_profile),
        onClick = {
            val context = GlobalContext.get().get<Context>()

            context.startActivity(
                ProfilesActivity.intent(
                    context,
                    Preferences.THEME_MODE.value,
                    Preferences.CONTRAST_LEVEL.value
                )
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        },
        modifier = modifier
    )

@Composable
fun ShareButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) =
    IconButton(onClick = onClick, modifier = modifier, enabled = enabled) {
        Icon(
            painterResource(R.drawable.share),
            contentDescription = stringResource(R.string.semantic_share)
        )
    }

/** Restarts the app, off while [enabled] is `false`. */
@Composable
fun RestartAppButton(onClick: () -> Unit, enabled: Boolean, modifier: Modifier = Modifier) =
    IconButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = IconButtonDefaults.iconButtonColors(
            contentColor = MaterialTheme.colorScheme.tertiary
        )
    ) {
        Icon(
            painterResource(R.drawable.power_settings_new),
            contentDescription = stringResource(R.string.semantic_restart_the_app)
        )
    }

/** Restarts the player, off while [enabled] is `false`. */
@Composable
fun RestartPlayerButton(onClick: () -> Unit, enabled: Boolean, modifier: Modifier = Modifier) =
    IconButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = IconButtonDefaults.iconButtonColors(
            contentColor = MaterialTheme.colorScheme.tertiary
        )
    ) {
        Icon(
            painterResource(R.drawable.restart_alt),
            contentDescription = stringResource(R.string.semantic_restart_the_player),
        )
    }

/**
 * Bookmarks an album, or takes the bookmark away while [isBookmarked]. The icon shows whether it
 * is bookmarked.
 */
@Composable
fun BookmarkButton(isBookmarked: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) =
    ToggleAction(
        isSet = isBookmarked,
        setIcon = R.drawable.bookmark_filled,
        unsetIcon = R.drawable.bookmark_outline,
        description = if (isBookmarked) stringResource(R.string.semantic_remove_bookmark) else stringResource(
            R.string.semantic_bookmark
        ),
        onClick = onClick,
        modifier = modifier,
    )

/**
 * Follows an artist, or unfollows them while [isFollowed]. The icon shows whether they are
 * followed.
 */
@Composable
fun FollowButton(isFollowed: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) =
    ToggleAction(
        isSet = isFollowed,
        setIcon = R.drawable.how_to_reg,
        unsetIcon = R.drawable.person_add,
        description = if (isFollowed) stringResource(R.string.semantic_unfollow) else stringResource(
            R.string.semantic_follow
        ),
        onClick = onClick,
        modifier = modifier,
    )

/** An icon button that is [setIcon] in the primary color while [isSet], [unsetIcon] otherwise. */
@Composable
private fun ToggleAction(
    isSet: Boolean,
    @DrawableRes setIcon: Int,
    @DrawableRes unsetIcon: Int,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(
            painterResource(if (isSet) setIcon else unsetIcon),
            contentDescription = description,
            tint = if (isSet) MaterialTheme.colorScheme.primary else LocalContentColor.current,
        )
    }
}

@Composable
fun SettingsButton(onClick: () -> Unit, modifier: Modifier = Modifier) =
    IconAction(R.drawable.settings, stringResource(R.string.semantic_settings), onClick, modifier)

/**
 * Pauses while [isPlaying], plays otherwise. The icon shows what a tap does.
 */
@Composable
fun PlayPauseButton(isPlaying: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) =
    IconButton(onClick = onClick, modifier = modifier) {
        Crossfade(targetState = isPlaying, label = "PlayPauseIcon") { playing ->
            if (playing)
                Icon(
                    painterResource(R.drawable.pause),
                    contentDescription = stringResource(R.string.semantic_pause)
                )
            else
                Icon(
                    painterResource(R.drawable.play_arrow),
                    contentDescription = stringResource(R.string.semantic_play)
                )
        }
    }

@Composable
fun SkipNextButton(onClick: () -> Unit, modifier: Modifier = Modifier) =
    IconAction(R.drawable.skip_next, stringResource(R.string.semantic_next), onClick, modifier)

@Composable
fun ConfigButton(onClick: () -> Unit, modifier: Modifier = Modifier) =
    IconAction(R.drawable.tune, stringResource(R.string.semantic_config), onClick, modifier)

/** Plays every song of a list, see [QueueAction.PLAY]. */
@Composable
fun PlayAllButton(onClick: () -> Unit, modifier: Modifier = Modifier) =
    IconAction(R.drawable.play_arrow, stringResource(R.string.semantic_play_all), onClick, modifier)

/** Plays every song of a list in a random order, see [QueueAction.SHUFFLE]. */
@Composable
fun ShuffleButton(onClick: () -> Unit, modifier: Modifier = Modifier) =
    IconAction(R.drawable.shuffle, stringResource(R.string.semantic_shuffle), onClick, modifier)

/** Plays every song of a list after the song playing, see [QueueAction.PLAY_NEXT]. */
@Composable
fun PlayNextButton(onClick: () -> Unit, modifier: Modifier = Modifier) =
    IconAction(
        R.drawable.queue_play_next,
        stringResource(R.string.semantic_play_next),
        onClick,
        modifier
    )

/** Adds every song of a list to the end of the queue, see [QueueAction.ENQUEUE]. */
@Composable
fun AddToQueueButton(onClick: () -> Unit, modifier: Modifier = Modifier) =
    IconAction(
        R.drawable.playlist_add,
        stringResource(R.string.semantic_add_to_queue),
        onClick,
        modifier
    )

/** Makes a new playlist. */
@Composable
fun CreatePlaylistButton(onClick: () -> Unit, modifier: Modifier = Modifier) =
    IconAction(R.drawable.add, stringResource(R.string.semantic_create_playlist), onClick, modifier)

/** Opens one item of a list picked at random. */
@Composable
fun RandomButton(onClick: () -> Unit, modifier: Modifier = Modifier) =
    IconAction(
        R.drawable.casino,
        stringResource(R.string.semantic_open_one_at_random),
        onClick,
        modifier
    )

/**
 * Opens a menu of the actions used less often. Each item of [items] should call `close` once
 * tapped.
 */
@Composable
fun MoreButton(
    modifier: Modifier = Modifier,
    items: @Composable ColumnScope.(close: () -> Unit) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier) {
        IconAction(
            R.drawable.more_vert,
            stringResource(R.string.semantic_more),
            { expanded = true },
            Modifier
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            items { expanded = false }
        }
    }
}

//<editor-fold defaultstate="collapsed" desc="Previews">
@ComponentPreviews
@Composable
private fun TileButtonPreview() = PreviewTheme {
    Surface {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .width(400.dp)
                .padding(16.dp),
        ) {
            TileButton(
                icon = R.drawable.settings,
                title = "Restore",
                subtitle = "From a backup file",
                onClick = {},
                modifier = Modifier.weight(1f),
            )
            TileButton(
                icon = R.drawable.settings,
                title = "Restoring",
                subtitle = "While it is busy",
                onClick = {},
                modifier = Modifier.weight(1f),
                busy = true,
            )
        }
    }
}

@ComponentPreviews
@Composable
private fun IconButtonsPreview() = PreviewTheme {
    Surface {
        Column {
            Row {
                BackButton({})
                SeeMoreButton({})
                SearchButton({})
                ProfileButton()
                SettingsButton({})
                ConfigButton({})
                ShareButton({})
                ShareButton({}, enabled = false)
            }
            Row {
                PlayAllButton({})
                ShuffleButton({})
                PlayNextButton({})
                AddToQueueButton({})
                CreatePlaylistButton({})
                RandomButton({})
                SkipNextButton({})
                MoreButton { close ->
                    DropdownMenuItem(text = { Text("Item") }, onClick = close)
                }
            }
        }
    }
}

@ComponentPreviews
@Composable
private fun ToggleButtonsPreview() = PreviewTheme {
    Surface {
        Row {
            BookmarkButton(isBookmarked = false, onClick = {})
            BookmarkButton(isBookmarked = true, onClick = {})
            FollowButton(isFollowed = false, onClick = {})
            FollowButton(isFollowed = true, onClick = {})
            PlayPauseButton(isPlaying = false, onClick = {})
            PlayPauseButton(isPlaying = true, onClick = {})
        }
    }
}
//</editor-fold>
