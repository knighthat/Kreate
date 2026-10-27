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

package me.knighthat.kreate3.source

import android.content.Context
import android.net.Uri
import coil3.request.Options
import coil3.size.Size
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

/** Covers kept as files: what kind a song's own cover is, and the size drawn. */
@RunWith(RobolectricTestRunner::class)
class CoverFilesTest {

    private val context: Context = RuntimeEnvironment.getApplication()
    private val files = CoverFiles(context)
    private val large = files.downloaded("abc", LARGE_COVER)

    @Test
    fun coversAreToldApartByFolder() {
        assertEquals(CoverKind.NONE, files.kindOf(null))
        assertEquals(CoverKind.DOWNLOADED, files.kindOf(Uri.fromFile(large)))
        assertEquals(
            CoverKind.CUSTOM,
            files.kindOf(Uri.fromFile(File(files.custom("default"), "x.jpg"))),
        )
        // Only in a profile's folder
        assertEquals(
            CoverKind.OTHER,
            files.kindOf(Uri.fromFile(File(context.noBackupFilesDir, "custom_thumbnail/x.jpg"))),
        )
        assertEquals(CoverKind.OTHER, files.kindOf(Uri.fromFile(File(context.filesDir, "x.jpg"))))
        assertEquals(CoverKind.OTHER, files.kindOf(Uri.parse("https://example.com/c.jpg")))
    }

    @Test
    fun smallCopyIsDrawnWhereItIsEnough() {
        val mapper = CoverFiles.Mapper(files)
        val cover = Uri.fromFile(large)

        assertEquals(
            Uri.fromFile(files.downloaded("abc", SMALL_COVER)),
            mapper.map(cover, Options(context, size = Size(200, 200))),
        )
        assertNull(mapper.map(cover, Options(context, size = Size(1000, 1000))))
        assertNull(mapper.map(cover, Options(context, size = Size.ORIGINAL)))
        // Only downloaded covers have copies
        assertNull(
            mapper.map(
                Uri.fromFile(File(files.custom("default"), "x.jpg")),
                Options(context, size = Size(200, 200)),
            )
        )
    }
}
