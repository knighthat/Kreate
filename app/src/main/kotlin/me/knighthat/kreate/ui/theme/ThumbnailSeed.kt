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

package me.knighthat.kreate.ui.theme

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.MediaItem
import coil3.Image
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.memory.MemoryCache
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.size.Size
import coil3.toBitmap
import com.materialkolor.quantize.QuantizerCelebi
import com.materialkolor.score.Score
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.knighthat.kreate.source.local.LocalCover
import me.knighthat.kreate.ui.component.localSongId
import me.knighthat.kreate.ui.component.thumbnailModel
import kotlin.math.roundToInt

private const val SAMPLE_SIZE = 48
private const val PALETTE_SIZE = 32

/**
 * The color to seed [me.knighthat.kreate.constant.ThemeMode.THUMBNAIL] with: the most fitting
 * color of the picture of [item]. It is [Color.Unspecified] until there is one, and the one of
 * the previous item stays while the next picture loads. Nothing is loaded while [enabled] is
 * `false`.
 *
 * The seed only lives in memory, it is worked out again for the next song.
 */
@Composable
fun rememberThumbnailSeed(item: MediaItem?, enabled: Boolean): Color {
    val context = LocalContext.current
    var seed by remember { mutableStateOf(Color.Unspecified) }

    LaunchedEffect(item?.mediaId, enabled) {
        if (!enabled) return@LaunchedEffect
        if (item == null) {
            seed = Color.Unspecified
            return@LaunchedEffect
        }

        val metadata = item.mediaMetadata
        val model = thumbnailModel(metadata.artworkUri, item.localSongId)
        seed = model?.let { extractSeed(context, it) } ?: Color.Unspecified
    }

    return seed
}

/**
 * The picture is most likely in memory already, the item that is playing shows it. It is found
 * by its key, which is what Coil caches it under. A request for the same picture would not use
 * that copy and decode it again, so a request is only made when there is no copy.
 */
private suspend fun extractSeed(context: Context, model: Any): Color? {
    val loader = SingletonImageLoader.get(context)
    val key = when (model) {
        is LocalCover -> model.cacheKey
        is Uri -> model.toString()
        else -> null
    }

    val image = key?.let { loader.memoryCache?.get(MemoryCache.Key(it))?.image }
        ?: load(context, loader, model)
        ?: return null

    return withContext(Dispatchers.Default) {
        // The picture can be much larger than needed. Drawing it small also gives a bitmap whose
        // pixels can be read, what the screens cache is often a hardware bitmap.
        val ratio = SAMPLE_SIZE.toFloat() / maxOf(image.width, image.height)
        val bitmap = image.toBitmap(
            width = maxOf(1, (image.width * ratio).roundToInt()),
            height = maxOf(1, (image.height * ratio).roundToInt()),
            config = Bitmap.Config.ARGB_8888,
        )
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

        val ranked = Score.score(
            QuantizerCelebi.quantize(pixels, PALETTE_SIZE),
            desired = 1,
            fallbackColorArgb = null,
        )
        ranked.firstOrNull()?.let(::Color)
    }
}

private suspend fun load(context: Context, loader: ImageLoader, model: Any): Image? {
    val request = ImageRequest.Builder(context)
        .data(model)
        .size(Size(SAMPLE_SIZE, SAMPLE_SIZE))
        .build()
    return (loader.execute(request) as? SuccessResult)?.image
}
