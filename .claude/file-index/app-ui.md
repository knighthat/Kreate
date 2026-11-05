# File index: `:app` ui/component, ui/navigation, ui/theme

Paths are relative to `app/src/main/kotlin/me/knighthat/kreate3/`; checked against the tree on
2026-10-20, verify a path still exists before relying on it. Map of the parts:
`.claude/file-index.md`.

ui/component/ (shared composables; each file that draws something ends with its own previews,
`.claude/rules/previews.md`)

- `Buttons.kt` —
  Back/Search/Profile/Settings/More/Share/PlayAll/Shuffle/PlayNext/AddToQueue/Random/CreatePlaylist/
  PlayPause/SeeMore/Config/SortOrder (newest/oldest first)/UndislikeAll/DeleteAllDownloads
  buttons, `QueueActionButton` (the one of PlayAll/Shuffle/PlayNext/AddToQueue a `QueueAction`
  names, album/artist page bars), `FilterIconButton` (a filter as its icon alone, origin chips while
  Icon only),
  `BookmarkButton`/`FollowButton` (filled in the primary color while set), `MoreSheetButton` (⋮ that
  opens a `ConfigSheet`, the artist pages'), `TileButton` (
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
- `OpenSubsonicName.kt` — `openSubsonicName()`: what texts call the OpenSubsonic server (the
  software's name from `OpenSubsonicServer`, else `source_opensubsonic`); `Resources` variant for
  messages, composable one (reads `Preferences`) for screens to pass to their content.
- `ConfigSheet.kt` — `ConfigSheet` (a screen's options: a row of `LabeledIconButton` actions given
  `close`, then `entries` and options under a divider; opens on its content,
  `skipPartiallyExpanded`), `ConfigToggle` (row with a trailing `Switch`), `ConfigEntry` (an action,
  optional long press,
  as a row, given `close` like the buttons; the artist menu's). Used by the home tabs.
- `BlurredCover.kt` — `Blur` (sample size + radius) and `BlurredCover` (a few pixels of a cover
  stretched and blurred, blurred on every Android version), for the full player and artist pages.
- `SongMenu.kt` (2026-10-20) — `LabeledIconButton` / `LabeledIconToggle` (internal, the sheet's top
  row, `showLabel`; the toggle also in the artist menu (Follow); also
  History's and `ConfigSheet`'s
  sheet), `SongMenuHost`/`LocalSongMenuHost`/`SongMenuOverlay` (one menu for
  the app, placed last in KreateApp's root Box), the lifted row (`LiftedRow`, `SheetPlacement`
  progress from `sheetState.requireOffset()`), the ModalBottomSheet (Dislike/Like/Share header, play
  next, add to queue, add to playlist + picker of the app's playlists (newest first, create button
  in its header), radio, go to album/artist(s) + picker, list actions like "Remove from playlist",
  Details opens `SongDetails` with the item's title, first artist and length).
  `SongMenuAction` for list-specific actions; `SongMenuHost.openPlaying(item)` for the full player's
  button (`rowBounds = null`: no lifted row; `showQueueActions = false`: no play next / add to
  queue).
- `OpenLink.kt` — `rememberOpenLink()`: opens a link, posts `error_no_browser` when no app can.
- `SongDownload.kt` — `LocalSongStorage` (provided in KreateApp), `MediaItem.storage`,
  `StorageBadge` (downloaded > cached, in `ItemBadges`), `SongDownloadAction` (song menu header
  button next to Share: Download / Cached → move-or-remove dialog with worse-quality warning /
  Downloading → stop dialog / Paused → stop or Resume / Downloaded → delete dialog).
- `AddToPlaylist.kt` — `rememberAddToPlaylist(item)`: the app's (`LOCAL`) playlists newest first,
  `add` on the global scope with an Added/Already in/Couldn't message; a song not in the
  `song` table is stored first (`SongStore`). The song's own source's playlists it may add to
  (`addablePlaylists`) are fetched live under a header,
  a progress ring while loading; `Origin.playlistOrigins` says which playlists a song can go in. New
  playlists are made on the `CreatePlaylist` screen.
- `ItemParts.kt` — `ItemRow`, `Thumbnail` (fallback drawable, `.claude/rules/covers.md`; a model in
  memory goes first unless under `minCachedSize`, then it is only the placeholder, no shimmer, the
  loaded one cross-fades over it), `LoadingShimmer`,
  `thumbnailModel`, `ItemBadge(s)` (origin/explicit badges; `OriginBadge`, `ExplicitBadge` alone),
  `formatDuration`,
  `MediaItem.localSongId`.
- `MediaItemDsl.kt` — model → `MediaItem` conversions (`toMediaItem`, `toLiveMediaItem`), `EXTRA_*`
  keys, `isExplicit`/`isBookmarked`/`hasBadges`.
- `QueueActions.kt` — (`QueueAction` enum is in `:preferences` `constant/`), `QueueActionRow`
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
- `Placeholders.kt` — shimmering stand-ins while a section loads: `LoadingBlock`,
  `ItemRowPlaceholder`, `TextPlaceholder` (description lines), `CardRowPlaceholder` (album/artist
  cards); used by the OpenSubsonic and YouTube artist pages.
- `SharedPicture.kt` — `LocalSharedTransitionScope` (KreateApp's `SharedTransitionLayout` around
  `NavDisplay`), `screenTransform` (400 ms fade of every screen change), `pictureFirst()` (entry
  metadata of the artist and album screens: opening, the picture moves for 350 ms while the screens
  fade,
  then the rest fades in over 200 ms; going back is the plain fade),
  `rememberSharedPicture(key, corners, otherCorners, onlyAway)` (artists; a `(key, shape,
  onlyAway)` overload for a same-shaped picture, album covers, `ShapeClip`): shared element with its
  own clip
  in a `graphicsLayer` (none while moving), corners morphing in the overlay by how far it grew
  (`MorphingClip`), `overlay` for what lies over it (artist bar, bottom fade); `onlyAway` (the
  artist and album cards) turns the match off while its screen comes back, so nothing moves back;
  `rememberContentsAfterPicture()`: the alpha of the rest of such a screen (keep the picture out
  of it);
  `artistPictureKey`, `albumPictureKey`.
- `RefreshBox.kt` — `RefreshBox`: pull-to-refresh around a scrollable (`pullToRefresh` + indicator,
  `enabled`, `indicatorTop`); used by `HomeScaffold`, the live detail screens
  (`LiveDetailViewModel.RefreshOnPull`), `YouTubeMoreScreen` and `SearchResultsScreen`.
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
  light+dark, `previewCover()` a made-up cover drawn as a `Bitmap`).
- `Sliders.kt` — thin-track sliders on a private `BallSlider` (4dp track, number in a ball/pill
  thumb 26dp, 34dp while held, no gap, unit label under the middle, `leadingIcon`/`trailingIcon`
  slots, `held` overloads for previews): `LufsSlider` (-18..-10 LUFS in 0.5 steps, filled from a
  mark at -14, "LUFS", fixed-width pill, volume down/up icons) and
  `SecondSlider` (whole seconds of an `IntRange`, filled from the start, "seconds", `number` for
  what the ball shows, hourglass -/+ icons) and `PercentSlider` (whole percents, "%", no default
  icons) and `MinuteSlider` (minutes of an `IntRange` in `step`s, "minutes", hourglass -/+ icons)
  and
  `CountSlider` (whole numbers, caller's `unit`, no default icons) and `StopSlider`
  (index of n stops, empty ball, nothing under it), all on a private
  `WholeSlider`; `SliderIcon` (icon with an optional small badge). Used by `SecondSetting`/
  `PercentSetting`/`CountSetting`/`LufsSetting`/`SliderSetting` and the Data size dialog. The thumb
  keeps one
  shape: the maintainer rejected pointed corners at the track ends.
- seekbar/`SeekBar.kt` — the full player's seek bar (moved out of PlayerSheet.kt by the maintainer),
  with previews and a private fake `PlaybackController`.

ui/navigation/

- `Route.kt` — every screen as a `Route` (Tab routes, Local*/OpenSubsonic*/YouTube* detail routes,
  SearchResults, YouTubeMore, settings, Account and its pages). `albumRoute`/`artistRoute` also take
  `(origin, id)`; `artistRoute(item)`/`albumRoute(item)` carry the card's name and picture
  (`OPENED_NAME`/`OPENED_PICTURE`, read as `OpenedItem`).
- `Navigator.kt` (2026-10-20) — `Navigator(current, open)` + `LocalNavigator`, provided by
  KreateApp, for opening screens from components that don't own the back stack (song menu).
- `TopLevelBackStack.kt` — one back stack per Tab.
- `RouteViewModel.kt` — `routeExtras`: route args → ViewModel `SavedStateHandle`.
- `RouteResults.kt` — pages opened for an answer like an activity result: `rememberRouteResult`
  (key saved, `RouteLauncher.launch(input)`, answer delivered once the opener is back),
  `RouteResults` Koin single holding inputs and answers in memory (`ProxyEditor` uses it).
- `ExitConfirmation.kt` — PredictiveBackHandler at the bottom of the stack (composed before
  NavDisplay in `KreateApp`): `EXIT_ON_BACK` off swallows back; on, `EXIT_CONFIRMATION_TIMEOUT`
  above 0 shows "
  Press/Swipe again to exit" for that many s (0 exits at once) (swipe = progress events or
  navigation_mode 2), second back moves the task back (API 31+) or finishes. Settings: General →
  Navigation.
- `StorageUsage.kt` — `StorageUse` (user/all/limit bytes and texts, `shared`, `limited`; off = 0,
  unlimited = `Long.MAX_VALUE` drawn against used + free room), `Context.storageUse`,
  `Resources.fileSize` (every size shown in the app, KiB/MiB or kB/MB by `BINARY_SIZES`),
  `StorageUsage` (this user's and all users' text over one stacked bar); Downloaded and Data.

ui/theme/ — `Theme.kt` (`KreateTheme`, color schemes, contrast), `ChartColors.kt` (fixed chart
palette per light/dark, `MaterialTheme.chartColors`, `Color.onChart()`), `ThumbnailSeed.kt` (
`rememberThumbnailSeed`, `rememberCoverSeed(models)`, runtime-only — `.claude/rules/storage.md`),
`CoverTheme.kt` (`CoverTheme(models)`: an album, artist or playlist page seeds its own colors from
its cover
while the theme is Thumbnail and the player is empty; `seedContrast` in `Theme.kt`),
`Type.kt` (Inter typography),
`KreateThemePreview.kt`.
