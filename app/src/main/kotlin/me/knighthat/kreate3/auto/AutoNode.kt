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

package me.knighthat.kreate3.auto

import me.knighthat.kreate3.auto.AutoNode.Companion.parse
import me.knighthat.kreate3.model.Origin
import me.knighthat.kreate3.ui.navigation.Tab
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * A place in the library Android Auto browses, see [AutoLibrary]. Auto gives back only the media
 * id of what the user taps, so the id says everything needed to find it again: [id] writes it,
 * [parse] reads it.
 */
internal sealed interface AutoNode {

    val id: String

    /** The top of the library, its children are the [Section]s. */
    data object Root : AutoNode {
        override val id = "$PREFIX$ROOT"
    }

    /** A tab, like the bottom bar of the home screens. */
    data class Section(val tab: Tab) : AutoNode {
        override val id get() = "$PREFIX$SECTION/${tab.name}"
    }

    /**
     * An album, artist or playlist of the [tab] it shows in.
     *
     * @property key what its songs are found by: the id at [origin], the database id of a
     * playlist made in the app
     */
    data class Collection(val tab: Tab, val origin: Origin, val key: String) : AutoNode {

        init {
            require(tab != Tab.SONGS) { "Songs have no collections" }
        }

        override val id get() = "$PREFIX$COLLECTION/${tab.name}/${origin.name}/${encode(key)}"
    }

    /**
     * The song [songId] in [list], a [Section] of songs or a [Collection]. Tapped, it plays with
     * the songs of [list] below it.
     */
    data class Song(val list: AutoNode, val songId: String) : AutoNode {

        init {
            require(list is Collection || list == Section(Tab.SONGS)) { "$list has no songs" }
        }

        override val id get() = "$PREFIX$SONG/${encode(songId)}/${list.id}"
    }

    companion object {

        // Song ids of the sources never start with it, the queue's items are told apart by it
        private const val PREFIX = "auto:"
        private const val ROOT = "root"
        private const val SECTION = "tab"
        private const val COLLECTION = "item"
        private const val SONG = "song"

        /** The node [id] stands for, `null` when it isn't one. */
        fun parse(id: String): AutoNode? {
            if (!id.startsWith(PREFIX)) return null
            val path = id.removePrefix(PREFIX)
            val kind = path.substringBefore('/')
            val rest = path.substringAfter('/', missingDelimiterValue = "")

            return runCatching {
                when (kind) {
                    ROOT -> Root.takeIf { rest.isEmpty() }

                    SECTION -> tabOf(rest)?.let(::Section)

                    COLLECTION -> {
                        val parts = rest.split('/')
                        if (parts.size != 3) return null
                        val tab = tabOf(parts[0]) ?: return null
                        val origin = Origin.entries.firstOrNull { it.name == parts[1] }
                            ?: return null
                        Collection(tab, origin, decode(parts[2]))
                    }

                    SONG -> {
                        val songId = decode(rest.substringBefore('/'))
                        val list = parse(rest.substringAfter('/', missingDelimiterValue = ""))
                            ?: return null
                        Song(list, songId)
                    }

                    else -> null
                }
            }.getOrNull()
        }

        private fun tabOf(name: String): Tab? = Tab.entries.firstOrNull { it.name == name }

        // Ids may hold '/', encoded they don't
        private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

        private fun decode(value: String): String = URLDecoder.decode(value, "UTF-8")
    }
}
