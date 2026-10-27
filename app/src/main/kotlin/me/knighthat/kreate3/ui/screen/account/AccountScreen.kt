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

package me.knighthat.kreate3.ui.screen.account

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import me.knighthat.kreate3.ProfilesActivity
import me.knighthat.kreate3.R
import me.knighthat.kreate3.preferences.Preferences
import me.knighthat.kreate3.ui.component.PreviewTheme
import me.knighthat.kreate3.ui.component.ScreenPreviews
import me.knighthat.kreate3.ui.navigation.Disliked
import me.knighthat.kreate3.ui.navigation.Downloaded
import me.knighthat.kreate3.ui.navigation.History
import me.knighthat.kreate3.ui.navigation.Route
import me.knighthat.kreate3.ui.navigation.Statistics
import me.knighthat.kreate3.ui.screen.detail.DetailScaffold

/**
 * What the active profile has done and kept, and the way to another profile.
 *
 * @param onOpen called with the route of the page the user picked
 * @param contentPadding room the list adds around its rows on top of the bar's, e.g. so the last
 * one can scroll clear of what floats over the screen
 */
@Composable
fun AccountScreen(
    onBack: () -> Unit,
    onOpen: (Route) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val context = LocalContext.current

    AccountContent(
        onBack = onBack,
        onOpen = onOpen,
        onSwitchProfile = {
            context.startActivity(
                ProfilesActivity.intent(
                    context,
                    Preferences.THEME_MODE.value,
                    Preferences.CONTRAST_LEVEL.value
                )
            )
        },
        modifier = modifier,
        contentPadding = contentPadding,
    )
}

@Composable
private fun AccountContent(
    onBack: () -> Unit,
    onOpen: (Route) -> Unit,
    onSwitchProfile: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    DetailScaffold(
        title = stringResource(R.string.screen_title_account),
        onBack = onBack,
        modifier = modifier,
        contentPadding = contentPadding,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding),
        ) {
            AccountEntry(R.drawable.history, R.string.screen_title_history) { onOpen(History) }
            AccountEntry(R.drawable.insert_chart, R.string.screen_title_statistics) {
                onOpen(Statistics)
            }
            AccountEntry(R.drawable.thumb_down, R.string.screen_title_disliked) { onOpen(Disliked) }
            AccountEntry(R.drawable.downloaded, R.string.screen_title_downloaded) {
                onOpen(Downloaded)
            }
            AccountEntry(
                R.drawable.switch_account,
                R.string.screen_title_switch_profile,
                onSwitchProfile
            )
        }
    }
}

/** A row of [AccountScreen] that opens [title]. */
@Composable
private fun AccountEntry(@DrawableRes icon: Int, @StringRes title: Int, onClick: () -> Unit) =
    ListItem(
        headlineContent = { Text(stringResource(title)) },
        // The title already says what it opens
        leadingContent = { Icon(painterResource(icon), contentDescription = null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick),
    )

@ScreenPreviews
@Composable
private fun AccountPreview() = PreviewTheme {
    AccountContent(onBack = {}, onOpen = {}, onSwitchProfile = {})
}
