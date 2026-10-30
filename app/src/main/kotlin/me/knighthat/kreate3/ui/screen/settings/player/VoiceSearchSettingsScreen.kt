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

package me.knighthat.kreate3.ui.screen.settings.player

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.knighthat.kreate3.R
import me.knighthat.kreate3.auto.voiceSearchOrigins
import me.knighthat.kreate3.model.Origin
import me.knighthat.kreate3.preferences.Preferences
import me.knighthat.kreate3.preferences.collectAsStateWithLifecycle
import me.knighthat.kreate3.ui.component.PreviewTheme
import me.knighthat.kreate3.ui.component.ScreenPreviews
import me.knighthat.kreate3.ui.component.sourceRes
import me.knighthat.kreate3.ui.screen.settings.DISABLED_ALPHA
import me.knighthat.kreate3.ui.screen.settings.SettingsPage
import sh.calvin.reorderable.ReorderableColumn

/**
 * Where a voice request ("play … on Kreate") searches: the origins in the order they are asked,
 * each one on or skipped.
 */
@Composable
fun VoiceSearchSettingsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val order by Preferences.AUTO_VOICE_SEARCH_ORDER.collectAsStateWithLifecycle()
    val skipped by Preferences.AUTO_VOICE_SEARCH_SKIPPED.collectAsStateWithLifecycle()
    val openSubsonicOn by Preferences.OPEN_SUBSONIC_ENABLED.collectAsStateWithLifecycle()

    VoiceSearchSettingsContent(
        origins = voiceSearchOrigins(order, skipped),
        unavailable = if (openSubsonicOn) emptySet() else setOf(Origin.OPEN_SUBSONIC),
        onMove = { from, to ->
            // Read when the drag ends, a drag keeps the callbacks it started with
            val origins = voiceSearchOrigins(
                Preferences.AUTO_VOICE_SEARCH_ORDER.value,
                Preferences.AUTO_VOICE_SEARCH_SKIPPED.value,
            ).map { it.first }.toMutableList()
            origins.add(to, origins.removeAt(from))
            Preferences.AUTO_VOICE_SEARCH_ORDER.update(origins.map { it.name })
        },
        onToggle = { origin, searched ->
            val now = Preferences.AUTO_VOICE_SEARCH_SKIPPED.value
            Preferences.AUTO_VOICE_SEARCH_SKIPPED.update(
                if (searched) now - origin.name else now + origin.name
            )
        },
        onBack = onBack,
        modifier = modifier,
    )
}

/**
 * @param origins every origin in the order it is searched, with whether it is
 * @param unavailable origins whose source is off in the Network settings: never searched, their
 * row is dimmed and can't be moved or switched
 * @param onMove moves the origin at the first index to the second
 * @param onToggle turns the search of an origin on or off
 */
@Composable
private fun VoiceSearchSettingsContent(
    origins: List<Pair<Origin, Boolean>>,
    unavailable: Set<Origin>,
    onMove: (Int, Int) -> Unit,
    onToggle: (Origin, Boolean) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) = SettingsPage(
    title = stringResource(R.string.screen_title_voice_search),
    onBack = onBack,
    modifier = modifier,
) {
    Text(
        text = stringResource(
            R.string.screen_voice_search_hint_order,
            stringResource(R.string.app_name),
        ),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )

    val haptics = LocalHapticFeedback.current
    ReorderableColumn(
        list = origins,
        onSettle = onMove,
        onMove = { haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick) },
    ) { _, (origin, searched), isDragging ->
        key(origin) {
            ReorderableItem {
                val enabled = origin !in unavailable
                val elevation by animateDpAsState(if (isDragging) 4.dp else 0.dp, label = "lift")
                Surface(shadowElevation = elevation) {
                    ListItem(
                        modifier = if (enabled) Modifier else Modifier.alpha(DISABLED_ALPHA),
                        headlineContent = { Text(stringResource(origin.sourceRes)) },
                        leadingContent = {
                            Icon(
                                painterResource(R.drawable.drag_handle),
                                contentDescription = stringResource(R.string.semantic_drag_to_move),
                                modifier = Modifier.draggableHandle(enabled = enabled),
                            )
                        },
                        trailingContent = {
                            Switch(
                                checked = searched && enabled,
                                onCheckedChange = { onToggle(origin, it) },
                                enabled = enabled,
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                    )
                }
            }
        }
    }
}

@ScreenPreviews
@Composable
private fun VoiceSearchSettingsPreview() = PreviewTheme {
    VoiceSearchSettingsContent(
        origins = listOf(
            Origin.LOCAL to true,
            Origin.OPEN_SUBSONIC to true,
            Origin.YOUTUBE to false,
        ),
        unavailable = setOf(Origin.OPEN_SUBSONIC),
        onMove = { _, _ -> },
        onToggle = { _, _ -> },
        onBack = {},
    )
}
