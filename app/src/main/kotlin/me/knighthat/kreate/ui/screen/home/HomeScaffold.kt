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

package me.knighthat.kreate.ui.screen.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import me.knighthat.kreate.R

/**
 * The title large under the logo and the actions, shrinking into one row as [content] scrolls.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeTopBar(
    title: String,
    scrollBehavior: TopAppBarScrollBehavior,
    onSearch: () -> Unit,
    onProfile: () -> Unit,
    onSettings: () -> Unit,
) {
    LargeTopAppBar(
        title = { Text(title) },
        navigationIcon = {
            Icon(
                painter = painterResource(R.drawable.ic_splash),
                contentDescription = null,
                tint = IconButtonDefaults.iconButtonColors().contentColor,
                modifier = Modifier
                    .padding(start = 12.dp)
                    .size(36.dp)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        role = Role.Button,
                        onClick = {

                        }
                    ),
            )
        },
        actions = {
            IconButton(onClick = onSearch) {
                Icon(painterResource(R.drawable.search), contentDescription = "Search")
            }
            IconButton(onClick = onProfile) {
                Icon(painterResource(R.drawable.person), contentDescription = "Profile")
            }
            IconButton(onClick = onSettings) {
                Icon(painterResource(R.drawable.settings), contentDescription = "Settings")
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surfaceDim,
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceDim,
        ),
        scrollBehavior = scrollBehavior,
    )
}

/**
 * The frame of a home tab: a large top bar named [title] that collapses when the scrollable
 * [content] is scrolled.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScaffold(
    title: String,
    modifier: Modifier = Modifier,
    onSearch: () -> Unit = {},
    onProfile: () -> Unit = {},
    onSettings: () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        topBar = { HomeTopBar(title, scrollBehavior, onSearch, onProfile, onSettings) },
        containerColor = MaterialTheme.colorScheme.surfaceDim,
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        content = content,
    )
}
