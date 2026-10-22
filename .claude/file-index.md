# File index

Where each source file lives and what it does, checked against the tree on 2026-10-20. Verify a
path still exists before relying on it; `git ls-files '*/src/main/*.kt'` finds files that moved.

Paths below are relative to `<module>/src/main/kotlin/me/knighthat/kreate3/`. Tests mirror them
under `src/test/` (see the list at the end). Room schema:
`database/schemas/me.knighthat.kreate3.database.AppDatabase/1.json` (v1, see
`.claude/rules/data-model.md`).

Module shape and rules: `.claude/rules/modules.md`.

## :app

Root

- `KreateApplication.kt` — `startKoin` with every module; global `CoroutineScope` single;
  `ProfileUserSync` bound as `ActiveUser`; Coil `ImageLoader` factory; top-level
  `internal fun Context.restart(configure)` (clear task, relaunch via the enabled launcher alias,
  `exit(0)`; used by wizard done/skip and backup restore).
- `KreateActivity.kt` — main activity: splash (waits on prefs), setup-wizard gate,
  `SETUP_WIZARD_SKIP_ACTION` handling, starts `LocalMusicScheduler`, hosts `KreateApp`.
- `ProfilesActivity.kt` — list/add/switch/delete profiles.
- `AppIcon.kt` — launcher icon choice (BLACK default, GRADIENT): enables one manifest
  `activity-alias` of `KreateActivity`, state lives in PackageManager, not Preferences.
- `ProfileUserSync.kt` — keeps DB `user` table in step with `ProfileManager` ids; implements
  `ActiveUser`.
- `ProfileSwitcher.kt` — switch/delete profile from `ProfilesActivity` (global scope):
  `PlaybackLifecycle.restartAround` (inside it `LocalMusicScheduler.cancelScans` first, instant +
  periodic), waits for `ActiveUser` + `Preferences.awaitProfile`, then in `finally` `scanNow` +
  `schedule` with the new profile's interval.
- `NetworkModule.kt` — Koin: shared `OkHttpClient` (timeouts/retries from Preferences), Ktor/JSON.
- `ViewModelModule.kt` — Koin: every `viewModel { }` declaration. Add new ViewModels here.
- backup/`Backup.kt` — zip export (`manifest.json`, `kreate.db` via VACUUM INTO, preference files;
  no secrets), `stage` (validate + unpack to `noBackupFilesDir/restore`, export current state to
  `cacheDir/rollback.zip`), `applyPendingRestore` (called in `KreateApplication.onCreate` before
  Koin; on failure stages the rollback for the next start, a failed rollback is dropped),
  `stageWithoutRollback` (setup wizard: no DB/prefs opened); restarting is `Context.restart()` in
  `KreateApplication.kt`. Wizard Import page stages a backup; `KreateActivity` then applies only the
  ToS checksum (wizard sends no profile/theme extras after an import).

notification/

- `Notifier.kt` — message bus (Koin single): `Message` (text, `MessageDuration`, dismissable,
  action, `channel`), `post()` from any code. With no collector (app not started) a message with a
  channel goes to `SystemNotifications`, one without is dropped.
- `AppChannel.kt` — notification channels (PLAYBACK default importance, OTHER default (one-off
  notices only, e.g. a failed restore — not every backup), LOCAL_SCAN low, SLEEP_TIMER low +
  `isSilent` (no sound/vibration, `notificationBuilder` sets `setSilent`), RESUME low + silent
  (brief foreground notification of playback resumption); message ids 2000+ordinal,
  newest replaces), `Context.createNotificationChannels()` called in `KreateApplication.onCreate`.
- `LocalScanNotifications.kt` — LOCAL_SCAN's two notifications (Koin single): scan (3000:
  indeterminate bar → "N songs scanned") and loudness (3001: `measured/total` bar, ≤1 update/s → "
  analyzed/total"; failed songs as group children 3100+, max 40, summary subText is the group
  header). `clearStale()` at app start drops ongoing progress left by a killed process. Called from
  `LocalMusicWorker`/`LoudnessWorker`.
- `SystemNotifications.kt` — internal helpers `Context.canPostNotifications()`,
  `notificationBuilder(channel)` (icon `ic_splash` like the media one, tap = launch intent),
  `notify(id, n)` (no-op without POST_NOTIFICATIONS); `SystemNotifications.post(Message)` (action
  button dropped).

playback/

- `PlaybackErrors.kt` — `PlaybackException.userMessage(item)`; `PlaybackControllerImpl` posts them
  to `Notifier` (dismissable); `togglePlay` re-prepares after a failure.
- `PlaybackService.kt` — `MediaLibraryService`, owns the player + notification; `SessionCallback`
  adds `withSongUri` to queued items, answers Android Auto's browsing from `AutoLibrary`
  (`onGetLibraryRoot`/`onGetChildren`/`onSubscribe`, no recent root) and turns a tapped Auto node
  into its queue in `onSetMediaItems` (items without `EXTRA_ORIGIN` whose id parses); follows
  `PLAYBACK_PRELOAD_SECONDS` into the player's preload. `onPlaybackResumption` plays
  `PersistentQueue.saved()` again when play comes with nothing queued (headphones, system media
  controls; `MediaButtonReceiver` in the manifest), going foreground first under
  `RESUME_FOREGROUND_ID` 5000 (`AppChannel.RESUME`); for System UI's card after a reboot only the
  playing song, cover as `artworkData`.
