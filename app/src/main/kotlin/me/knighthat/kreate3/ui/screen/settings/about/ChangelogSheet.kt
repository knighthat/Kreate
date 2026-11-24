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

import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import me.knighthat.kreate3.R
import me.knighthat.kreate3.ui.screen.settings.SettingsSection

/**
 * The notes of the stable release [version], as `.claude/scripts/bump.sh` writes them for each
 * platform flavor to `res/raw/changelog.json` (English only, never translated). `main` holds an
 * empty one for a flavor that has none yet.
 */
@Serializable
data class Changelog(
    val version: String = "",
    val adds: List<String> = emptyList(),
    val removes: List<String> = emptyList(),
    val changes: List<String> = emptyList(),
    val fixes: List<String> = emptyList(),
) {
    val isEmpty: Boolean
        get() = adds.isEmpty() && removes.isEmpty() && changes.isEmpty() && fixes.isEmpty()

    companion object {
        private val JSON = Json { ignoreUnknownKeys = true }

        /** The flavor's notes read from `R.raw.changelog`, off the main thread. */
        suspend fun read(resources: Resources): Changelog = withContext(Dispatchers.IO) {
            resources.openRawResource(R.raw.changelog)
                .bufferedReader()
                .use { JSON.decodeFromString(serializer(), it.readText()) }
        }
    }
}

/**
 * The notes of [changelog]'s release, its lists under Added, Removed, Changed and Fixed; an empty
 * list leaves its heading out.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangelogSheet(changelog: Changelog, onDismiss: () -> Unit) =
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Text(
                text = stringResource(R.string.dialog_title_changelog, changelog.version),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            ChangelogSection(R.string.section_header_changelog_added, changelog.adds)
            ChangelogSection(R.string.section_header_changelog_removed, changelog.removes)
            ChangelogSection(R.string.section_header_changelog_changed, changelog.changes)
            ChangelogSection(R.string.section_header_changelog_fixed, changelog.fixes)
            Spacer(Modifier.height(16.dp))
        }
    }

@Composable
private fun ChangelogSection(@StringRes title: Int, items: List<String>) {
    if (items.isEmpty()) return

    SettingsSection(stringResource(title))
    // The bullet sits in its own column so wrapped lines start under the text, and a screen reader
    // reads bullet and text as one item
    items.forEach {
        Row(
            Modifier
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .semantics(mergeDescendants = true) {}
        ) {
            Text(
                text = "•",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.width(16.dp),
            )
            Text(text = it, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
