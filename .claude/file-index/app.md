# File index: `:app` root, notification, auto, preferences, utils, res

Paths are relative to `app/src/main/kotlin/me/knighthat/kreate3/`; checked against the tree on
2026-10-20, verify a path still exists before relying on it. Map of the parts:
`.claude/file-index.md`.

Root

- `AppRestart.kt` — `readOnce(preference)` for settings read once by app-wide singletons (network
  timeouts/retries, song cache + downloads sizes, image caches); `needed` = one of them changed
  since, lights the settings pages' restart-app button.
- `KreateApplication.kt` — `startKoin` with every module; global `CoroutineScope` single;
  `ProfileUserSync` bound as `ActiveUser`; Coil `ImageLoader` factory; `restartAfterPlayback()` (
  restart inside `PlaybackLifecycle.restartAround`, so the player's writes finish first); member
  `internal fun restart(configure)` (clear task, relaunch via the enabled launcher alias,
  `exit(0)`; used by wizard done/skip and backup restore).
- `KreateActivity.kt` — main activity: splash (waits on prefs), setup-wizard gate,
  `SETUP_WIZARD_SKIP_ACTION` handling, `OPEN_PLAYER_ACTION` (media notification tap, from onCreate
  or onNewIntent) → `KreateApp` expands the player sheet (unless `NOTIFICATION_OPENS_PLAYER` is
  off), starts `LocalMusicScheduler`, hosts `KreateApp`.
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
- `DownloadNotifications.kt` — DOWNLOADS channel, three groups (downloading 6000 = the download
  service's foreground summary, downloaded 6001, failed 6002); one child per song tagged with its
  download id (6010/6011/6012) with its cover (`smallCover`, loaded on the global scope), max 12 per
  group; downloading child has Pause + Cancel (Resume + Cancel once stopped, "Waiting for Wi-Fi" /
  battery text), summary has no buttons (Android never shows a group summary's own buttons);
  paused/battery-waiting children in their own group (Android drops a group's children with the
  service's summary when it stops); summaries posted 1 s later (coalesced, Android sheds fast
  updates), count = listed + posted in the last 3 s; failed child has Retry (re-adds the request to
  `SongDownloadService`), downloaded child has
  Play/Next/Queue (`DownloadedSongAction` → `PlaybackService`, data = download id);
  `clearStale()` at app start.
- `SystemNotifications.kt` — internal helpers `Context.canPostNotifications()`,
  `notificationBuilder(channel)` (icon `ic_splash` like the media one, tap = launch intent),
  `notify(id, n)` (no-op without POST_NOTIFICATIONS); `SystemNotifications.post(Message)` (action
  button dropped).

download/

- `SongDownloads.kt` — `SongStorage` (CACHED/DOWNLOADING/PAUSED/DOWNLOADED; `resume`),
  `overview()` (`DownloadsOverview`: the active user's whole files with size and quality, unfinished
  downloads as `UnfinishedDownload`/`DownloadStage`, `DownloadWait`, this user's and all users'
  bytes; polls the main-thread `currentDownloads` every second while one runs or waits),
  `pause`/`retry` (the notification's actions from the app); failure reasons kept per download for
  the app's run from the `DataSourceException` reason (`playbackReasonRes`),
  `DownloadPlan` (cached vs
  set quality, `movesCache`), `SongDownloads` (Koin single): `songs` map by song address (badges,
  menu), `plan`/`download(item)`/`item(id)`/`delete`/`removeCached`, listens to the
  `DownloadManager` (notifications, drops cache copies on completion), applies
  `DOWNLOAD_PARALLEL_COUNT`/`DOWNLOAD_RETRY_COUNT` live, `DOWNLOAD_WIFI_ONLY` as
  `Requirements.NETWORK_UNMETERED`, `DOWNLOAD_ON_LOW_BATTERY` off → `pauseDownloads()` while the
  battery-low broadcast holds (`pausedForBattery`; Media3 has no battery requirement), user pause =
  stop reason `STOP_REASON_PAUSED` (counts as DOWNLOADING), resumes unfinished downloads at start;
  per user: `songs` shows only the active user's marks, `download` stores + marks then shares an
  as-good file or (re)adds at the better quality (completion drops other download qualities),
  `leave(id)` unmarks the active user and removes the file once nobody wants it, startup `sweep`
  removes unwanted downloads and forgets marks without a download;
  request data = the song's MediaItem bundle (`toDownloadData`/`songItem`, with the Media3
  interface version).
- `DownloadedSongStore.kt` — what a download keeps in the DB: on start `SongStore.store` +
  `SongFormatRecorder.flush`; once whole the cover in both sizes (`CoverFiles.downloaded`, name =
  sha256 of the cover uri, JPEG q80), shared by songs of one cover, set as the active user's
  `user_song.thumbnail` (the 1440 file) unless a custom cover's file exists or it's another kind,
  and with `DOWNLOAD_LYRICS` the settings' source lyrics through `loadLyrics` (stored row or
  always-live → nothing); on removal `forgetThumbnail` for that song (every user), the files and
  every row's use of them gone once no download uses them.
- `SongDownloadService.kt` — Media3 `DownloadService`, dataSync foreground, no scheduler; re-applies
  the battery pause after `onCreate` (Media3 resumes all there), `ACTION_CANCEL` (data = id),
  `leave` for the active user only.
- `DownloadModule.kt` — Koin: `DownloadNotifications`, `DownloadManager` (no requirements),
  `SongDownloads`.

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

utils/ — `PermUtil.kt` (audio/notification permission helpers), `ScrollbarModifier.kt` (
`Modifier.verticalScrollbar`).

res: `res/raw/terms_of_service.txt`, `res/font/inter.ttf`; source SVGs for launcher/splash in
repo-root `assets/`.
