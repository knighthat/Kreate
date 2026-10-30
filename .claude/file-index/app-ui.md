# File index: `:app` ui/component, ui/navigation, ui/theme

Paths are relative to `app/src/main/kotlin/me/knighthat/kreate3/`; checked against the tree on
2026-10-20, verify a path still exists before relying on it. Map of the parts:
`.claude/file-index.md`.

ui/component/ (shared composables; each file that draws something ends with its own previews,
`.claude/rules/previews.md`)

- `Buttons.kt` —
  Back/Search/Profile/Settings/More/Share/PlayAll/Shuffle/PlayNext/AddToQueue/Random/CreatePlaylist/
  PlayPause/SeeMore/Config/SortOrder (newest/oldest first)/UndislikeAll/DeleteAllDownloads
  buttons, `FilterIconButton` (a filter as its icon alone, origin chips while Icon only),
  `BookmarkButton`/`FollowButton` (filled in the primary color while set), `TileButton` (
  big icon+title+subtitle tile with busy spinner; wizard restore, Backup
  settings) (`.claude/rules/ui.md`).
- `SongItem.kt`, `AlbumItem.kt`, `ArtistItem.kt`, `PlaylistItem.kt` — list/grid items. `SongItem`
  long-press opens the song menu (`hasMenu = false` in QueuePage, `menuActions` for list-specific
  rows, `trailing` in place of the length, like History's play times); `SongRow` draws both the row
  and its lifted copy. `PlaylistItem` without a picture of its
  own shows its covers (`EXTRA_COVERS`): 4 → 2 × 2 grid, 1–3 → the most played alone, none →
  `queue_music` icon, or the name's first letter on a hue from the name (HCT) while
  `LocalPlaylistInitial` (`Preferences.PLAYLIST_INITIAL`, provided in `KreateApp`) is on; drawn
  through `Thumbnail`'s `placeholder` slot.
- `ConfigSheet.kt` — `ConfigSheet` (a screen's options: a row of `LabeledIconButton` actions given
  `close`, then options under a divider; opens on its content, `skipPartiallyExpanded`),
  `ConfigToggle` (row with a trailing `Switch`). Used by the home tabs.
- `SongMenu.kt` (2026-10-20) — `LabeledIconButton` (internal, the sheet's top row, `showLabel`; also
  History's and `ConfigSheet`'s
  sheet), `SongMenuHost`/`LocalSongMenuHost`/`SongMenuOverlay` (one menu for
  the app, placed last in KreateApp's root Box), the lifted row (`LiftedRow`, `SheetPlacement`
  progress from `sheetState.requireOffset()`), the ModalBottomSheet (Dislike/Like/Share header, play
  next, add to queue, add to playlist + picker of the app's playlists (newest first, create button
  in its header), radio, go to album/artist(s) + picker, list actions like "Remove from
  playlist", Details = "In construction"). `SongMenuAction` for list-specific actions.
- `SongDownload.kt` — `LocalSongStorage` (provided in KreateApp), `MediaItem.storage`,
  `StorageBadge` (downloaded > cached, in `ItemBadges`), `SongDownloadAction` (song menu header
  button next to Share: Download / Cached → move-or-remove dialog with worse-quality warning /
  Downloading → stop dialog / Paused → stop or Resume / Downloaded → delete dialog).
- `AddToPlaylist.kt` — `rememberAddToPlaylist(item)`: the app's (`LOCAL`) playlists newest first,
  `add`/`create` on the global scope with an Added/Already in/Couldn't message; a song not in the
  `song` table is stored first (`SongStore`).
  `CreatePlaylistDialog` (also used by `HomePlaylistsScreen`).
- `ItemParts.kt` — `ItemRow`, `Thumbnail` (fallback drawable, `.claude/rules/covers.md`),
  `thumbnailModel`, `ItemBadge(s)` (origin/explicit badges; `OriginBadge`, `ExplicitBadge` alone),
  `formatDuration`,
  `MediaItem.localSongId`.
- `MediaItemDsl.kt` — model → `MediaItem` conversions (`toMediaItem`, `toLiveMediaItem`), `EXTRA_*`
  keys, `isExplicit`/`isBookmarked`/`hasBadges`.
- `QueueActions.kt` — `QueueAction` enum (PLAY/SHUFFLE/ENQUEUE/PLAY_NEXT), `QueueActionRow`
  (detail screens: Play, then Shuffle / Play next / Add to queue icon buttons, optional `menu` for a
  more button), `QueuePlayButton` (labeled, filled icon when icon-only), `QueueMoreButton`,
  `PlaybackController.queue` (quick actions). The app's playlists pass the move/remove switches as
  its menu (`PlaylistActionRow` in `LocalPlaylistDetailScreen.kt`), device albums a Reorder switch.
- `CoverEdit.kt` — `CoverEditMode` NONE/REORDER/REMOVE and `CoverEditOverlay` (drag handle or X
  button over a `SongItem` cover, the handle being the caller's reorderable `draggableHandle`), for
  the queue and the app's playlists; `DragHandle` (the handle alone in the cover's place, for
  cover-less rows: device albums; `ItemRow` draws `thumbnailOverlay` alone without the cover).
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
  primary on / 38% onSurface off), `QueuePlayButton` becomes a FilledIconButton;
  icons carry the content description.
- `PreviewSamples.kt` (sample data, `previewItem` with album/media type/reaction for badges,
  `PreviewTheme`, `@ScreenPreviews` phone-sized light+dark, `@ComponentPreviews` wrap-content
  light+dark).
- seekbar/`SeekBar.kt` — the full player's seek bar (moved out of PlayerSheet.kt by the maintainer),
  with previews and a private fake `PlaybackController`.

ui/navigation/

- `Route.kt` — every screen as a `Route` (Tab routes, Local*/OpenSubsonic*/YouTube* detail routes,
  SearchResults, YouTubeMore, settings, Account and its pages). `albumRoute`/`artistRoute` also take
  `(origin, id)`.
- `Navigator.kt` (2026-10-20) — `Navigator(current, open)` + `LocalNavigator`, provided by
  KreateApp, for opening screens from components that don't own the back stack (song menu).
- `TopLevelBackStack.kt` — one back stack per Tab.
- `RouteViewModel.kt` — `routeExtras`: route args → ViewModel `SavedStateHandle`.
- `ExitConfirmation.kt` — PredictiveBackHandler at the bottom of the stack (composed before
  NavDisplay in `KreateApp`): `EXIT_ON_BACK` off swallows back; on, `EXIT_CONFIRMATION` shows "
  Press/Swipe again to exit" for `EXIT_CONFIRMATION_TIMEOUT` s (swipe = progress events or
  navigation_mode 2), second back moves the task back (API 31+) or finishes. Settings: General →
  Navigation.
- `StorageUsage.kt` — `StorageUse` (user/all/limit bytes and texts, `shared`, `limited`; off = 0,
  unlimited = `Long.MAX_VALUE` drawn against used + free room), `Context.storageUse`,
  `Resources.fileSize` (every size shown in the app, KiB/MiB or kB/MB by `BINARY_SIZES`),
  `StorageUsage` (this user's and all users' text over one stacked bar); Downloaded and Data.

ui/theme/ — `Theme.kt` (`KreateTheme`, color schemes, contrast), `ChartColors.kt` (fixed chart
palette per light/dark, `MaterialTheme.chartColors`, `Color.onChart()`), `ThumbnailSeed.kt` (
`rememberThumbnailSeed`, runtime-only — `.claude/rules/storage.md`), `Type.kt` (Inter typography),
`KreateThemePreview.kt`.
