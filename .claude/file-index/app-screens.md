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
- home/ — `HomeScaffold.kt` (collapsing title/toolbar, `contentPadding` added to its bars'),
  `Home{Songs,Albums,Artists,Playlists}Screen/ViewModel.kt`, `OriginFilter.kt` (filter chips state),
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
  list), `SettingsComponents.kt` (SettingsPage/Section, MainSwitch (page-top switch),
  Switch/Choice/Radio/Number/Text/Slider settings, `prettyName`), `StreamQualitySettings.kt` (
  Wi-Fi + mobile quality sliders), `LibrarySyncSettings.kt` (per-section sync/show switches of a
  source); both, like `ProxySettings`, also have a stateless overload taking values that previews
  call. `general/GeneralSettingsScreen.kt`, `player/PlayerSettingsScreen.kt` (player behavior/UI
  only: Queue incl. preload slider, History; Android Auto section with the three
  `AUTO_*_TAP` choices), `ui/UiSettingsScreen.kt`,
  `network/NetworkSettingsScreen.kt` (timeouts/retries + "
  Configure YouTube/OpenSubsonic" rows), `network/youtube/` (`YouTubeSettingsScreen.kt` account
  page + always-shown Playback quality sliders, `YouTubeAccountViewModel.kt`,
  `YouTubeSignInScreen.kt` WebView sign-in, `YouTubeChannelPicker.kt`), `network/opensubsonic/` (
  `OpenSubsonicSettingsScreen.kt` + `OpenSubsonicSettingsViewModel.kt`: server, cert trust, sign-in
  checks, direct play, quality sliders, library), `backup/` (`BackupSettingsScreen.kt` +
  `BackupViewModel.kt`, SAF export/restore).
