# File index: tests

Paths are relative to `<module>/src/main/kotlin/me/knighthat/kreate3/`; checked against the tree on
2026-10-20, verify a path still exists before relying on it. Map of the parts:
`.claude/file-index.md`.

Tests mirror the main paths under `src/test/`.

- app: `ProfileUserSyncTest`, auto/`AutoNodeTest`, `VoiceSearchTest`, `SongSubtitleTest`,
  `ServedArtworkTest`, backup/
  `BackupTest`, download/`SongDownloadsTest` (song kept in a request), musicbrainz/
  `MusicBrainzTest` (MockEngine: best match, query fallback, recording parsing), playback/
  `DataSourceResolverTest`,
  `CacheCopiesTest` (Robolectric), `SongDataSourceTest` (read order, read-only downloads, the
  downloader's cache move), `LoudnessNormalizerTest`,
  `PlaybackErrorsTest`, `ShuffledOrderTest`, `KreateShuffleOrderTest`, source/`CoverCacheTest`,
  `LoudnessMeterTest`,
  `MediaStoreScannerTest`, `CoverFilesTest`, `PlaylistFilesTest` (CSV writer),
  ui/navigation/`TopLevelBackStackTest`, ui/screen/detail/`LiveDetailViewModelTest`,
  `YouTubeDetailViewModelTest`, `SourceToggleTest`, ui/screen/search/`SearchViewModelTest`,
  ui/screen/account/`HistoryBucketsTest`, `TimeTicksTest`, `UndislikedMessageTest`,
  `UnlikedMessageTest`
  (Undislike all's and Unlike all's message), playlist/`PlaylistCsvTest` (old and new headers,
  durations, quoting, missing headers, grouping, `LocalMatcher`).
- database: `DatabaseTest`, `DatabaseBackupTest` (cleaned copy too), `ConvertersTest`, paging/
  `SortingTest`,
  repository/ `CleanupRepositoryTest`, `HistoryRepositoryTest`, `StatisticsRepositoryTest`,
  `LocalCatalogRepositoryTest`,
  `MappersTest`,
  `PlaylistRepositoryTest`, `PlaylistImportRepositoryTest`, `RemoteCatalogRepositoryTest`,
  `SearchPatternTest`,
  `SongRepositoryTest`, `UserLibraryRepositoryTest`, `UserRepositoryTest`, `UserScopeTest`.
- preferences: `CodecTest`, `PreferencesBackupTest`, `DataStorePreferenceStorageTest`,
  `PreferenceEngineTest`, `ProfileManagerTest`, `SchemaMigrationTest`, `SecretStoreTest` (device
  secrets too).
- api: `PatreonFilesTest` (pages through the cursor with the token, 401 vs `tier_too_low` vs other
  errors, no address, upload link then PUT without the token and a retry on storage's 403, upload
  refusals, download link then content without the token, missing file, delete tells a file already
  gone), `PatreonAuthTest` (MockEngine: login URL and PKCE challenge, code trade,
  redirect and
  fragment results, refresh rotation, 401 vs other failures, no address, token claims and features,
  profile
  parsing and storing).
- discord: `DiscordActivityTest` (text limits, gateway JSON of buttons and assets).
- shazam: `ShazamTest` (signature equal to Metrolist's reference, silence has no peaks and isn't
  sent, resampling, MockEngine request/404/429/match parsing).
- lyrics: `LyricsFinderTest`, `LyricsSourcesTest` (MockEngine), `LyricsParserTest`. app:
  ui/screen/player/`LoadLyricsTest`, `LyricsImportTest` (decoding and reading lyrics files).
- provider: opensubsonic/ `OpenSubsonicLibraryTest`, `OpenSubsonicMappersTest`,
  `OpenSubsonicProviderTest`, `ServerStreamTest`, `SelfSignedTrustManagerTest`; youtube/
  `BotGuardTest`, `YouTubeStreamsTest`, `YouTubeLibraryTest`, `YouTubeLinksTest`,
  `YouTubeParserTest`, `YouTubeProviderTest`, `YouTubeSignInTest`.
- app: logging/ `RedactionTest`, `FileLogWriterTest` (level wait, rollover, keep, zip, tail, session
  filter, recent activity).
- Gotchas: `.claude/rules/testing.md`.
