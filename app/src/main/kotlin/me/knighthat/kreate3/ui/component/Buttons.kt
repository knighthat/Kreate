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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
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
    IconAction(R.drawable.arrow_back, "Back", onClick, modifier)

/** Opens every item of a row that shows only some, see [SectionTitle]. */
@Composable
fun SeeMoreButton(onClick: () -> Unit, modifier: Modifier = Modifier) =
    IconAction(R.drawable.arrow_forward, "See more", onClick, modifier)

@Composable
fun SearchButton(onClick: () -> Unit, modifier: Modifier = Modifier) =
    IconAction(R.drawable.search, "Search", onClick, modifier)

@Composable
fun ProfileButton(modifier: Modifier = Modifier) =
    IconAction(
        icon = R.drawable.person,
        description = "Profile",
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
        Icon(painterResource(R.drawable.share), contentDescription = "Share")
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
        description = if (isBookmarked) "Remove bookmark" else "Bookmark",
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
        description = if (isFollowed) "Unfollow" else "Follow",
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
    IconAction(R.drawable.settings, "Settings", onClick, modifier)

/**
 * Pauses while [isPlaying], plays otherwise. The icon shows what a tap does.
 */
@Composable
fun PlayPauseButton(isPlaying: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) =
    IconButton(onClick = onClick, modifier = modifier) {
        Crossfade(targetState = isPlaying, label = "PlayPauseIcon") { playing ->
            if (playing)
                Icon(painterResource(R.drawable.pause), contentDescription = "Pause")
            else
                Icon(painterResource(R.drawable.play_arrow), contentDescription = "Play")
        }
    }

@Composable
fun SkipNextButton(onClick: () -> Unit, modifier: Modifier = Modifier) =
    IconAction(R.drawable.skip_next, "Next", onClick, modifier)

@Composable
fun ConfigButton(onClick: () -> Unit, modifier: Modifier = Modifier) =
    IconAction(R.drawable.tune, "Config", onClick, modifier)

/** Plays every song of a list, see [QueueAction.PLAY]. */
@Composable
fun PlayAllButton(onClick: () -> Unit, modifier: Modifier = Modifier) =
    IconAction(R.drawable.play_arrow, "Play all", onClick, modifier)

/** Plays every song of a list in a random order, see [QueueAction.SHUFFLE]. */
@Composable
fun ShuffleButton(onClick: () -> Unit, modifier: Modifier = Modifier) =
    IconAction(R.drawable.shuffle, "Shuffle", onClick, modifier)

/** Makes a new playlist. */
@Composable
fun CreatePlaylistButton(onClick: () -> Unit, modifier: Modifier = Modifier) =
    IconAction(R.drawable.add, "Create playlist", onClick, modifier)

/** Opens one item of a list picked at random. */
@Composable
fun RandomButton(onClick: () -> Unit, modifier: Modifier = Modifier) =
    IconAction(R.drawable.casino, "Open one at random", onClick, modifier)

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
        IconAction(R.drawable.more_vert, "More", { expanded = true }, Modifier)
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            items { expanded = false }
        }
    }
}
