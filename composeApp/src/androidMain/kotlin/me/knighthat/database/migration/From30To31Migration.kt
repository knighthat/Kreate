package me.knighthat.database.migration

import androidx.room.RenameTable
import androidx.room.migration.AutoMigrationSpec


/**
 * This migration aims to rename tables to standards.
 *
 * This includes changing their names to  match functionalities,
 * or to remove `CamelCase` with `snake_case`.
 *
 * For example:
 * - QueuedMediaItem -> persistent_queue
 * - Song -> songs
 */
@RenameTable.Entries(
    RenameTable("Album", "albums"),
    RenameTable("Artist", "artists"),
    RenameTable("Event", "playback_history"),
    RenameTable("Format", "formats"),
    RenameTable("Lyrics", "lyrics"),
    RenameTable("Playlist", "playlists"),
    RenameTable("QueuedMediaItem", "persistent_queue"),
    RenameTable("SearchQuery", "search_history"),
    RenameTable("Song", "songs"),
    RenameTable("SongAlbumMap", "song_album_map"),
    RenameTable("SongArtistMap", "song_artist_map"),
    RenameTable("SongPlaylistMap", "song_playlist_map")
)
class From30To31Migration : AutoMigrationSpec