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

import android.os.Bundle
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaConstants
import me.knighthat.kreate3.constant.ContrastLevel
import me.knighthat.kreate3.constant.ThemeMode
import me.knighthat.kreate3.database.model.Origin
import me.knighthat.kreate3.ui.theme.KreateTheme

@OptIn(UnstableApi::class)
private fun sample(
    title: String,
    subtitle: String,
    durationMs: Long? = null,
    local: Boolean = false,
    explicit: Boolean = false,
) =
    MediaItem.Builder()
        .setMediaId(title)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setDurationMs(durationMs)
                .setExtras(Bundle().apply {
                    if (local) putString(EXTRA_ORIGIN, Origin.LOCAL.name)
                    if (explicit) putLong(
                        MediaConstants.EXTRAS_KEY_IS_EXPLICIT,
                        MediaConstants.EXTRAS_VALUE_ATTRIBUTE_PRESENT
                    )
                })
                .build()
        )
        .build()

@Composable
private fun Items() {
    val song = sample(
        "A song with a rather long title that does not fit",
        "Artist · Album",
        215_000,
        local = true,
        explicit = true
    )
    val album = sample("Album", "Artist · 2020", local = true, explicit = true)
    val artist = sample("Artist", "12 songs", local = true)
    val playlist = sample("Road trip", "Monthly · On this device", local = true)

    Surface {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SongItem(song, onClick = {})
            PlaylistItem(playlist, onClick = {})
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                AlbumItem(album, onClick = {}, modifier = Modifier.weight(1f))
                ArtistItem(artist, onClick = {}, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Preview(showBackground = true, name = "Light")
@Composable
private fun ItemsLightPreview() {
    KreateTheme(ThemeMode.LIGHT, ContrastLevel.NORMAL, Color.Unspecified) { Items() }
}

@Preview(showBackground = true, name = "Dark")
@Composable
private fun ItemsDarkPreview() {
    KreateTheme(ThemeMode.DARK, ContrastLevel.NORMAL, Color.Unspecified) { Items() }
}
