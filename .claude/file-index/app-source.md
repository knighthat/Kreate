# File index: `:app` source/

Paths are relative to `app/src/main/kotlin/me/knighthat/kreate3/`; checked against the tree on
2026-10-20, verify a path still exists before relying on it. Map of the parts:
`.claude/file-index.md`.

source/ (app-side glue for sources)

- `LiveLibrary.kt` — `liveAlbums`/`liveArtists`/`livePlaylists`: fetch every external source live,
  merged in order, de-duplicated.
- `SongDetails.kt` — `songDetails(origin, songId, youTube, openSubsonic)`: a remote song as its
  source has it now, to store it (`PlayedSongRecorder`, `SongStore`).
- `CoverFiles.kt` — covers kept as files under `noBackupFilesDir`: `download_covers` (2 sizes,
  `downloaded(address, size)`, `sizedLike`) and `custom(profileId)` =
  `profiles/<id>/custom_thumbnail`; `kindOf` → `CoverKind`
  (NONE/DOWNLOADED/CUSTOM/OTHER); `Mapper` (Coil, 1440 → 512 file when drawn small);
  `forgetMissing` (app start, gone files off every user's songs). Koin single in
  `KreateApplication`.
- `SongStore.kt` — `SongStore.store(songId, origin)` (Koin single): makes sure a song is in the
  `song` table before the user keeps something about it (playlist, reaction); one fetched live is
  fetched with `songDetails` and stored with `savePlayedSong` (added to the library).
- `DeviceReactions.kt` — `DeviceReactions`/`DeviceReactionsImpl` (Koin single): album bookmarks
  and artist follows kept on the device for live items, storing the album/artist first when it
  isn't and removing the library entry again when taken back with nothing else kept
  (`removeIfEmpty`); YouTube detail screens use it while signed out.
- `DislikedSongs.kt` — `withoutDisliked`/`withoutDislikedSongs` (lists) and `hidingDisliked`
  (paged, after `cachedIn`): leave out songs whose id is in `UserSongRepository.dislikedIds`.
- local/`MediaStoreScanner.kt` — reads device music from MediaStore → `LocalSong`.
- local/`LocalMusicWorker.kt` + `LocalMusicScheduler.kt` — WorkManager scan of device music, then
  loudness.
- local/`LoudnessWorker.kt` → `LoudnessAnalyzer.kt` (decode whole file) → `LoudnessMeter.kt` (LUFS
  measurement, reference loudness). Decodes on its own `Loudness-N` threads at
  THREAD_PRIORITY_BACKGROUND (see `CLAUDE.md`); posts `LocalScanNotifications` progress.
- `CoverCache.kt` — shared cover sizes (`SMALL_COVER` 512 / `LARGE_COVER` 1440, `coverSize`), JPEG
  re-encode (`encodeCover`), Coil disk-cache read/save helpers (see `.claude/docs/cover-cache.md`).
- local/`LocalCover.kt` — album-art content URI (`albumArtUri`), Coil `LocalCoverFetcher`/`Keyer`,
  disk-cached in two sizes keyed by DATE_MODIFIED (see `.claude/docs/cover-cache.md`).
- opensubsonic/`OpenSubsonicCoverFetcher.kt` — Coil fetcher signing `getCoverArt` requests via the
  provider, disk-cached in two sizes; throws (never returns null) while the server is off.
- opensubsonic/`OpenSubsonicSearch.kt` — `searchOpenSubsonic` paging (search3 by offset, one kind
  per tab; playlists = server playlists filtered by name, one page) and
  `OpenSubsonicProvider.suggestions` (3 per kind) for the search screen.
- youtube/`YouTubeSearch.kt` — `searchYouTube` paging, `YouTubeItem.toMediaItem`, `.tab`, artwork
  sizing.
- youtube/`YouTubeBrowse.kt` — `browseYouTube` paging for "See more" pages.
