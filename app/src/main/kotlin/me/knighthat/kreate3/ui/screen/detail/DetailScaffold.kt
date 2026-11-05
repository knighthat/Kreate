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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.plus
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.net.toUri
import androidx.lifecycle.SavedStateHandle
import me.knighthat.kreate3.ui.component.BackButton
import me.knighthat.kreate3.ui.component.LocalTooltipSide
import me.knighthat.kreate3.ui.component.TooltipSide
import me.knighthat.kreate3.ui.navigation.OPENED_NAME
import me.knighthat.kreate3.ui.navigation.OPENED_PICTURE

/**
 * What the card or row an artist or album was opened from showed of it, see
 * [me.knighthat.kreate3.ui.navigation.artistRoute] and
 * [me.knighthat.kreate3.ui.navigation.albumRoute]. Its page shows it until it has its own.
 *
 * @property picture the picture as the card loaded it, the page's picture grows from it
 */
data class OpenedItem(val name: String?, val picture: Uri?)

/** The [OpenedItem] in the arguments of a screen, nothing when opened by id alone. */
internal fun SavedStateHandle.openedItem(): OpenedItem =
    OpenedItem(get<String>(OPENED_NAME), get<String>(OPENED_PICTURE)?.toUri())

/**
 * The frame of a detail screen: a small top bar with a back button, the [title] of the item and
 * its [actions] at the end, and the [bottomBar] and [snackbarHost] if there are any.
 *
 * @param contentPadding room [content] adds around its items on top of the bar's, e.g. so the last
 * one can scroll clear of what floats over the screen
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        topBar = {
            // Below its buttons, above them is the status bar
            CompositionLocalProvider(LocalTooltipSide provides TooltipSide.BELOW) {
                TopAppBar(
                    title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = {
                        BackButton(onBack)
                    },
                    actions = actions,
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceDim,
                    ),
                )
            }
        },
        bottomBar = bottomBar,
        snackbarHost = snackbarHost,
        containerColor = MaterialTheme.colorScheme.surfaceDim,
        modifier = modifier,
    ) { content(it + contentPadding) }
}