- `PlaybackController.kt` / `PlaybackControllerImpl.kt` — `PlaybackState` (incl.
  `repeatMode: RepeatMode` OFF/ONE/ALL, `isShuffled`) + interface screens use (`toggleShuffle`,
  `cycleRepeat`, `sleepTimer` (read from session extras), `startSleepTimer`/`changeSleepTimer` (
  custom session commands), `audioSessionId()` from session extras); impl wraps a `MediaController`
  on the main thread.
- `SleepTimer.kt` — `SleepTimer` (Running endsAt elapsedRealtime / Paused remaining / EndOfSong(
  isPaused)), `SleepTimerAction` (TOGGLE_PAUSE/ADD/REMOVE/STOP), `changedBy(step, add)` (
  UNTIL_SONG_ENDS → EndOfSong either way, EndOfSong takes no minutes, remove can't take all),
  session-extras codec. `SleepTimerRunner.kt` — service-side timer per session (countdown on the
  player scope, EndOfSong via `pauseAtEndOfMediaItems`, dropped when the queue empties or it ended
  while nothing played), `SleepTimerCommands` (session commands, only the app's own controllers get
  them in `SessionCallback.onConnect`), notification button intents handled in
  `PlaybackService.onStartCommand`; running out → pause + `finished` notification whose Restart (
  `SleepTimerCommands.RESTART`, extras: duration + ended song) replays via `restart()` (foreground
  first, see `.claude/docs/android-gotchas.md`). `SleepTimerNotification.kt` — id 4000, channel
  SLEEP_TIMER, DecoratedCustomViewStyle + `res/layout/sleep_timer_notification.xml` (Chronometer
  countdown, −/pause/+/stop; icons tinted `@color/notification_icon` light/night —
  `setColorStateListAttr(textColorPrimary)` resolved in the app's light theme and turned them
  black); swiped away = timer goes on, every user change re-posts, the remove-button refresh only
  updates a showing one.
- `AudioEffectSession.kt` — per session: broadcasts OPEN/CLOSE_AUDIO_EFFECT_CONTROL_SESSION, puts
  the audio session id in the session extras (`EXTRA_AUDIO_SESSION_ID`).
- `KreatePlayer.kt` — `ExoPlayer` delegate wrapper; enforces repeat-one ⇄ shuffle exclusion for
  every controller (app and system media controls).
- `DataSourceResolver.kt` — `QueuedSong` (`kreate://<ORIGIN>/<id>`, `quality`, `cacheKey` = uri
  `#QUALITY`), `MediaItem.withSongUri`, resolver: queued address → provider URL at read time in the
  copy's quality, YouTube stream cache/refusals, OpenSubsonic `ServerStream` reuse for seeks,
  `answered` (Content-Type) (`.claude/docs/playback-pipeline.md`).
- `SongFormatRecorder.kt` — stores YouTube/OpenSubsonic `SongFormat` (gain) before a song is read,
  called by the resolver; OpenSubsonic: `raw` row from getSong once + transcoded `{codec}-{kbps}`
  rows, `recordAnswered` tags from Content-Type; formats of unstored songs kept in memory
  (`pendingFormatsOf`/`pendingFormats`) until `flush`.
- `StreamQuality.kt` — `Context.streamQuality(origin, youTube)` (Wi-Fi/mobile by metered network,
  YouTube `_PREMIUM` pair while signed in, LOCAL = ORIGINAL), `Context.isOnline()`.
- `CacheCopies.kt` — picks the per-quality cache copy at song start (best whole ≥ setting → part ≥
  setting → new copy → offline best), pins it for seeks, `readEnded` drops lower copies once the
  read one is whole.
- `SongDataSource.kt` — `songDataSourceFactory`: local → direct, remote → downloads (read-only) →
  cache → ResolvingDataSource → `StreamDataSource` (app/OpenSubsonic OkHttp, 403 → `refused`, server
  Content-Type → `answered`); router sets the `CacheCopies` copy as DataSpec key + customData,
  OpenSubsonic direct play skips the cache.
- `SongCaches.kt` — the two SimpleCaches (streamed cache in cacheDir, downloads in
  noBackupFilesDir); `copies(song)` (quality → whole?), `drop`, `cachedLength`.
- `LoudnessNormalizer.kt` — volume change in dB from a song's gain (`normalizationDb`,
  `cutToVolume`), from the recorder's in-memory formats first, then the DB.
- `PlaybackHistoryRecorder.kt` — AnalyticsListener created in `PlaybackService`:
  `PlaybackStatsListener` play time (no pauses/loading, wall-clock) → `recordPlay` per session ≥
  `Preferences.PLAYBACK_HISTORY_MIN_SECONDS` (Player settings → History, default 1 s), nothing while
  `PLAYBACK_HISTORY_PAUSED`; on STATE_IDLE/ENDED saves the ongoing session early and `updatePlay`s
  that row when it ends; writes in the global scope (last ones come after release).
