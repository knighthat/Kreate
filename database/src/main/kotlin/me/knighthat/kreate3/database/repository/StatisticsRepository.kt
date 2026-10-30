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

package me.knighthat.kreate3.database.repository

import kotlinx.coroutines.flow.Flow
import me.knighthat.kreate3.database.model.ListeningSummary
import me.knighthat.kreate3.database.model.OriginTime
import me.knighthat.kreate3.database.model.PlayPeriod
import me.knighthat.kreate3.database.model.PlaybackHistory
import me.knighthat.kreate3.database.model.TopAlbum
import me.knighthat.kreate3.database.model.TopArtist
import me.knighthat.kreate3.database.model.TopSong
import me.knighthat.kreate3.database.model.UserSongView

/**
 * What the active user's history adds up to. Every function follows changes, counts only the
 * plays in [PlayPeriod] (all of them for `null`) and, like `HistoryRepository.plays`, leaves out
 * disliked songs and songs no longer in the library.
 */
interface StatisticsRepository {

    fun summary(period: PlayPeriod?): Flow<ListeningSummary>

    /** The [limit] songs listened to the most, see [TopSong.listens]. */
    fun topSongs(period: PlayPeriod?, limit: Int): Flow<List<TopSong>>

    fun topAlbums(period: PlayPeriod?, limit: Int): Flow<List<TopAlbum>>

    fun topArtists(period: PlayPeriod?, limit: Int): Flow<List<TopArtist>>

    /**
     * The song listened to the longest of all time, its plays' time spent added up, right now.
     *
     * @param likedOnly whether to look only at the songs the user liked
     * @return `null` when no such song was played
     */
    suspend fun mostListened(likedOnly: Boolean = false): UserSongView?

    /** How long the songs of each origin were listened to, the longest first. */
    fun timeByOrigin(period: PlayPeriod?): Flow<List<OriginTime>>

    /** Every play of [songId], the oldest first. */
    fun songPlays(songId: String): Flow<List<PlaybackHistory>>

    /**
     * The album with the listens of all its songs added up, of all time. `null` while none of its
     * songs was played.
     */
    fun album(albumId: String): Flow<TopAlbum?>

    /** The [limit] songs of the album listened to the most, of all time. */
    fun albumSongs(albumId: String, limit: Int): Flow<List<TopSong>>

    /** Every play of the songs of the album, the oldest first. */
    fun albumPlays(albumId: String): Flow<List<PlaybackHistory>>

    /** Like [album] for the songs crediting the artist. */
    fun artist(artistId: String): Flow<TopArtist?>

    /** The [limit] songs crediting the artist listened to the most, of all time. */
    fun artistSongs(artistId: String, limit: Int): Flow<List<TopSong>>

    /** Every play of the songs crediting the artist, the oldest first. */
    fun artistPlays(artistId: String): Flow<List<PlaybackHistory>>
}
