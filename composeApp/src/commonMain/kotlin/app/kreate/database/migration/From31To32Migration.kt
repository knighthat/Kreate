package app.kreate.database.migration

import androidx.room.RenameColumn
import androidx.room.migration.AutoMigrationSpec


/**
 * This migration aims to rename columns to standards.
 *
 *  This includes changing their names to  match functionalities,
 * or to remove `CamelCase` with `snake_case`.
 *
 * For example:
 * - QueuedMediaItem -> persistent_queue
 * - Song -> songs
 */
@RenameColumn.Entries(
    // Album
    RenameColumn("albums", "thumbnailUrl", "thumbnail_url"),
    RenameColumn("albums", "authorsText", "artists"),
    RenameColumn("albums", "shareUrl", "url"),
    RenameColumn("albums", "bookmarkedAt", "bookmarked_at"),
    RenameColumn("albums", "timestamp", "created_at"),
    RenameColumn("albums", "isYoutubeAlbum", "youtube"),

    // Artist
    RenameColumn("artists", "thumbnailUrl", "thumbnail_url"),
    RenameColumn("artists", "timestamp", "created_at"),
    RenameColumn("artists", "bookmarkedAt", "bookmarked_at"),
    RenameColumn("artists", "isYoutubeArtist", "youtube"),

    // Event
    RenameColumn("playback_history", "songId", "song_id"),
    RenameColumn("playback_history", "timestamp", "created_at"),
    RenameColumn("playback_history", "playTime", "time_spent"),

    // Format
    RenameColumn("formats", "songId", "song_id"),
    RenameColumn("formats", "itag", "yt_itag"),
    RenameColumn("formats", "mimeType", "mimetype"),
    RenameColumn("formats", "contentLength", "length"),
    RenameColumn("formats", "lastModified", "updated_at"),
    RenameColumn("formats", "loudnessDb", "loudness"),

    // Lyrics
    RenameColumn("lyrics", "songId", "song_id"),

    // Playlist
    RenameColumn("playlists", "browseId", "browse_id"),
    RenameColumn("playlists", "isEditable", "editable"),
    RenameColumn("playlists", "isYoutubePlaylist", "youtube"),

    // QueueMediaItem
    RenameColumn("persistent_queue", "mediaItem", "song"),

    // Song
    RenameColumn("songs", "artistsText", "artists"),
    RenameColumn("songs", "durationText", "duration"),
    RenameColumn("songs", "thumbnailUrl", "thumbnail_url"),
    RenameColumn("songs", "likedAt", "liked_at"),
    RenameColumn("songs", "totalPlayTimeMs", "total_playtime"),

    // SongAlbumMap
    RenameColumn("song_album_map", "songId", "song_id"),
    RenameColumn("song_album_map", "albumId", "album_id"),

    // SongArtistMap
    RenameColumn("song_artist_map", "songId", "song_id"),
    RenameColumn("song_artist_map", "artistId", "artist_id"),

    // SongPlaylistMap
    RenameColumn("song_playlist_map", "songId", "song_id"),
    RenameColumn("song_playlist_map", "playlistId", "playlist_id"),
    RenameColumn("song_playlist_map", "setVideoId", "set_video_id")
)
class From31To32Migration : AutoMigrationSpec