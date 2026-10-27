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

package me.knighthat.kreate3.playback

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheSpan
import androidx.media3.datasource.cache.ContentMetadataMutations
import java.io.File

/**
 * [cache] for a reader that must never change it, like playback reading the downloads: anything
 * that would write, drop or release it throws. Holding a part of it while reading, which the
 * read takes and gives back, stays allowed.
 */
@OptIn(UnstableApi::class)
internal class ReadOnlyCache(private val cache: Cache) : Cache by cache {

    override fun release() = refuse()

    override fun startFile(key: String, position: Long, length: Long): File = refuse()

    override fun commitFile(file: File, length: Long) = refuse()

    override fun removeResource(key: String) = refuse()

    override fun removeSpan(span: CacheSpan) = refuse()

    override fun applyContentMetadataMutations(key: String, mutations: ContentMetadataMutations) =
        refuse()

    private fun refuse(): Nothing = throw UnsupportedOperationException("The cache is read only")
}
