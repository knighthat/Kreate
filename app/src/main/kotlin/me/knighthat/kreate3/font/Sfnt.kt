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

package me.knighthat.kreate3.font

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile

/**
 * Reads the table directory of a font file (TrueType, OpenType, or a collection of them, whose
 * first font is read), enough to tell a font from any other file and a variable font from a static
 * one.
 */
internal object Sfnt {

    private const val TRUE_TYPE = 0x00010000
    private const val OPEN_TYPE = 0x4F54544F // "OTTO"
    private const val APPLE_TRUE_TYPE = 0x74727565 // "true"
    private const val COLLECTION = 0x74746366 // "ttcf"

    /** The most tables a directory can claim, a real font has a few dozen. */
    private const val MAX_TABLES = 512

    /** Whether [file] is a font. */
    fun isFont(file: File): Boolean = tables(file) != null

    /** Whether [file] is a font whose weight (and more) can be varied, it has an `fvar` table. */
    fun isVariable(file: File): Boolean = tables(file)?.contains("fvar") == true

    /**
     * The tags of the tables of [file]'s font, `null` when it isn't one or can't be read.
     */
    fun tables(file: File): Set<String>? = try {
        RandomAccessFile(file, "r").use { read ->
            var magic = read.readInt()
            if (magic == COLLECTION) {
                // Version and font count, then the offset of each font's directory
                read.seek(12)
                read.seek(read.readInt().toLong() and 0xFFFFFFFFL)
                magic = read.readInt()
            }
            if (magic != TRUE_TYPE && magic != OPEN_TYPE && magic != APPLE_TRUE_TYPE) {
                null
            } else {
                val count = read.readUnsignedShort()
                if (count == 0 || count > MAX_TABLES) {
                    null
                } else {
                    // Search hints, then a record each: tag, checksum, offset, length
                    read.skipBytes(6)
                    val tag = ByteArray(4)
                    buildSet {
                        repeat(count) {
                            read.readFully(tag)
                            add(String(tag, Charsets.ISO_8859_1))
                            read.skipBytes(12)
                        }
                    }
                }
            }
        }
    } catch (_: IOException) {
        null
    }
}
