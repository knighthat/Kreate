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

import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import me.knighthat.kreate3.ui.navigation.Tab

/**
 * One button for each [Tab], the home screens' bar and the search results' tabs. With
 * [LocalIconOnly], only the icons show.
 */
@Composable
fun TabBar(
    selected: Tab,
    onSelect: (Tab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val iconOnly = LocalIconOnly.current
    NavigationBar(modifier) {
        Tab.entries.forEach {
            NavigationBarItem(
                selected = it == selected,
                onClick = { onSelect(it) },
                icon = {
                    // Without the text, the icon has to name the tab
                    Icon(
                        painterResource(it.icon),
                        contentDescription = if (iconOnly) stringResource(it.title) else null,
                    )
                },
                label = if (iconOnly) null else ({ Text(stringResource(it.title)) }),
            )
        }
    }
}
