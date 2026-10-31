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

package me.knighthat.kreate3.ui.screen.settings.about

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.knighthat.kreate3.R
import me.knighthat.kreate3.ui.component.PreviewTheme
import me.knighthat.kreate3.ui.component.ScreenPreviews
import me.knighthat.kreate3.ui.component.SearchButton
import me.knighthat.kreate3.ui.component.rememberOpenLink
import me.knighthat.kreate3.ui.screen.detail.DetailScaffold
import me.knighthat.kreate3.ui.screen.settings.SettingsSection
import org.koin.compose.viewmodel.koinViewModel

/**
 * The libraries the app is built with, a row each with its author and licenses; a tap shows the
 * license texts.
 */
@Composable
fun OpenSourceLicensesScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OpenSourceLicensesViewModel = koinViewModel(),
) {
    val shown by viewModel.shown.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()

    OpenSourceLicensesContent(
        libraries = shown,
        query = query,
        onSearch = viewModel::search,
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
private fun OpenSourceLicensesContent(
    libraries: List<LibraryRow>?,
    query: String,
    onSearch: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var searching by rememberSaveable { mutableStateOf(query.isNotEmpty()) }
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    // The field is the list's first item, open it where it can be seen
    LaunchedEffect(searching) { if (searching) listState.scrollToItem(0) }
    val open = openId?.let { id -> libraries?.firstOrNull { it.id == id } }
    if (open != null) LicenseSheet(open, onDismiss = { openId = null })

    DetailScaffold(
        title = stringResource(R.string.screen_title_open_source_licenses),
        onBack = onBack,
        modifier = modifier,
        actions = {
            SearchButton({
                // Closing the field shows every library again
                if (searching) onSearch("")
                searching = !searching
            })
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize(), listState, contentPadding = padding) {
            if (searching)
                item(key = "search") { SearchField(query, onSearch) }
            if (libraries != null && libraries.isEmpty())
                item(key = "no match") {
                    Text(
                        text = stringResource(
                            R.string.screen_open_source_licenses_status_no_match,
                            stringResource(R.string.quoted, query.trim()),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            items(libraries.orEmpty(), key = LibraryRow::id) { library ->
                LibraryItem(library, onClick = { openId = library.id })
            }
        }
    }
}

@Composable
private fun SearchField(query: String, onSearch: (String) -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    OutlinedTextField(
        value = query,
        onValueChange = onSearch,
        placeholder = { Text(stringResource(R.string.screen_open_source_licenses_hint_search)) },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .focusRequester(focus),
    )
}

/**
 * A library's name, its author and a label for each of its licenses.
 */
@Composable
private fun LibraryItem(library: LibraryRow, onClick: () -> Unit) =
    ListItem(
        headlineContent = { Text(library.name) },
        supportingContent = {
            Column {
                library.author?.let { Text(it) }
                if (library.licenses.isNotEmpty())
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(top = 4.dp),
                    ) { library.licenses.forEach { LicenseLabel(it.name) } }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick),
    )

@Composable
private fun LicenseLabel(name: String) =
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }

/**
 * The texts of [library]'s licenses, and a button opening its website when it has one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LicenseSheet(library: LibraryRow, onDismiss: () -> Unit) {
    val openLink = rememberOpenLink()

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Text(
                text = library.name,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            library.author?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            library.website?.let { website ->
                TextButton(
                    onClick = { openLink(website) },
                    modifier = Modifier.padding(horizontal = 4.dp),
                ) { Text(stringResource(R.string.action_open_website)) }
            }
            library.licenses.forEach { license ->
                SettingsSection(license.name)
                license.content?.let {
                    Text(
                        text = it.trim(),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@ScreenPreviews
@Composable
private fun OpenSourceLicensesPreview() = PreviewTheme {
    val apache = LicenseText("Apache License 2.0", null)
    val gpl = LicenseText("GNU General Public License v3.0", null)
    OpenSourceLicensesContent(
        libraries = listOf(
            LibraryRow(
                "media3",
                "ExoPlayer",
                "The Android Open Source Project",
                listOf(apache),
                null
            ),
            LibraryRow("coil", "coil-compose", "Coil Contributors", listOf(apache), null),
            LibraryRow("innertubex", "InnerTubeX", null, listOf(gpl), null),
            LibraryRow("koin", "Koin", "Kotzilla", listOf(apache), null),
            LibraryRow("vico", "Vico", "Patryk Goworowski, Patrick Michalik", listOf(apache), null),
        ),
        query = "",
        onSearch = {},
        onBack = {},
    )
}
