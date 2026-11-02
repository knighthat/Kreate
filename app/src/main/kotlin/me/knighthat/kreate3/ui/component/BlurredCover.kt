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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest

/**
 * How blurred a copy of a cover is: the edge in pixels it is loaded at and the radius of the
 * real blur over it. Stretched to the window, few pixels blur it on every Android version, the
 * real blur only smooths it where there is one.
 */
internal data class Blur(val sampleSize: Int, val radius: Dp)

/**
 * The cover blurred as much as [blur] says, filling [modifier] with its sides cut. It tries
 * [models] in order until one loads.
 */
@Composable
internal fun BlurredCover(models: List<Any>, blur: Blur, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var failed by remember(models) { mutableIntStateOf(0) }
    val model = models.getOrNull(failed) ?: return
    val request = remember(model, blur.sampleSize) {
        ImageRequest.Builder(context)
            .data(model)
            .size(blur.sampleSize)
            // A key of its own, a tiny picture must not stand in for a sharper one
            .memoryCacheKey(memoryCacheKey(model)?.let { "blurred:${blur.sampleSize}:$it" })
            .build()
    }

    AsyncImage(
        model = request,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        onError = { failed++ },
        modifier = modifier.blur(blur.radius),
    )
}
