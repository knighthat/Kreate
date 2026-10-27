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

import androidx.room.RoomRawQuery
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import me.knighthat.kreate3.database.ActiveUser
import me.knighthat.kreate3.database.dao.PlaybackHistoryDao
import me.knighthat.kreate3.database.entity.PlaybackHistoryEntity
import me.knighthat.kreate3.database.model.ListeningSummary
import me.knighthat.kreate3.database.model.OriginTime
import me.knighthat.kreate3.database.model.PlayPeriod
import me.knighthat.kreate3.database.model.PlaybackHistory
import me.knighthat.kreate3.database.model.TopAlbum
import me.knighthat.kreate3.database.model.TopArtist
import me.knighthat.kreate3.database.model.TopSong
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Room backed implementation of [StatisticsRepository].
 */
internal class StatisticsRepositoryImpl(
    private val playbackHistoryDao: PlaybackHistoryDao,
    activeUser: ActiveUser,
) : StatisticsRepository {

    private val scope = UserScope(activeUser)

    override fun summary(period: PlayPeriod?): Flow<ListeningSummary> =
        scope.observe(ListeningSummary(0, 0, 0, Duration.ZERO)) { userId ->
            val plays = playsIn(period)
            playbackHistoryDao.observeListeningSummary(
                query(
                    userId,
                    "SELECT COUNT(*) AS plays, COUNT(DISTINCT h.song_id) AS songs, " +
                            "COALESCE(SUM(h.time_spent), 0) AS time_spent, " +
                            "(SELECT COUNT(DISTINCT m.artist_id) FROM song_artist_map m " +
                            "WHERE m.song_id IN (SELECT h.song_id $plays)) AS artists " +
                            plays
                )
            ).map { ListeningSummary(it.plays, it.songs, it.artists, it.timeSpent.seconds) }
        }

    override fun topSongs(period: PlayPeriod?, limit: Int): Flow<List<TopSong>> =
        songs(period, Of.All, limit)

    override fun topAlbums(period: PlayPeriod?, limit: Int): Flow<List<TopAlbum>> =
        albums(period, Of.All, limit)

    override fun topArtists(period: PlayPeriod?, limit: Int): Flow<List<TopArtist>> =
        artists(period, Of.All, limit)

    override fun timeByOrigin(period: PlayPeriod?): Flow<List<OriginTime>> =
        scope.observe(emptyList()) { userId ->
            playbackHistoryDao.observeOriginTimes(
                query(
                    userId,
                    "SELECT v.origin AS origin, SUM(h.time_spent) AS time_spent " +
                            "${playsIn(period)} GROUP BY v.origin ORDER BY time_spent DESC"
                )
            ).map { rows -> rows.map { OriginTime(it.origin, it.timeSpent.seconds) } }
        }

    override fun songPlays(songId: String): Flow<List<PlaybackHistory>> =
        scope.observe(emptyList()) { userId ->
            playbackHistoryDao.songPlays(userId, songId)
                .map { it.map(PlaybackHistoryEntity::toModel) }
        }

    override fun album(albumId: String): Flow<TopAlbum?> =
        albums(null, Of.Album(albumId), 1).map { it.firstOrNull() }

    override fun albumSongs(albumId: String, limit: Int): Flow<List<TopSong>> =
        songs(null, Of.Album(albumId), limit)

    override fun albumPlays(albumId: String): Flow<List<PlaybackHistory>> =
        plays(Of.Album(albumId))

    override fun artist(artistId: String): Flow<TopArtist?> =
        artists(null, Of.Artist(artistId), 1).map { it.firstOrNull() }

    override fun artistSongs(artistId: String, limit: Int): Flow<List<TopSong>> =
        songs(null, Of.Artist(artistId), limit)

    override fun artistPlays(artistId: String): Flow<List<PlaybackHistory>> =
        plays(Of.Artist(artistId))

    private fun songs(period: PlayPeriod?, of: Of, limit: Int): Flow<List<TopSong>> =
        scope.observe(emptyList()) { userId ->
            playbackHistoryDao.observeTopSongs(
                query(
                    userId,
                    "${
                        perSong(
                            period,
                            of
                        )
                    } SELECT v.*, ps.listens AS listens, ps.time_spent AS time_spent " +
                            "FROM per_song ps " +
                            "JOIN user_song_view v ON v.user_id = ?1 AND v.song_id = ps.song_id " +
                            "ORDER BY ps.listens DESC, ps.time_spent DESC, ps.song_id LIMIT $limit",
                    of.id,
                )
            ).map { rows ->
                rows.map { TopSong(it.song.toModel(), it.listens, it.timeSpent.seconds) }
            }
        }

    /** Albums of the plays [of] allows, so for [Of.Artist] the albums of their songs. */
    private fun albums(period: PlayPeriod?, of: Of, limit: Int): Flow<List<TopAlbum>> =
        scope.observe(emptyList()) { userId ->
            // The columns of user_album_view, for albums the user may keep nothing about
            playbackHistoryDao.observeTopAlbums(
                query(
                    userId,
                    "${perSong(period, of)} SELECT ?1 AS user_id, a.id AS album_id, " +
                            "a.origin AS origin, COALESCE(ual.name, a.name) AS name, " +
                            "COALESCE(ual.artists, a.artists) AS artists, " +
                            "COALESCE(ual.thumbnail, a.thumbnail) AS thumbnail, a.year AS year, " +
                            "a.url AS url, a.is_explicit AS is_explicit, " +
                            "(SELECT COUNT(*) FROM song s WHERE s.album_id = a.id) AS song_count, " +
                            "ual.bookmarked_at AS bookmarked_at, " +
                            "SUM(ps.listens) AS listens, SUM(ps.time_spent) AS time_spent " +
                            "FROM per_song ps JOIN album a ON a.id = ps.album_id " +
                            "LEFT JOIN user_album ual ON ual.album_id = a.id AND ual.user_id = ?1 " +
                            "GROUP BY a.id ORDER BY listens DESC, time_spent DESC, a.id LIMIT $limit",
                    of.id,
                )
            ).map { rows ->
                rows.map { TopAlbum(it.album.toModel(), it.listens, it.timeSpent.seconds) }
            }
        }

    /**
     * Artists credited on the songs of the plays [of] allows. For [Of.Artist] only that artist
     * is kept, not the others credited with them.
     */
    private fun artists(period: PlayPeriod?, of: Of, limit: Int): Flow<List<TopArtist>> =
        scope.observe(emptyList()) { userId ->
            val only = if (of is Of.Artist) "AND ar.id = ?2" else ""
            // The columns of user_artist_view, for artists the user may keep nothing about
            playbackHistoryDao.observeTopArtists(
                query(
                    userId,
                    "${perSong(period, of)} SELECT ?1 AS user_id, ar.id AS artist_id, " +
                            "ar.origin AS origin, COALESCE(ua.name, ar.name) AS name, " +
                            "COALESCE(ua.thumbnail, ar.thumbnail) AS thumbnail, " +
                            "(SELECT COUNT(*) FROM song_artist_map c WHERE c.artist_id = ar.id) AS song_count, " +
                            "ua.follow_since AS follow_since, " +
                            "SUM(ps.listens) AS listens, SUM(ps.time_spent) AS time_spent " +
                            "FROM per_song ps " +
                            "JOIN song_artist_map m ON m.song_id = ps.song_id " +
                            "JOIN artist ar ON ar.id = m.artist_id $only " +
                            "LEFT JOIN user_artist ua ON ua.artist_id = ar.id AND ua.user_id = ?1 " +
                            "GROUP BY ar.id ORDER BY listens DESC, time_spent DESC, ar.id LIMIT $limit",
                    of.id,
                )
            ).map { rows ->
                rows.map { TopArtist(it.artist.toModel(), it.listens, it.timeSpent.seconds) }
            }
        }

    /** Every play [of] allows, the oldest first. */
    private fun plays(of: Of): Flow<List<PlaybackHistory>> =
        scope.observe(emptyList()) { userId ->
            playbackHistoryDao.observePlays(
                query(userId, "SELECT h.* ${playsIn(null, of)} ORDER BY h.created_at, h.id", of.id)
            ).map { it.map(PlaybackHistoryEntity::toModel) }
        }

    /** [sql] with the user as its `?1` and [id], when given, as its `?2`. */
    private fun query(userId: String, sql: String, id: String? = null) =
        RoomRawQuery(
            sql = sql,
            onBindStatement = {
                it.bindText(1, userId)
                if (id != null) it.bindText(2, id)
            },
        )

    /** Which plays count: all of them, or those of the songs of an album or an artist. */
    private sealed interface Of {

        /** Bound as `?2` of the query, `null` when [filter] uses none. */
        val id: String?

        /** Added to the `WHERE` of [playsIn], over `h` and `v`. */
        val filter: String

        data object All : Of {
            override val id: String? = null
            override val filter = ""
        }

        data class Album(override val id: String) : Of {
            override val filter = "AND v.album_id = ?2"
        }

        data class Artist(override val id: String) : Of {
            override val filter =
                "AND EXISTS (SELECT 1 FROM song_artist_map f WHERE f.song_id = h.song_id AND f.artist_id = ?2)"
        }
    }

    private companion object {

        /**
         * `FROM` the plays of the user `?1` in [period], every play for `null` (aliased `h`), of
         * songs the library keeps and aren't disliked (aliased `v`), narrowed by [of]. A like is
         * a time, -1 is a dislike. The period's numbers are written into the query, never the
         * user's input.
         */
        fun playsIn(period: PlayPeriod?, of: Of = Of.All): String {
            val span = period?.let {
                "AND h.created_at >= ${it.start.toEpochMilliseconds()} " +
                        "AND h.created_at < ${it.end.toEpochMilliseconds()}"
            }.orEmpty()
            return "FROM playback_history h " +
                    "JOIN user_song_view v ON v.user_id = h.user_id AND v.song_id = h.song_id " +
                    "WHERE h.user_id = ?1 AND (v.liked_at IS NULL OR v.liked_at >= 0) $span ${of.filter}"
        }

        /**
         * `WITH per_song`: each song played in [period] that [of] allows, with its album, time
         * spent in seconds and listens (time spent over its length, a listen per play while the
         * length is unknown).
         */
        fun perSong(period: PlayPeriod?, of: Of): String =
            "WITH per_song AS (SELECT h.song_id AS song_id, MAX(v.album_id) AS album_id, " +
                    "SUM(h.time_spent) AS time_spent, " +
                    "CASE WHEN MAX(v.duration) > 0 THEN SUM(h.time_spent) * 1.0 / MAX(v.duration) " +
                    "ELSE COUNT(*) * 1.0 END AS listens " +
                    "${playsIn(period, of)} GROUP BY h.song_id)"
    }
}