- `PlayedSongRecorder.kt` — stores a YouTube/server song once it starts playing (catalog + library,
  no like), asking its source again; then `SongFormatRecorder.flush` and `onStored` (queue re-save).
- `Radio.kt` — songs like a song, per origin: local ones scored from the user's library (
  `UserSongRepository.similar`), others asked from their source; nothing stored. `RADIO_SIZE` (30).
- `PersistentQueue.kt` — saves the player queue to `persistent_queue` (whole queue on playlist
  change; position on transition/pause/seek and every `PERSISTENT_QUEUE_SAVE_INTERVAL` s while
  playing) when `PERSISTENT_QUEUE_ENABLED`; restores it unprepared in `PlaybackService.onCreate` if
  the player is empty; `saved()` (also for playback resumption) gives the queue as items;
  `release()` before the player's. Table: PK (user_id, ordinal), `position` =
  ms of the one playing row (single-statement CASE update keeps it unique), songs missing from
  `song` skipped on insert.
- `PlaybackLifecycle.kt` — `restartAround { }`: disconnects the controller,
  `PlaybackService.stop()` (session released, its write job joined, 5 s cap then cancelled), runs
  the block, reconnects; service makes no session while `isStopped`.
- `PlaybackModule.kt` — Koin for player/controller and `AutoLibrary`;
  `ProgressiveMediaSource` (128 KiB check interval), `preloadConfiguration(seconds)`.

auto/ (Android Auto, browsed through `PlaybackService`)

- `AutoNode.kt` — media ids of the browse tree, `auto:`-prefixed: `Root`, `Section(Tab)`,
  `Collection(tab, origin, key)` (key = id at the origin, DB id for an app playlist),
  `Song(list, songId)`; `parse` returns `null` for anything else (source song ids).
- `AutoLibrary.kt` — root → the 4 home tabs (albums/artists as grids), same items and order as
  the home tabs with their `HOME_*_ORIGINS` filters (DB `list()` + `live*` from `LiveLibrary.kt`);
  albums/artists/playlists browsable or playable by `AUTO_*_TAP`; `queue(id)`: collection songs
  (shuffled for PLAY_RANDOMLY) or a song + those below it (Songs tab: radio while
  `HOME_SONGS_TAP_RADIO`). Collection songs like the detail screens (YouTube artist = top songs,
  YouTube playlist = every continuation). Auto's song rows (only those, not the app's items or the
  queue) have the subtitle `{artists} • {album}` (`songSubtitle`, no dot when one part is missing);
  an album's songs show only the artists, an artist's only the album.
