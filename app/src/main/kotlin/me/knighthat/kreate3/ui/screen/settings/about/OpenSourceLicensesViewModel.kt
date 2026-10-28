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

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.entity.Library
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import me.knighthat.kreate3.R

/** One license of a library: its [name] and full text, `null` when the build found none. */
@Immutable
class LicenseText(val name: String, val content: String?)

/**
 * A library the app is built with.
 *
 * @property author its developers, or the organization behind it when none is named
 */
@Immutable
class LibraryRow(
    val id: String,
    val name: String,
    val author: String?,
    val licenses: List<LicenseText>,
    val website: String?,
)

/**
 * The libraries the app is built with, by name, as the AboutLibraries plugin found them in their
 * Maven POMs at build time.
 */
class OpenSourceLicensesViewModel(private val context: Context) : ViewModel() {

    private val _query = MutableStateFlow("")

    /** What the list is searched for, as typed. */
    val query: StateFlow<String> = _query.asStateFlow()

    /**
     * The libraries whose name or author holds the [query], every library while it's blank,
     * `null` until they're read.
     */
    val shown: StateFlow<List<LibraryRow>?> =
        combine(flow { emit(load()) }, _query) { libraries, query ->
            val typed = query.trim()
            if (typed.isEmpty()) libraries
            else libraries.filter { typed in it.name || it.author?.contains(typed) == true }
        }.flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun search(query: String) {
        _query.value = query
    }

    private suspend fun load(): List<LibraryRow> {
        val json = withContext(Dispatchers.IO) {
            context.resources.openRawResource(R.raw.aboutlibraries)
                .bufferedReader()
                .use { it.readText() }
        }
        return withContext(Dispatchers.Default) {
            Libs.Builder()
                .withJson(json)
                .build()
                .libraries
                .map { it.toRow() }
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
        }
    }

    private fun Library.toRow(): LibraryRow =
        LibraryRow(
            id = uniqueId,
            name = name,
            author = developers.mapNotNull { it.name?.takeIf(String::isNotBlank) }
                .distinct()
                .joinToString()
                .ifEmpty { organization?.name },
            licenses = licenses.map { LicenseText(it.name, it.licenseContent) },
            website = website,
        )
}
