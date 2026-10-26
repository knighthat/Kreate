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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * The heading of a group of items inside a list. With [onSeeMore], an arrow at the end of the line
 * opens every item of the group, give it only when the group shows some of them.
 */
@Composable
fun SectionTitle(title: String, modifier: Modifier = Modifier, onSeeMore: (() -> Unit)? = null) {
    if (onSeeMore == null) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
        return
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        SeeMoreButton(onSeeMore)
    }
}

//<editor-fold defaultstate="collapsed" desc="Previews">
@ComponentPreviews
@Composable
private fun SectionTitlePreview() = PreviewTheme {
    Surface {
        Column(modifier = Modifier.width(360.dp)) {
            SectionTitle("Recently played")
            SectionTitle("Albums", onSeeMore = {})
            SectionTitle("A title that is far too long to fit on one line", onSeeMore = {})
        }
    }
}
//</editor-fold>
