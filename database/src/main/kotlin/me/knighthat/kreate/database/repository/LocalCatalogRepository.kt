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

package me.knighthat.kreate.database.repository

import me.knighthat.kreate.database.model.LocalSong

/**
 * Keeps the songs found on the device in the shared catalog.
 */
fun interface LocalCatalogRepository {

    /**
     * Brings the local part of the catalog up to date with [songs], in one transaction. New songs,
     * albums and artists are added and changed ones are updated.
     *
     * Every song, album and artist found is also added to the library of the active user, unless
     * it is there already. This includes items the catalog had before. An item the user removed
     * from the library is added again by the next scan. Waits up to 10 seconds for an active user
     * and throws [IllegalStateException] if there is none, so the caller can retry.
     *
     * Nothing is deleted. A song that is no longer in [songs] is kept with the time it went
     * missing in `unavailableSince`, and gets `null` again when a later scan finds it. This keeps
     * likes, playlists and history for songs on storage that is only unplugged for a while.
     *
     * Does nothing for an empty list, a failed scan looks the same as an empty device and must
     * not hide the whole catalog.
     */
    suspend fun sync(songs: List<LocalSong>)
}
