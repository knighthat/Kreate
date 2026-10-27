# File index: `:database`

Paths are relative to `database/src/main/kotlin/me/knighthat/kreate3/`; checked against the tree on
2026-10-20, verify a path still exists before relying on it. Map of the parts:
`.claude/file-index.md`.

Room schema: `database/schemas/me.knighthat.kreate3.database.AppDatabase/<version>.json`
(`.claude/scripts/schema.sh` reads it; rules in `.claude/rules/database.md`).

- `AppDatabase.kt` — Room DB (internal), entity/view/DAO list. `DatabaseModule.kt` — Koin,
  BundledSQLiteDriver (`.claude/rules/database.md`), repositories.
- `DatabaseBackup.kt` — `export` (VACUUM INTO), `restore` (before the DB opens), `VERSION` (=
  `DATABASE_VERSION` in DatabaseModule.kt).
- `ActiveUser.kt` — interface the app provides to say whose data to use. `repository/UserScope.kt` —
  restarts queries on user change.
- `Transaction.kt` — write-transaction helper (no `withTransaction` with SQLite driver).
  `Converters.kt` — Instant/Duration/list/Uri.
- entity/ — shared tables `Song`, `Album`, `Artist`, `SongFormat` (gain), `Lyrics`, `SongArtistMap`,
  `SongPlaylistMap` (`position` = playlist order, gaps left by removals); per-user `User`,
  `UserSong` (`album_position` = the user's album order over `song.album_position`, set for
  every song of the album by `UserSongRepository.moveInAlbum`, cleared by `resetAlbumOrder`),
  `UserAlbum`, `UserArtist`, `UserPlaylist`,
  `PlaybackHistory`, `SearchHistory`, `PersistentQueue`.
- view/ — `UserSongViewEntity`, `UserAlbumViewEntity`, `UserArtistViewEntity` (user override ?:
  shared value; songs with `unavailable_since` hidden).
- dao/ — one DAO per table (internal). `UserPlaylistDao.kt` also has `UserPlaylistRow` (a
  playlist + its `covers` JSON, for `PlaylistRepository.all`).
- model/ — public models mirroring entities/views (`Origin`/`LyricsSync` now come from `:model`);
  `LocalSong.kt` (+`LocalAlbum`/`LocalArtist` scan results), `QueuedSong.kt` (a saved-queue row; not
  the playback `QueuedSong` in `DataSourceResolver.kt`), `PeriodPlay.kt` (`PlayPeriod`,
  `PeriodPlay` = a play
  in a period with its song, `PeriodSummary`; `HistoryRepository.plays`/
  `periodSummaries` group plays by periods given as a `UNION ALL` of spans in SQL),
  `PlaylistCover.kt` (cover of one of a
  playlist's songs; `UserPlaylist.covers` = up to 4 different covers of its most played songs,
  filled by `PlaylistRepository.all` only).
- paging/ — `Paging.kt` (`RoomDatabase.pagedFlow(transform, tables…, pageSize)`, `LARGE_PAGE_SIZE`
  for bigger pages: also invalidates its sources
  on every change of the tables, because Room's paging source ignores changes made while its first
  page loads — the Songs tab missed the startup scan; PagingConfig), `Sorting.kt` (`SortOrder`,
  `*Sort` enums, `toOrderBy`).
- repository/ — public interface + internal `*Impl` pairs: `Song`, `Album`, `Artist`, `Playlist`,
  `History`, `Queue`, `User`, `UserSong` (likes/tri-state — `.claude/rules/data-model.md`;
  `forgetThumbnail(uri, songId?)` takes a file cover off every user's rows: deleted when nothing
  else is kept, else `NULL`; `fileThumbnails()`; per-user downloads via `user_song.downloaded_at`:
  `downloadedIds()` (active), `downloadedByAnyone()`, `forgetDownload(songId, cover, everyUser)`,
  `updateDownloaders`),
  `UserAlbum`, `UserArtist`, `LocalCatalog` (sync scan results), `RemoteCatalog` (`RemoteSong`,
  `RemoteItem`; liked-song sync from servers). Helpers: `Mappers.kt` (entity↔model),
  `OriginFilter.kt` (SQL WHERE by origins), `SearchPattern.kt` (escaped LIKE).
