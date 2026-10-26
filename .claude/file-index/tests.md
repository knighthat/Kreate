# File index: tests

Paths are relative to `<module>/src/main/kotlin/me/knighthat/kreate3/`; checked against the tree on
2026-10-20, verify a path still exists before relying on it. Map of the parts:
`.claude/file-index.md`.

Tests mirror the main paths under `src/test/`.

- app: `ProfileUserSyncTest`, auto/`AutoNodeTest`, `SongSubtitleTest`, `ServedArtworkTest`, backup/
  `BackupTest`, playback/`DataSourceResolverTest`,
  `CacheCopiesTest` (Robolectric), `SongDataSourceTest`, `LoudnessNormalizerTest`,
  `PlaybackErrorsTest`, `ShuffledOrderTest`, source/`CoverCacheTest`, `LoudnessMeterTest`,
  `MediaStoreScannerTest`,
  ui/navigation/`TopLevelBackStackTest`, ui/screen/detail/`LiveDetailViewModelTest`,
  `YouTubeDetailViewModelTest`, `SourceToggleTest`, ui/screen/search/`SearchViewModelTest`.
- database: `DatabaseTest`, `DatabaseBackupTest`, `ConvertersTest`, paging/`SortingTest`,
  repository/ `HistoryRepositoryTest`, `LocalCatalogRepositoryTest`, `MappersTest`,
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
