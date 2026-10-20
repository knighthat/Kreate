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
import me.knighthat.kreate3.constant.ContrastLevel
import me.knighthat.kreate3.constant.ThemeMode
import me.knighthat.kreate3.model.Origin
import me.knighthat.kreate3.ui.theme.KreateTheme

@Composable
private fun Items() {
    val song = previewItem(
        "A song with a rather long title that does not fit",
        "Artist · Album",
        215_000,
        Origin.LOCAL,
        explicit = true
    )
    val album = previewItem("Album", "Artist · 2020", origin = Origin.LOCAL, explicit = true)
    val artist = previewItem("Artist", "12 songs", origin = Origin.LOCAL)
    val playlist = previewItem("Road trip", "Monthly · On this device", origin = Origin.LOCAL)

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
