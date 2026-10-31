# File index: `:app` ui/screen/

Paths are relative to `app/src/main/kotlin/me/knighthat/kreate3/`; checked against the tree on
2026-10-20, verify a path still exists before relying on it. Map of the parts:
`.claude/file-index.md`.

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
- account/ — `AccountScreen.kt` (the profile button's page: rows open History, Statistics, Disliked,
  Downloaded, and Switch profile starts `ProfilesActivity`). `DownloadedScreen.kt` +
  `DownloadedViewModel.kt` (the active user's whole downloads from `UserSongRepository.downloaded`
  kept to `DownloadsOverview.files`, row `trailing` = size · quality; storage header =
  `StorageUsage`
  of this user's and all users' bytes against `MEDIA3_DOWNLOAD_SIZE`; In progress section shows the
  running downloads and the next in line (else the first one), its See more opens the rest; origin
  chips (Disliked's `OriginChips`) as a row of the list; the bar's `ConfigButton` opens
  `DownloadedSheet` (`ConfigSheet`): Play all, Shuffle, Play next, Add to queue, Delete all
  (confirm, only what's shown), then Show disliked (`UserSongRepository.downloaded` includes
  disliked songs, hidden by default, marked with a thumb when shown); the newest/oldest button stays
  in the bar; tap plays the list from the song;
  nothing kept). `DownloadQueueScreen.kt` + `DownloadQueueViewModel.kt`
  (every unfinished download in the order asked for, failed last: state over the darkened cover
  (ring + percent, hourglass, faded ring + pause, warning), `UnfinishedRow.status` above the title
  (bytes of total, "Nth in line" for the first 5, "Paused at x%", the failure reason in the error
  color), tap pauses/resumes/retries, only Cancel at the end, no badges here; waiting-for line;
  `UnfinishedItem`, `WaitingLine`, `unfinishedActions` are shared with Downloaded, whose view model
  extends this one; flows stop 5 s after the screen, `WHILE_SHOWN`).
  `DislikedScreen.kt` + `DislikedViewModel.kt` (paged
  `UserSongRepository.disliked`, each row's `trailing` says how long ago it was disliked; origin
  chips under the bar (only sources with dislikes); the bar's `ConfigButton` opens a `ConfigSheet`
  with Undislike all (confirm dialog, only what's shown while filtered, one song at a time, then one
  message from
  `undislikedMessage`: done, the reason, or counts with failures; the song menu's Dislike takes one
  back); the newest/oldest button stays in the bar; tap plays the song alone; undislikes go through
  the song's source like the song menu, on
  the app scope; nothing is kept). `StatisticsScreen.kt` + `StatisticsViewModel.kt`
  (All time / This month chips, nothing kept; summary tiles with the change against last month up to
  the same day (`monthToDate`), top 5 songs with minutes, 3 albums, 10 artists, time per source;
  songs open `SongStatisticsScreen.kt` + `SongStatisticsViewModel.kt`, albums and artists
  `GroupStatisticsScreen.kt` + `GroupStatisticsViewModel.kt` (one of each per `AlbumStatistics`/
  `ArtistStatistics` route: header opening the real page, numbers, charts, top 10 songs opening
  theirs), all of all time), `ListeningCharts.kt` (`ChartBar`, `ListeningCharts`, built from plays
  by day/week/month, hour, weekday), `StatisticsDetail.kt` (parts the three detail screens share:
  charts section, `NumberLines`, `MinutesText`, `playDate`, preview charts), `StatisticsCharts.kt`
  (`TimeColumnChart`: Vico columns with a tap marker, every column labeled for Vico;
  `ArtistColumns`: Compose columns by time under `Thumbnail` avatars, a crown on the first, a
  `timeTicks` time axis with grid lines; `SourcesPie`: Vico donut + legend, a lone origin drawn as
  two halves to dodge Vico's filled full ring). `HistoryScreen.kt` + `HistoryViewModel.kt`
  (paged `HistoryRepository.plays` over `HistoryBuckets.spans()` clipped to the range, large
  pages; every play is a `HistoryRow` with its time in `SongItem`'s `trailing`, the day too with
  a range; `insertSeparators` adds `HistoryHeader`s, sticky, with a superscript songs · time
  summary from `periodSummaries` and Play/Play next/Add to queue, none while a range is picked; tap
  plays from
  that play on in its group (`songsFrom`); menu adds Delete from history (that play); top bar:
  newest/oldest toggle and Config, which opens `HistorySheet` (a `ModalBottomSheet`: Play all,
  Play next, Add to queue, Shuffle, Clear history as a row of `LabeledIconButton`s (labels hidden
  while Icon only), origin chips (shown even for one origin) (
  `FilterIconButton`s while Icon
  only), date presets and an inline `DateRangePicker`, each applied at once); nothing is kept, the
  page opens unfiltered and
  newest first; entries carry the `query` they came from, keys use it and the list goes to the top
  when it changes;
  Clear history clears all, or only what's shown while filtered; paused banner with Resume),
  `HistoryGroups.kt` (`HistoryBuckets`: Today, Yesterday, days to 6, weeks to a month, months to a
  year, years up to 50; `spans()` gives each bucket's time span; `HistoryRange` as picker UTC days,
  presets).
- home/ — `HomeScaffold.kt` (collapsing title/toolbar, `contentPadding` added to its bars'; the
  toolbar's `ConfigButton` opens the tab's `sheet`),
  `Home{Songs,Albums,Artists,Playlists}Screen/ViewModel.kt` (each tab's `ConfigSheet`: Songs play
  all/shuffle/play next/add to queue + show disliked, cached only, downloaded only (`SongFilters`,
  cache/download from `SongDownloads.songs`); Albums open random, play random (songs via
  `AutoLibrary.songsOf`) + bookmarked only; Artists open random + followed only (live items by their
  `isBookmarked`/`isFollowed`); Playlists open random, play random, new playlist + monthly only
  (grayed out unless local playlists exist and show); filters live in the view model, kept while the
  tab is in the back stack),
  `OriginFilter.kt` (filter chips state),
  `LiveItems.kt` (live items appended after DB items).
- detail/ — `DetailScaffold.kt` (`contentPadding` added to the bar's; lists take its padding as
  content padding), `DetailHeader.kt` (origin/explicit badges above the cover), `LiveDetail.kt` (
  `LiveDetailViewModel` (state re-filtered by `dislikedIds` through each subclass's
  `withoutSongs`), `LiveState`, `LiveSectionRow`, `LiveDetailScreen`), `ShareAction.kt` (
  OpenSubsonic share link on demand),
  `SourceToggle.kt` (bookmark/follow kept by a source: flips at once, sent in tap order on the app
  scope, flipped back with a message on failure; `serverFailure`/`youTubeFailure` texts);
  `{Local,OpenSubsonic,YouTube}{Album,Artist,Playlist}DetailScreen/ViewModel.kt` (album screens
  have Bookmark, artist screens Follow: local columns, the server's star, YouTube's library save /
  subscription, or local columns while signed out of YouTube; the album screen of a `LOCAL`
  album (not stored remote ones, they keep the server's order) has a more menu with Reorder: the
  whole album loaded in `arranged`, `DragHandle`s where covers would be, a drop calls
  `moveInAlbum` serialized by a `Mutex`, like the app's playlists; a long press on Reorder
  (`ReorderMenuItem`, a menu row that takes long presses) asks to reset to the album's order);
  `YouTubeMoreScreen/ViewModel.kt` (grid behind "See more");
  `OpenSubsonicArtistSongsScreen/ViewModel.kt` (every song of a server artist, behind "See more" of
  the artist screen's 10 top songs: `getTopSongs` filled from `search3`; Play/Shuffle there start
  a random top song at once and the rest follow, `rememberQuickQueueActions` +
  `PlaybackController.follow`).
- player/ — `PlayerSheet.kt` (full player sheet: anchoredDraggable over the window, nested scroll
  lets lists drag it down; pager of Player/Lyrics/Queue tabs at the bottom over the blurred cover,
  indicator on top with a full-width track; no collapse button; its own `MessageHost` above the
  tabs, the app's is under the sheet), `LyricsPage.kt` (`loadLyrics`,
  rules in `.claude/rules/lyrics.md`; `LyricsRequest`/`loadRequested` for bar picks,
  `LyricsControls` hoisted per song in `FullPlayer`, text scaled via `LocalDensity.fontScale` ×
  `LYRICS_TEXT_SIZE`), `LyricsButtons.kt` (lyrics bar: source menu, search dialog, timing menu, text
  size stepper, copy), `PlayerButtons.kt` (Player page bar: EQ = system effect panel via
  StartActivityForResult, sleep timer menu with countdown, share: YouTube link / OpenSubsonic
  `Shareable.Song` share / local file via EXTRA_STREAM), `QueuePage.kt` (`PlaybackController.queue`,
  rows in `shuffledOrder` while shuffled (queue order while its size lags), tap → `skipTo` and
  remove at `positionOf` (row in queue order, queue index less pending removals when shuffled),
  `FullPlayer` scrolls the list to the playing song's row when the order switches; entries keyed
  `mediaId#n`, `CoverEditMode` (`CoverEdit.kt`) turns covers into
  drag handles (sh.calvin.reorderable, local order, `move` on drop) or X buttons (`remove`);
  `QueueButtons` locate/lock/trash/clear (clear asks first: `PlaybackController.clear` keeps the
  song playing, or with "Remove currently playing song" checked `stop`, which closes the player);
  while shuffled the lock is `ModeButton(dimmed = true)` and a
  tap posts "Turn shuffle off to move songs", `FullPlayer` leaves REORDER when shuffle turns on).
  The bar under the tabs (`PageButtons` in PlayerSheet.kt,
  `BUTTON_BAR_HEIGHT`) shows each page's buttons, fading with the pager; queue list state and mode
  are hoisted into `FullPlayer`; the mode resets to NONE whenever the pager's current page leaves
  the queue (`.claude/rules/player.md`: leaving a page resets its buttons).
- search/ — `SearchScreen.kt` + `SearchViewModel.kt` (input, history, YouTube/server suggestions;
  the screen restores and saves `Preferences.SEARCH_ORIGIN`, falling back to LOCAL while the server
  is off), `SearchResultsScreen.kt` + `SearchResultsViewModel.kt`.
- settings/ — one subpackage per page; shared bits stay at the root: `SettingsScreen.kt` (page
  list), `SettingsComponents.kt` (SettingsPage (top bar actions + restart-app button with confirm
  dialog)/Section, MainSwitch (page-top switch), SettingsSummary (row text: title 1 line, summary
  2),
  Switch/Choice/Radio/Number/Text/Slider settings, `prettyName`), `StreamQualitySettings.kt` (
  Wi-Fi + mobile quality sliders), `LibrarySyncSettings.kt` (per-section sync/show switches of a
  source); both, like `ProxySettings`, also have a stateless overload taking values that previews
  call. `general/GeneralSettingsScreen.kt` (library scan, navigation), `data/DataSettingsScreen.kt`
  + `DataSettingsViewModel.kt` (Storage: `DeviceStorageUsage.kt` bar of the device with the app's
    parts (`StoragePart`: image cache, song cache, downloads, download covers, lyrics) and other
    apps, then image disk cache, song cache, song downloads as `StorageUsage` bars, polled every 2
    s; a tap opens a MiB field + slider of presets from Off to Unlimited, a size below what is
    stored asks first (Turn it off? / Smaller…, Apply, Apply & restart for caches, Cancel); default
    profile only: Clear / Delete each, confirmed (`SongCaches.clear`, Coil `diskCache.clear` +
    memory cache when ticked, `SongDownloads.deleteAll`); then Downloads and Images (memory cache)
    settings), `player/PlayerSettingsScreen.kt` (player behavior/UI
  only: Queue incl. preload slider, failure action + stall limit, Sound (even out loudness switch,
  target loudness slider), Buffering (load ahead incl.
  Unlimited / before
  playing sliders, keep played switch), History; Android Auto section with
  the three
    `AUTO_*_TAP` choices and a Voice search row), `player/VoiceSearchSettingsScreen.kt` (voice
    search origins: drag to reorder via `ReorderableColumn`, switch to skip; OpenSubsonic dimmed,
    not movable or switchable while `OPEN_SUBSONIC_ENABLED` is off),
    `ui/UiSettingsScreen.kt` (theme, contrast; Layout: icon only, playlist
    initial, binary units; app icon),
  `network/NetworkSettingsScreen.kt` (timeouts/retries + "
  Configure YouTube/OpenSubsonic" rows), `network/youtube/` (`YouTubeSettingsScreen.kt` account
    page + always-shown Region choice and Playback quality sliders, `YouTubeAccountViewModel.kt`,
  `YouTubeSignInScreen.kt` WebView sign-in, `YouTubeChannelPicker.kt`), `network/opensubsonic/` (
    `OpenSubsonicSettingsScreen.kt` + `OpenSubsonicSettingsViewModel.kt`: server, cert trust, "Sign
    in" row with the check's status, direct play (disabled while the song cache is Off, value kept),
    quality sliders, ReplayGain reference (LUFS), library; `OpenSubsonicSignInScreen.kt`: a
    segmented button per sign-in way, Sign out / Save in the top bar), `backup/` (
  `BackupSettingsScreen.kt` +
  `BackupViewModel.kt`, SAF export/restore), `about/` (`AboutScreen.kt` version, license and the
  licenses row; `OpenSourceLicensesScreen.kt` + `OpenSourceLicensesViewModel.kt`: libraries read
  from AboutLibraries' generated `R.raw.aboutlibraries`, search, license text sheet).