- `ArtworkProvider.kt` — non-exported `ContentProvider` (`<applicationId>.artwork`) serving covers
  as JPEG through the app's Coil loader (`SMALL_COVER`, http/https/content only);
  `Context.autoArtworkUri` (`content://<pkg>.artwork/cover?source=…`, idempotent),
  `Context.grantArtwork(package)` (prefix read grant to the browser), `MediaItem.withServedArtwork`
  (queued songs, applied by `PlaybackService.queued` and `PersistentQueue`'s restore, so Auto's
  player, the notification and the lock screen load covers here), `Uri.coverSource()` (unwrap,
  used by `thumbnailModel`/`songThumbnailModels`), `Context.smallCoverJpeg` (same load as bytes,
  for the resumption card's `artworkData`). Manifest also declares Media3's `MediaButtonReceiver`.
  Manifest: service actions `MediaLibraryService` + `MediaBrowserService`,
  `com.google.android.gms.car.application` → `res/xml/automotive_app_desc.xml`; voice search
  (`MEDIA_PLAY_FROM_SEARCH`) not supported, its lint check is ignored.

preferences/`PreferenceDsl.kt` — `Preference<T>`/`Secret` `.collectAsStateWithLifecycle()` for
Compose.

source/ (app-side glue for sources)

- `LiveLibrary.kt` — `liveAlbums`/`liveArtists`/`livePlaylists`: fetch every external source live,
  merged in order, de-duplicated.
- `SongDetails.kt` — `songDetails(origin, songId, youTube, openSubsonic)`: a remote song as its
  source has it now, to store it (`PlayedSongRecorder`, `SongStore`).
- `SongStore.kt` — `SongStore.store(songId, origin)` (Koin single): makes sure a song is in the
  `song` table before the user keeps something about it (playlist, reaction); one fetched live is
  fetched with `songDetails` and stored with `savePlayedSong` (added to the library).
- `DeviceReactions.kt` — `DeviceReactions`/`DeviceReactionsImpl` (Koin single): album bookmarks
  and artist follows kept on the device for live items, storing the album/artist first when it
  isn't; YouTube detail screens use it while signed out.
- local/`MediaStoreScanner.kt` — reads device music from MediaStore → `LocalSong`.
- local/`LocalMusicWorker.kt` + `LocalMusicScheduler.kt` — WorkManager scan of device music, then
  loudness.
- local/`LoudnessWorker.kt` → `LoudnessAnalyzer.kt` (decode whole file) → `LoudnessMeter.kt` (LUFS
  measurement, reference loudness). Decodes on its own `Loudness-N` threads at
  THREAD_PRIORITY_BACKGROUND (see `CLAUDE.md`); posts `LocalScanNotifications` progress.
- `CoverCache.kt` — shared cover sizes (`SMALL_COVER` 512 / `LARGE_COVER` 1440, `coverSize`), JPEG
  re-encode (`encodeCover`), Coil disk-cache read/save helpers (see `.claude/docs/cover-cache.md`).
- local/`LocalCover.kt` — album-art content URI (`albumArtUri`), Coil `LocalCoverFetcher`/`Keyer`,
  disk-cached in two sizes keyed by DATE_MODIFIED (see `.claude/rules/data-model.md`).
- opensubsonic/`OpenSubsonicCoverFetcher.kt` — Coil fetcher signing `getCoverArt` requests via the
  provider, disk-cached in two sizes; throws (never returns null) while the server is off.
- opensubsonic/`OpenSubsonicSearch.kt` — `searchOpenSubsonic` paging (search3 by offset, one kind
  per tab; playlists = server playlists filtered by name, one page) and
  `OpenSubsonicProvider.suggestions` (3 per kind) for the search screen.
- youtube/`YouTubeSearch.kt` — `searchYouTube` paging, `YouTubeItem.toMediaItem`, `.tab`, artwork
  sizing.
- youtube/`YouTubeBrowse.kt` — `browseYouTube` paging for "See more" pages.

ui/component/ (shared composables)

- `Buttons.kt` —
  Back/Search/Profile/Settings/More/Share/PlayAll/Shuffle/Random/CreatePlaylist/PlayPause/SeeMore/Config
  buttons, `BookmarkButton`/`FollowButton` (filled in the primary color while set), `TileButton` (
  big icon+title+subtitle tile with busy spinner; wizard restore, Backup
  settings) (`.claude/rules/ui.md`).
- `SongItem.kt`, `AlbumItem.kt`, `ArtistItem.kt`, `PlaylistItem.kt` — list/grid items. `SongItem`
  long-press opens the song menu (`hasMenu = false` in QueuePage, `menuActions` for list-specific
  rows); `SongRow` draws both the row and its lifted copy.
- `SongMenu.kt` (2026-10-20) — `SongMenuHost`/`LocalSongMenuHost`/`SongMenuOverlay` (one menu for
  the app, placed last in KreateApp's root Box), the lifted row (`LiftedRow`, `SheetPlacement`
  progress from `sheetState.requireOffset()`), the ModalBottomSheet (Dislike/Like/Share header, play
  next, add to queue, add to playlist + picker of the app's playlists (newest first, create button
  in its header), radio, go to album/artist(s) + picker, list actions like "Remove from
  playlist", Details = "In construction"). `SongMenuAction` for list-specific actions.
- `AddToPlaylist.kt` — `rememberAddToPlaylist(item)`: the app's (`LOCAL`) playlists newest first,
  `add`/`create` on the global scope with an Added/Already in/Couldn't message; a song not in the
  `song` table is stored first (`SongStore`).
  `CreatePlaylistDialog` (also used by `HomePlaylistsScreen`).
- `ItemParts.kt` — `ItemRow`, `Thumbnail` (fallback drawable, `.claude/rules/ui.md`),
  `thumbnailModel`, `ItemBadge(s)` (origin/explicit badges), `formatDuration`,
  `MediaItem.localSongId`.
- `MediaItemDsl.kt` — model → `MediaItem` conversions (`toMediaItem`, `toLiveMediaItem`), `EXTRA_*`
  keys, `isExplicit`/`isBookmarked`/`hasBadges`.
- `QueueActions.kt` — `QueueAction` enum, `QueueActionRow`, `QueueMoreButton`,
  `PlaybackController.queue` (quick actions).
- `CenteredPicker` (now in `ui/screen/search/SearchScreen.kt`) — generic LazyRow picker, each value
  once (no looping — the maintainer rejected a pseudo-infinite list): selected item centered (snap),
  neighbours scaled 0.75 / faded, ends have one side empty, tap slides an item to the middle; used
  for the search origin in `SearchScreen` ConfigSheet. `Origin.label` (Local/YouTube/OpenSubsonic)
  lives in `ItemParts.kt`.
- `MiniPlayer.kt` (`PlaybackMiniPlayer`: own vertical `draggable` — up drives
  `PlayerSheetState.dragBy`/`settle`, down from a closed sheet slides+fades it, past half height or
  a fling calls `PlaybackController.stop()` = stop + clear queue; `MiniPlayerSpace`/
  `rememberMiniPlayerSpace`: measured mini player height, `outside(barPadding)` for the screen's
  outer padding, `inside` for list content padding), `SectionTitle.kt`.
- `TabBar.kt` — `TabBar`: the NavigationBar of `Tab`s, shared by the home bottom bar (`KreateApp`)
  and the search results tabs; drops labels under `LocalIconOnly`.
- `Messages.kt` — `LocalNotifier`/`LocalMessageHost` (provided in `KreateApp`), `ShowMessages` (
  collects only while STARTED, read from `currentStateFlow` — not repeatOnLifecycle, lint flags it
  since `KreateActivity` calls setContent in onStart; newest replaces current), `MessageHost` (the
  app scaffold shows it except on Search/SearchResults, which put it above their own bottom bar).
  Screens no longer have their own SnackbarHostState.
- `IconOnly.kt` — `LocalIconOnly` (provided in `KreateApp` from `Preferences.ICON_ONLY`, false in
  previews): bottom bar drops its text, home origin chips become `OriginToggle` icon toggles (
  primary on / 38% onSurface off), `QueueActionRow` becomes FilledIconButton Play + ShuffleButton;
  icons carry the content description.
- `PreviewSamples.kt` (sample data, `PreviewTheme`, `@ScreenPreviews` phone-sized light+dark,
  `@ComponentPreviews` wrap-content light+dark), `ItemPreviews.kt` (@Previews).
- seekbar/`SeekBar.kt` — the full player's seek bar (moved out of PlayerSheet.kt by the maintainer),
  with previews and a private fake `PlaybackController`.

ui/navigation/

- `Route.kt` — every screen as a `Route` (Tab routes, Local*/OpenSubsonic*/YouTube* detail routes,
  SearchResults, YouTubeMore, settings). `albumRoute`/`artistRoute` also take `(origin, id)`.
- `Navigator.kt` (2026-10-20) — `Navigator(current, open)` + `LocalNavigator`, provided by
  KreateApp, for opening screens from components that don't own the back stack (song menu).
- `TopLevelBackStack.kt` — one back stack per Tab.
- `RouteViewModel.kt` — `routeExtras`: route args → ViewModel `SavedStateHandle`.
- `ExitConfirmation.kt` — PredictiveBackHandler at the bottom of the stack (composed before
  NavDisplay in `KreateApp`): `BACK_EXIT_DISABLED` swallows back; `EXIT_CONFIRMATION` shows "
  Press/Swipe again to exit" for `EXIT_CONFIRMATION_TIMEOUT` s (swipe = progress events or
  navigation_mode 2), second back moves the task back (API 31+) or finishes. Settings: General →
  Navigation.

