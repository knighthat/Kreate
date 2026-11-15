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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuite
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteItem
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.knighthat.kreate3.ui.navigation.Tab

/** Narrowest window, in dp, whose home tabs get the rail instead of the bottom bar. */
private const val RAIL_MIN_WIDTH = 600

/**
 * Where the home tabs' buttons go in the current window: a bar at the bottom while it is narrow (a
 * phone held upright), a rail along its start once it is wide (a tablet, a phone turned sideways).
 * Material keeps the bar on a sideways phone, but there its height is what's short.
 */
@Composable
fun tabNavigationType(): NavigationSuiteType =
    if (currentWindowAdaptiveInfoV2().windowSizeClass.minWidthDp >= RAIL_MIN_WIDTH)
        NavigationSuiteType.NavigationRail
    else
        NavigationSuiteType.NavigationBar

/**
 * The colors of the home tabs' bar and rail: the rail takes the bar's container color, so the
 * buttons look the same in both.
 */
@Composable
fun tabNavigationColors() = NavigationSuiteDefaults.colors(
    navigationRailContainerColor = NavigationBarDefaults.containerColor,
)

/**
 * One button for each [Tab], for a navigation suite of [type] (see [tabNavigationType]). With
 * [LocalIconOnly], only the icons show.
 *
 * @param itemModifier added to a tab's button, like a hold gesture
 * @param iconOverlay drawn in a tab's icon, for a popup anchored to it
 */
@Composable
fun TabNavigationItems(
    selected: Tab,
    onSelect: (Tab) -> Unit,
    type: NavigationSuiteType,
    itemModifier: (Tab) -> Modifier = { Modifier },
    iconOverlay: @Composable (Tab) -> Unit = {},
) {
    val iconOnly = LocalIconOnly.current
    Tab.entries.forEach {
        NavigationSuiteItem(
            selected = it == selected,
            onClick = { onSelect(it) },
            icon = {
                // Without the text, the icon has to name the tab
                Icon(
                    painterResource(it.icon),
                    contentDescription = if (iconOnly) stringResource(it.title) else null,
                )
                iconOverlay(it)
            },
            label = if (iconOnly) null else ({ Text(stringResource(it.title)) }),
            modifier = itemModifier(it),
            navigationSuiteType = type,
        )
    }
}

/**
 * One button for each [Tab] in a bar, the search results' tabs. Its buttons look like the home
 * tabs' bar ([TabNavigationItems]).
 */
@Composable
fun TabBar(selected: Tab, onSelect: (Tab) -> Unit, modifier: Modifier = Modifier) =
    NavigationSuite(
        navigationSuiteType = NavigationSuiteType.NavigationBar,
        colors = tabNavigationColors(),
        modifier = modifier,
    ) {
        TabNavigationItems(selected, onSelect, NavigationSuiteType.NavigationBar)
    }

//<editor-fold defaultstate="collapsed" desc="Previews">
@ComponentPreviews
@Composable
private fun TabBarPreview() = PreviewTheme {
    Column(modifier = Modifier.width(360.dp)) {
        TabBar(selected = Tab.SONGS, onSelect = {})
        CompositionLocalProvider(LocalIconOnly provides true) {
            TabBar(selected = Tab.ALBUMS, onSelect = {})
        }
    }
}

@ComponentPreviews
@Composable
private fun TabRailPreview() = PreviewTheme {
    Row(modifier = Modifier.height(480.dp)) {
        listOf(false, true).forEach { iconOnly ->
            CompositionLocalProvider(LocalIconOnly provides iconOnly) {
                NavigationSuite(
                    navigationSuiteType = NavigationSuiteType.NavigationRail,
                    colors = tabNavigationColors(),
                ) {
                    TabNavigationItems(
                        selected = Tab.ARTISTS,
                        onSelect = {},
                        type = NavigationSuiteType.NavigationRail,
                    )
                }
            }
        }
    }
}
//</editor-fold>
