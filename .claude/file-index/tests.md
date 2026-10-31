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
  `PlaybackErrorsTest`, `ShuffledOrderTest`, source/`CoverCacheTest`, `LoudnessMeterTest`,
  `MediaStoreScannerTest`, `CoverFilesTest`,
  ui/navigation/`TopLevelBackStackTest`, ui/screen/detail/`LiveDetailViewModelTest`,
  `YouTubeDetailViewModelTest`, `SourceToggleTest`, ui/screen/search/`SearchViewModelTest`,
  ui/screen/account/`HistoryBucketsTest`, `TimeTicksTest`, `UndislikedMessageTest` (Undislike all's
  message).
- database: `DatabaseTest`, `DatabaseBackupTest`, `ConvertersTest`, paging/`SortingTest`,
  repository/ `HistoryRepositoryTest`, `StatisticsRepositoryTest`, `LocalCatalogRepositoryTest`,
  `MappersTest`,
  `PlaylistRepositoryTest`, `RemoteCatalogRepositoryTest`, `SearchPatternTest`,
  `SongRepositoryTest`, `UserLibraryRepositoryTest`, `UserRepositoryTest`, `UserScopeTest`.
- preferences: `CodecTest`, `PreferencesBackupTest`, `DataStorePreferenceStorageTest`,
  `PreferenceEngineTest`, `ProfileManagerTest`, `SchemaMigrationTest`, `SecretStoreTest`.
- lyrics: `LyricsFinderTest`, `LyricsSourcesTest` (MockEngine), `LyricsParserTest`. app:
  ui/screen/player/`LoadLyricsTest`.
- provider: opensubsonic/ `OpenSubsonicLibraryTest`, `OpenSubsonicMappersTest`,
  `OpenSubsonicProviderTest`, `ServerStreamTest`, `SelfSignedTrustManagerTest`; youtube/
  `BotGuardTest`, `YouTubeStreamsTest`, `YouTubeLibraryTest`, `YouTubeLinksTest`,
  `YouTubeParserTest`, `YouTubeProviderTest`, `YouTubeSignInTest`.
- Gotchas: `.claude/rules/testing.md`.