ui/screen/

- `KreateApp.kt` — top-level scaffold: bottom bar, Navigation 3 `NavDisplay` entry → screen mapping,
  MiniPlayer. `Route.hasMiniPlayer` puts the mini player at the window bottom; a screen with its own
  bottom bar (Search, SearchResults) must opt out there and draw `PlaybackMiniPlayer` above its bar
  itself, or the mini player lands under that bar.
- player/`SongReaction.kt` — `rememberSongReaction(item)`: like/dislike state of a song (player,
  song menu) from `UserSongRepository.find`, toggles write `liked_at` on the global scope
  (OpenSubsonic songs through `OpenSubsonicLibrary.react`, which stars them; a failed star is taken
  back and posted to `LocalNotifier`). A song not in the library shows neutral and is added on its
  first reaction (`SongStore` first); `null` (buttons off) only while the lookup loads or the item
  has no origin. A dislike also calls `PlaybackController.drop`.
- `SetupWizardScreen.kt` — first-run wizard (ToS, profile name, theme, permissions);
  `setupCompletedFile`.
- home/ — `HomeScaffold.kt` (collapsing title/toolbar, `contentPadding` added to its bars'),
  `Home{Songs,Albums,Artists,Playlists}Screen/ViewModel.kt`, `OriginFilter.kt` (filter chips state),
  `LiveItems.kt` (live items appended after DB items).
- detail/ — `DetailScaffold.kt` (`contentPadding` added to the bar's; lists take its padding as
  content padding), `DetailHeader.kt`, `LiveDetail.kt` (`LiveDetailViewModel`, `LiveState`,
  `LiveSectionRow`, `LiveDetailScreen`), `ShareAction.kt` (OpenSubsonic share link on demand),
  `SourceToggle.kt` (bookmark/follow kept by a source: flips at once, sent in tap order on the app
  scope, flipped back with a message on failure; `serverFailure`/`youTubeFailure` texts);
  `{Local,OpenSubsonic,YouTube}{Album,Artist,Playlist}DetailScreen/ViewModel.kt` (album screens
  have Bookmark, artist screens Follow: local columns, the server's star, YouTube's library save /
  subscription, or local columns while signed out of YouTube);
  `YouTubeMoreScreen/ViewModel.kt` (grid behind "See more").
- player/ — `PlayerSheet.kt` (full player sheet: anchoredDraggable over the window, nested scroll
  lets lists drag it down; pager of Player/Lyrics/Queue tabs at the bottom over the blurred cover,
  indicator on top with a full-width track; no collapse button), `LyricsPage.kt` (`loadLyrics`,
  rules in `.claude/rules/lyrics.md`; `LyricsRequest`/`loadRequested` for bar picks,
  `LyricsControls` hoisted per song in `FullPlayer`, text scaled via `LocalDensity.fontScale` ×
  `LYRICS_TEXT_SIZE`), `LyricsButtons.kt` (lyrics bar: source menu, search dialog, timing menu, text
  size stepper, copy), `PlayerButtons.kt` (Player page bar: EQ = system effect panel via
  StartActivityForResult, sleep timer menu with countdown, share: YouTube link / OpenSubsonic
  `Shareable.Song` share / local file via EXTRA_STREAM), `QueuePage.kt` (`PlaybackController.queue`,
  tap → `skipTo`; entries keyed `mediaId#n`, `QueueEditMode` NONE/REORDER/REMOVE turns covers into
  drag handles (sh.calvin.reorderable, local order, `move` on drop) or X buttons (`remove`);
  `QueueButtons` locate/lock/trash). The bar under the tabs (`PageButtons` in PlayerSheet.kt,
  `BUTTON_BAR_HEIGHT`) shows each page's buttons, fading with the pager; queue list state and mode
  are hoisted into `FullPlayer`; the mode resets to NONE whenever the pager's current page leaves
  the queue (`.claude/rules/ui.md`: leaving a page resets its buttons).
- search/ — `SearchScreen.kt` + `SearchViewModel.kt` (input, history, YouTube/server suggestions;
  the screen restores and saves `Preferences.SEARCH_ORIGIN`, falling back to LOCAL while the server
  is off), `SearchResultsScreen.kt` + `SearchResultsViewModel.kt`.
- settings/ — one subpackage per page; shared bits stay at the root: `SettingsScreen.kt` (page
  list), `SettingsComponents.kt` (SettingsPage/Section, MainSwitch (page-top switch),
  Switch/Choice/Radio/Number/Text/Slider settings, `prettyName`), `StreamQualitySettings.kt` (
  Wi-Fi + mobile quality sliders), `LibrarySyncSettings.kt` (per-section sync/show switches of a
  source). `general/GeneralSettingsScreen.kt`, `player/PlayerSettingsScreen.kt` (player behavior/UI
  only: Queue incl. preload slider, History; Android Auto section with the three
  `AUTO_*_TAP` choices), `ui/UiSettingsScreen.kt`,
  `network/NetworkSettingsScreen.kt` (timeouts/retries + "
  Configure YouTube/OpenSubsonic" rows), `network/youtube/` (`YouTubeSettingsScreen.kt` account
  page + always-shown Playback quality sliders, `YouTubeAccountViewModel.kt`,
  `YouTubeSignInScreen.kt` WebView sign-in, `YouTubeChannelPicker.kt`), `network/opensubsonic/` (
  `OpenSubsonicSettingsScreen.kt` + `OpenSubsonicSettingsViewModel.kt`: server, cert trust, sign-in
  checks, direct play, quality sliders, library), `backup/` (`BackupSettingsScreen.kt` +
  `BackupViewModel.kt`, SAF export/restore).

ui/theme/ — `Theme.kt` (`KreateTheme`, color schemes, contrast), `ThumbnailSeed.kt` (
`rememberThumbnailSeed`, runtime-only — `.claude/rules/storage.md`), `Type.kt` (Inter typography),
`KreateThemePreview.kt`.

utils/ — `PermUtil.kt` (audio/notification permission helpers), `ScrollbarModifier.kt` (
`Modifier.verticalScrollbar`).

res: `res/raw/terms_of_service.txt`, `res/font/inter.ttf`; source SVGs for launcher/splash in
repo-root `assets/`.

## :model

Pure Kotlin/JVM (`kotlin("jvm")`), package `me.knighthat.kreate3.model`, rules in its
`build.gradle.kts` header (see `.claude/rules/modules.md`).

- `Origin.kt` (moved from database/model 2026-10-19), `LyricsSync.kt` (NONE/LINE/WORD),
  `LyricsProvider.kt` (BETTER_LYRICS, LYRICS_PLUS, LRCLIB, YOUTUBE — declaration order = ask order).
  All stored by name: never rename.

## :database

- `AppDatabase.kt` — Room DB (internal), entity/view/DAO list. `DatabaseModule.kt` — Koin,
  BundledSQLiteDriver (`.claude/rules/data-model.md`), repositories.
- `DatabaseBackup.kt` — `export` (VACUUM INTO), `restore` (before the DB opens), `VERSION` (=
  `DATABASE_VERSION` in DatabaseModule.kt).
- `ActiveUser.kt` — interface the app provides to say whose data to use. `repository/UserScope.kt` —
  restarts queries on user change.
- `Transaction.kt` — write-transaction helper (no `withTransaction` with SQLite driver).
  `Converters.kt` — Instant/Duration/list/Uri.
- entity/ — shared tables `Song`, `Album`, `Artist`, `SongFormat` (gain), `Lyrics`, `SongArtistMap`,
  `SongPlaylistMap`; per-user `User`,
  `UserSong`, `UserAlbum`, `UserArtist`, `UserPlaylist`,
  `PlaybackHistory`, `SearchHistory`, `PersistentQueue`.
- view/ — `UserSongViewEntity`, `UserAlbumViewEntity`, `UserArtistViewEntity` (user override ?:
  shared value; songs with `unavailable_since` hidden).
- dao/ — one DAO per table (internal).
- model/ — public models mirroring entities/views (`Origin`/`LyricsSync` now come from `:model`);
  `LocalSong.kt` (+`LocalAlbum`/`LocalArtist` scan results), `QueuedSong.kt` (a saved-queue row; not
  the playback `QueuedSong` in `DataSourceResolver.kt`).
- paging/ — `Paging.kt` (`RoomDatabase.pagedFlow(transform, tables…)`: also invalidates its sources
  on every change of the tables, because Room's paging source ignores changes made while its first
  page loads — the Songs tab missed the startup scan; PagingConfig), `Sorting.kt` (`SortOrder`,
  `*Sort` enums, `toOrderBy`).
- repository/ — public interface + internal `*Impl` pairs: `Song`, `Album`, `Artist`, `Playlist`,
  `History`, `Queue`, `User`, `UserSong` (likes/tri-state — `.claude/rules/data-model.md`),
  `UserAlbum`, `UserArtist`, `LocalCatalog` (sync scan results), `RemoteCatalog` (`RemoteSong`,
  `RemoteItem`; liked-song sync from servers). Helpers: `Mappers.kt` (entity↔model),
  `OriginFilter.kt` (SQL WHERE by origins), `SearchPattern.kt` (escaped LIKE).

## :preferences

- `preferences/Preferences.kt` — every setting (`object Preferences`), keys on disk never renamed.
  `Preference.kt` — single setting following the active profile.
- `preferences/Secrets.kt` + `Secret.kt` + `SecretStore.kt` — passwords/tokens in
  `noBackupFilesDir/profiles/<id>/secrets.preferences_pb` (`.claude/rules/storage.md`).
- `app/res/xml/backup_rules.xml` (API ≤30) + `data_extraction_rules.xml` (API 31+) — backup/transfer
  exclusions (`.claude/rules/storage.md`).
- `preferences/PreferencesBackup.kt` — preference files for a backup (global + profiles/<id>
  /preferences), path check, `restore`, schema versions.
- `preferences/ProfileManager.kt` — profile ids, active profile, file layout. `Profile.kt` —
  constants.
- `preferences/PreferenceEngine.kt` (active-profile snapshot), `PreferenceStorage.kt` +
  `DataStorePreferenceStorage.kt` (per-profile DataStore), `PreferencesDataStore.kt`, `Codec.kt`,
  `SchemaMigration.kt` (GLOBAL/PROFILE/SECRET steps), `PreferencesModule.kt`.
- constant/ — `ThemeMode`, `ContrastLevel`, `OpenSubsonicAuth`, `StreamQuality` (
  LOW/MEDIUM/HIGH/ORIGINAL, order = ordinal), `SyncMode` (OFF/LIKED/ALL), `SleepTimerStep` (sleep
  timer add/remove amounts), `ProxyType` (HTTP/SOCKS), `AutoTapAction`
  (OPEN/PLAY_FROM_START/PLAY_RANDOMLY, Android Auto) enums.

## :provider

- `Provider.kt` — interface every source implements (search, details, library sections); answers
  with DB models, never writes. `Providers.kt` — Origin → Provider lookup. `ProviderModule.kt` —
  Koin (register new providers here and in `Origin`). `SearchResults.kt`, `LibrarySection.kt` (
  synced library parts). `ReactionSender.kt` (shared by the OpenSubsonic and YouTube libraries:
  saves a song reaction, sends it, takes it back on failure; keeps a liked-songs pull that
  overlapped one from being stored; `isLike`/`isDislike`).
- local/`LocalProvider.kt` — reads device songs back from DB.
- opensubsonic/ — `OpenSubsonicProvider.kt` (API client: ping, auth check, library, details, shares,
  cover URLs, `stream(id, quality)` via transcode decision or maxBitRate, `extensions()`; gated by
  `OPEN_SUBSONIC_ENABLED` — `.claude/rules/sources.md`), `OpenSubsonicLibrary.kt` (starred-song
  sync both ways: pulls stars, `react` stars/unstars a song liked/unliked in the app,
  `openSubsonicSyncSetting`), `OpenSubsonicSettings.kt`, `OpenSubsonicClients.kt` (dedicated
  OkHttp client), `OpenSubsonicMappers.kt` (JSON → models, `toSongFormat` = `raw`,
  `toTranscodedFormat`), `ServerStream.kt` (`ServerStream`, `RAW_FORMAT`, Content-Type → format
  tag), `SelfSignedTrustManager.kt` (cert pinning, `CertificatePin`), `ServerCheck.kt`,
  `ServerException.kt`, `OpenSubsonicSearch.kt` (`searchLibrary` result: RemoteSong/RemoteItem
  lists), `Shareable.kt`, `ArtistWithSongs.kt`.
- youtube/ — `YouTubeProvider.kt` (public API: search, suggestions, album/artist/playlist pages,
  browse, sign-in session, channels, lyrics; `isYouTubeSession`), `InnerTube.kt` (HTTP +
  `YouTubeSession` + `YouTubeException`; `stream` delegates to `YouTubeStreams.kt`, the innertubex
  extractor: signed-out and session extractors, `StreamQuality.audioQuality`, Original = session +
  premium hint), `WebPoTokenProvider.kt` + `PoTokenWebView.kt` + `BotGuard.kt` (PO tokens via a
  hidden WebView, page in `provider/src/main/assets/po_token.html`), `YouTubeParser.kt` (InnerTube
  JSON → models), `YouTubeJson.kt` (JSON path helpers), `YouTubeModels.kt` (`YouTubeItem`,
  page/section/account/channel models, `YouTubeSearchFilter`, `YouTubeStream`),
  `YouTubeLibrary.kt` (liked-song sync both ways: pulls "Liked Music", `react` sends like,
  dislike or neither while signed in; `youTubeSyncSetting`), `YouTubeLinks.kt` (share links,
  `resizeArtwork`).
- test resources: `provider/src/test/resources/youtube/*.json` (captured InnerTube responses),
  `certs/*.pem`.

## :lyrics

Depends only on `:model` (api) plus Ktor/serialization/Koin.

- `LyricsQuery.kt` — song id + `Origin` + title/artists/album/duration.
- `LyricsSource.kt` — interface (`provider: LyricsProvider`, `name`, main-safe `find`) and
  `FoundLyrics` (raw `text` as the source gave it — plain/LRC/TTML/LyricsPlus JSON, `sync`,
  `source: LyricsProvider`, `credit`). The DB stores that raw text; parsing happens when shown.
- `LyricsContent.kt` — parsed model: `PlainLyrics`, `SyncedLyrics` of `LyricsLine` (start, end?,
  text, `words`, `background`) / `LyricsWord`; `coarsenedTo(sync)`.
- `LyricsParser.kt` — main-safe `parseLyrics(text)` sniffs the format (`<` TTML, `{` LyricsPlus,
  LRC, else plain; unreadable → plain), `parseClock`, `withEnds`. `LrcParser.kt` (LRC + enhanced
  LRC, offset, multi-time lines), `TtmlParser.kt` (SAX, namespace-aware: p/span, x-bg backing lines,
  itunes:timing), `LyricsPlusParser.kt` (syllabus, isBackground).
- `LyricsFinder.kt` — asks sources in order, never in parallel; `find(query, wanted, exclude)` stops
  at `wanted` sync, else most finely timed; `LyricsException` when nothing found and a source
  failed.
- Sources: `BetterLyricsSource.kt` (TTML), `LyricsPlusSource.kt` (mirrors, JSON),
  `LrcLibSource.kt` (/api/search, LRC) — see `.claude/rules/lyrics.md`. The YouTube one is
  `app/.../source/youtube/YouTubeLyricsSource.kt` (needs `:provider`), bound in `KreateApplication`'
  s Koin module.
- `LyricsModule.kt` — Koin `lyricsModule`: each source `bind LyricsSource::class`;
  `LyricsFinder(getAll<LyricsSource>().sortedBy { it.provider })`.
- App side: `ui/screen/player/SyncedLyricsList.kt` (LazyColumn, position every frame while playing,
  lit lines, word fill drawn with clipRect + drawText from the TextLayoutResult in the draw phase,
  auto-scroll to 1/3 unless dragged, tap line = seek); `ui/screen/player/LyricsPage.kt` (shows
  lyrics coarsened to the preferred timing; `loadLyrics` follows `.claude/rules/lyrics.md`),
  settings in `PlayerSettingsScreen` Lyrics section. DB `lyrics` table: `sync`, `source` (
  LyricsProvider by name), `credit` columns. Settings `LYRICS_SOURCE`/`LYRICS_SYNC` are enum prefs.

## Tests

- app: `ProfileUserSyncTest`, auto/`AutoNodeTest`, `SongSubtitleTest`, `ServedArtworkTest`, backup/
  `BackupTest`, playback/`DataSourceResolverTest`,
  `CacheCopiesTest` (Robolectric), `SongDataSourceTest`, `LoudnessNormalizerTest`,
  `PlaybackErrorsTest`, source/`CoverCacheTest`, `LoudnessMeterTest`, `MediaStoreScannerTest`,
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

## Proxies

Provider `Proxies.kt` (`SourceProxy` Off/Incomplete/Ready, `sourceProxy()` reader,
`OkHttpClient.Builder.proxy(source, proxy)` — HTTP proxyAuthenticator, SOCKS via a process-wide
java.net.Authenticator keyed by proxy host:port, Incomplete = interceptor that fails every request),
`HttpClientFactory.kt` (app's Ktor setup, bound in NetworkModule), `youtube/YouTubeClients.kt` (
rebuilt per proxy with own pool; `youTubeProxy()`, `isYouTubeHost()`); `OpenSubsonicClients` keys on
proxy too. App: `source/SourceNetworkClient.kt` (Coil routes YouTube hosts to
`YouTubeProvider.httpClient()`), `SongDataSource` has a YouTube upstream (
`YouTubeProvider.streamClient()`), settings `ProxySettings.kt` (section at the end of both source
pages), `network/youtube/SignInProxy.kt` (androidx.webkit ProxyController override while the sign-in
screen shows; SOCKS with a username refused, Chromium can't sign in to SOCKS).
