# File index: `:app` root, notification, auto, preferences, utils, res

Paths are relative to `app/src/main/kotlin/me/knighthat/kreate3/`; checked against the tree on
2026-10-20, verify a path still exists before relying on it. Map of the parts:
`.claude/file-index.md`.

Root

- `AppRestart.kt` — `readOnce(preference)` for settings read once by app-wide singletons (network
  timeouts/retries, song cache + downloads sizes, image caches); `needed` = one of them changed
  since, lights the settings pages' restart-app button.
- `KreateApplication.kt` — `startKoin` with every module; global `CoroutineScope` single;
  `ProfileUserSync` bound as `ActiveUser`; Coil `ImageLoader` factory (disk cache size 0 = none, its
  folder `imageCacheDir()` deleted); `restartAfterPlayback()` (
  restart inside `PlaybackLifecycle.restartAround`, so the player's writes finish first); member
  `internal fun restart(configure)` (clear task, relaunch via the enabled launcher alias,
  `exit(0)`; used by wizard done/skip and backup restore).
- `PictureInPictureWindow.kt` — picture-in-picture window (Android 11+,
  `Preferences.PICTURE_IN_PICTURE`):
  `Context.pictureInPictureParams` (1:1 cover / 16:9 lyrics, previous/play-pause/next
  `RemoteAction`s, auto-enter on 12+), `PictureInPictureReceiver` (buttons → `PlaybackController`).
  `KreateActivity` keeps the params current while shown and enters from `onUserLeaveHint` on 11.
- `KreateActivity.kt` — main activity: splash (waits on prefs), setup-wizard gate,
  `SETUP_WIZARD_SKIP_ACTION` handling, `OPEN_PLAYER_ACTION` (media notification tap, from onCreate
  or onNewIntent) → `KreateApp` expands the player sheet (unless `NOTIFICATION_OPENS_PLAYER` is
  off), starts `LocalMusicScheduler`, hosts `KreateApp`. `ACTION_VIEW` of an audio file ("Open
  with", manifest filter `audio/*` + ogg/flac, content/file) → `OpenedFiles.item` →
  `PlaybackController.play` + opens the player; `null` → `error_open_file` message. A
  `music.youtube.com` link (`ACTION_VIEW`, filter on /watch /playlist /browse/ /channel/, not
  verified) or text shared to it (`ACTION_SEND` text/plain) → `YouTubeLinks.find`: a song plays
  (`songDetails` → `toLiveMediaItem`) and the player opens, an album/artist/playlist goes to
  `KreateApp(openRoute)`, which collapses the player and pushes it; anything else →
  `error_open_link`.
- `ProfilesActivity.kt` — list/add/switch/delete profiles.
- `ServiceSignOut.kt` — sign out of a service that must be told first; every Koin one runs before
  a settings reset deletes the profile's secrets (Discord's in nonfree).
- `NonFreeNet.kt` — two copies, one per platform set: `app/src/nonfree/…` (github, patreon:
  `nonFreeNetModules(isSetup)` = `presenceModule` + `shazamModule` + the `SongRecognizer` binding)
  and `app/src/foss/…` (`NonFreeNetOff`, created at start: turns `DISCORD_ENABLED` and
  `SHAZAM_ENABLED` off in each profile once it's active and loaded, and again whenever they come
  back on, like after a settings reset); `KreateApplication` adds them.
  `BuildConfig.NON_FREE_NET` says
  which build it is. `app/src/foss/AndroidManifest.xml` removes `DiscordCallbackActivity`,
  `DiscordSignInActivity` and
  `RECORD_AUDIO`; `app/src/patreon/AndroidManifest.xml` removes the Crowdin SDK's `AuthActivity`.
- `TranslationUpdates.kt` — two copies: `app/src/patreon/…` (APK has English only; `languages`
  from Crowdin's manifest kept in `noBackupFilesDir/crowdin_manifest.json`, refetched by
  `refresh` (language page) once 48 h old; `check` at start, on configuration changes and every
  48 h on the global scope: downloads the shown non-English language through the Crowdin SDK
  (distribution `BuildConfig.CROWDIN_DISTRIBUTION_HASH`: env, else local.properties; empty = English
  only) when it has none, or its download is 48 h old and
  the manifest has a newer release; times per tag in `translation_checks`, a failed download is
  forgotten; `delivered` makes `KreateActivity` recreate after a language's first download;
  `wrap` in both activities' `attachBaseContext`) and `app/src/crowdin/kotlin/…` (foss, github:
  does nothing, the APK's texts).
- presence/ — Discord's Rich Presence on the app's side (`:discord` does the protocol). Only
  `Doing.kt` and `DiscordCovers.kt` are in `main`; the rest is under `app/src/nonfree/`, left out
  of foss. `PresenceState` is registered in `KreateApplication` for every build.
  `PresenceModule.kt` (`presenceModule(isSetup)`: a Ktor client without read timeout through the
  network proxy (named `discord`), `DiscordAuth`/`DiscordClient` with `BuildConfig.DISCORD_APP_ID` /
  `DISCORD_REDIRECT_URI`, `DiscordPresence` created at start once set up, a `ServiceSignOut` named
  `discord` calling
  `DiscordAccount.forget`), `DiscordAccount.kt`
  (tokens in `Secrets.DISCORD_*`; `access(profile, force)` refreshes a day ahead or when the gateway
  refused it, saves only while that profile is active, forgets them on `invalid_grant` and posts
  the signed-out notice;
  `DiscordAccountState`; `start` → URL to open,
  `finish(uri)` from the callback, `abandon` on resume without an answer, `refreshUser` once per
  profile, `signOut` revokes, `forget` = the same, returning once forgotten),
  `DiscordCallbackActivity.kt` (exported, `kreate3://discord/callback`,
  own task, hands the answer over and brings `KreateActivity` back),
  `DiscordSignInNotification.kt` (OTHER channel, id 8000: "Signed out of Discord" with a Sign in
  button, taken back on a finished sign in), `DiscordSignInActivity.kt` (not exported, the button's
  target: `start()` and opens the page; an activity because a receiver can't open another app from
  a notification on Android 12+), `Doing.kt` (`Doing.Listening`/
  `Browsing`/`Searching`, `BrowsedKind`, `PresenceState` (player's song + page on screen),
  `ShowOnDiscord`/`ShowBrowsingOnDiscord`/`ShowSearchingOnDiscord` composables, off in previews;
  called by the album/artist/playlist detail scaffolds and `SearchResultsScreen`),
  `DiscordPresence.kt` (picks the song over a page, pages shown for YouTube only, a page reads item
  name / creator / "Viewing …", per-source switches, English texts from `DiscordTexts.kt` (literals
  on purpose), YouTube covers as YouTube's URL at `DISCORD_COVER_SIZE`, others uploaded, the
  artist's
  picture through `ArtistPictures.pictureOf`, `app_logo` fallback, connection while enabled + signed
  in
  + something shown or shown in the last minute, per profile; `REJECTED` → forced refresh, a failed
    refresh retried after 1 min), `DiscordCovers.kt` (resolution stops, size stops by
    `BINARY_SIZES`, `snapSettings`, also used by UI settings), `CoverUploads.kt`
    (`CoverUploads.publicUrl(models)`:
    first cover that loads at the setting's size, MD5 of its pixels, JPEG shrunk to the network's
    limit,
    litterbox `1h`, reused 55 min, a failure waits 10 min).
- `AppLanguage.kt` — app language, per profile in `Preferences.LANGUAGE` (empty = the device's):
  `available` from `BuildConfig.LANGUAGES` (English + the `app/src/crowdin/res/values-*` folders
  with a `*.strings.xml`, listed by `app/build.gradle.kts`) + `TranslationUpdates.languages`
  (patreon; `follow` waits for them), `displayName` in the language itself,
  country/script only when two share a language; `select` (settings), `follow` (started by
  `KreateApplication`: shows each profile's language once it loads, a restored one too) and
  `takeFromSystem` (config change: a pick in the system's settings goes to the active profile). What
  is shown: Android 13+ `LocaleManager`, older the `app_language` SharedPreferences read by
  `wrap` in `attachBaseContext` of the application, both activities and both services (a change
  restarts the app there); that file also holds the last shown tag.
- `AppIcon.kt` — launcher icon choice (BLACK default, GRADIENT): enables one manifest
  `activity-alias` of `KreateActivity`, state lives in PackageManager, not Preferences.
- logging/ — `AppLog.kt` (Kermit setup: Logcat + `FileLogWriter`, rolls `kreate.log` over in
  `externalCacheDir/logs`, queues until the level is known, `follow` applies the `GlobalPreferences`
  log settings), `LogFiles.kt` (folders, tail, zip), `LogActions.kt` (Copy/Export of the settings),
  `Redaction.kt` (`redact` for every written text, `isSecretHeader`), `CrashHandler.kt` (uncaught
  handler, crash log in `getExternalFilesDir/crashlogs`, `deleteExpired`), `CrashActivity.kt`
  (`:crash` process screen: Copy/Export/Close/Restart, no Koin), `NetworkLogger.kt` (Ktor
  `Logging`), `CoilLogger.kt` (Coil `Logger`), `WorkerLogging.kt` (`logged` for workers).
  `.claude/docs/logging.md`.
- `ProfileUserSync.kt` — keeps DB `user` table in step with `ProfileManager` ids; implements
  `ActiveUser`.
- `ProfileSwitcher.kt` — switch/delete profile from `ProfilesActivity` (global scope):
  `PlaybackLifecycle.restartAround` (inside it `LocalMusicScheduler.cancelScans` first, instant +
  periodic), waits for `ActiveUser` + `Preferences.awaitProfile`, then in `finally` `scanNow` +
  `schedule` with the new profile's interval.
- `NetworkModule.kt` — Koin: shared `OkHttpClient` (timeouts/retries from Preferences), Ktor/JSON.
- musicbrainz/`MusicBrainz.kt` — MusicBrainz web service client (Koin single in
  `KreateApplication`): recording `search` (phrase, then loose words), `identify` (`best`: top
  scores, length within 5 s, not live, most releases, earliest), `recording` lookup (credits, ISRCs,
  genres + tags, releases); artist `searchArtists`, `identifyArtist` (top scores, same name, then
  other case), `artist` lookup (area, life span and places, aliases, genres, ISNIs, url links); one
  request a second across callers, named User-Agent.
- `ViewModelModule.kt` — Koin: every `viewModel { }` declaration. Add new ViewModels here.
- font/ — `FontFiles.kt` (Koin single; `SavedFont` = the `<id>:<name>` a font preference stores,
  `FontSlot` APP/LYRICS; fonts picked by the user in `noBackupFilesDir/profiles/<id>/fonts/
  <slot>-<id>.font`: `stage` (copy + check), `keep`, `forget`, `backupFiles`/`restore`/
  `isBackupPath` for the zip), `Sfnt.kt` (reads a font's table directory: is it a font, is it
  variable).
- backup/`Backup.kt` — zip export (`manifest.json`, `kreate.db` via VACUUM INTO, preference files;
  no secrets), `stage` (validate + unpack to `noBackupFilesDir/restore`, export current state to
  `cacheDir/rollback.zip`), `applyPendingRestore` (called in `KreateApplication.onCreate` before
  Koin; on failure stages the rollback for the next start, a failed rollback is dropped),
  `stageWithoutRollback` (setup wizard: no DB/prefs opened); restarting is `Context.restart()` in
  `KreateApplication.kt`. Wizard Import page stages a backup; `KreateActivity` then applies only the
  ToS checksum (wizard sends no profile/theme extras after an import).
- backup/`LegacyBackup.kt` — import of the old Kreate app's one-zip archive (`manifest.json` with
  `source: "kreate-legacy"`, `profiles/<n>/data.db`): `unpack` validates, `stage`/
  `stageWithoutRollback`
  build `kreate.db` with `LegacyImport` and a profile list, through `Backup.stageBuilt`. Not wired
  to any screen yet. Rules in `.claude/rules/backup.md`.
- playlist/`PlaylistCsv.kt` — `ExportifyCsvColumns` (Spotify playlists exported by Exportify: a
  header with its four `REQUIRED` columns makes the file one, other columns ignored, artists split
  by `;`, rows are `matchOnYouTube` tracks without a playlist name; `exportifyPlaylistName` from
  the file name), `PlaylistCsvColumns` (this app's snake_case columns, `HEADER` the
  export writes; unlike the old app's on purpose so it rejects these files), `LegacyCsvColumns`
  (old Kreate's, import only); reads both kinds (`parsePlaylistCsv`: the kind whose columns the
  header has more of, this app's on a tie; columns in any order, case ignored; a missing,
  repeated or unknown column fails with `InvalidHeaderException`; new rows without a known
  `origin` get none (skipped); old rows: id of digits = device song, else YouTube; `Duration` in
  seconds or `m:ss`), `groupPlaylistRows` (one playlist per playlist name, unnamed rows →
  the file's name, else
  `Playlist-<n>`), `readCsv` (RFC 4180 records via kotlin-csv).
  `PlaylistImporter.kt` — `PlaylistImporter` (Koin single in `KreateApplication`): `readCsv(uri)`
  and the shared entry point `importPlaylists(List<PlaylistToImport>, lookUp)` (any file format's
  rows → new local playlists; YouTube stored from the row (album/artists when it has their ids,
  `credits`), or with `lookUp` looked up again (`lookUpSongs`; dead ones go in from the file, ones
  missing from the answer searched with
  `replacementOf` through `BatchSearches` and replaced (`replaceSong` moves the old rows), else left
  out as `GONE`);
  device songs matched by
  `LocalMatcher` (id + title, else unique title / closest length), OpenSubsonic only stored ones
  or covers of the server set now), Exportify tracks matched by `matchTracks` (one per ISRC, else
  title/artists/length: the
  only stored YouTube song of that folded title within 2 s, else `songMatching` through
  `BatchSearches` with a `SearchCount` on the placeholders; stops on unreachable, 429 or 5 refusals
  in a row → `NOT_SEARCHED`, no match → `NO_MATCH`), `tracksToMatch` (the dialog's count),
  `PlaylistImportReport` with `ImportSkip` reasons,
  `importMessage`/`importFailedMessage`; `importing` (`ImportingPlaylist`s not made yet with their
  `ImportStep`, the Playlists tab's pinned placeholders; `PlaylistImportReport.outcomes` =
  per-playlist `PlaylistOutcome` with `FailedSong`s for the notifications), `startImport(uri)` →
  `ImportTask`
  (one import's placeholders: file name while reading, then one per playlist; steps, done, close).
  Used by the home Playlists tab sheet's "Import from file"
  entry.

notification/

- `Notifier.kt` — message bus (Koin single): `Message` (text, `MessageDuration`, dismissable,
  action, `channel`), `post()` from any code. With no collector (app not started) a message with a
  channel goes to `SystemNotifications`, one without is dropped.
- `AppChannel.kt` — notification channels (PLAYBACK default importance, OTHER default (one-off
  notices only, e.g. a failed restore — not every backup), LOCAL_SCAN low, PLAYLIST_IMPORT low,
  SLEEP_TIMER low +
  `isSilent` (no sound/vibration, `notificationBuilder` sets `setSilent`); the brief foreground
  notification of playback resumption goes to OTHER with `setSilent(true)`, the old `resume` channel
  is deleted at start; message ids 2000+ordinal,
  newest replaces), `Context.createNotificationChannels()` called in `KreateApplication.onCreate`.
- `LocalScanNotifications.kt` — LOCAL_SCAN's two notifications (Koin single): scan (3000:
  indeterminate bar → "N songs scanned") and loudness (3001: `measured/total` bar, ≤1 update/s → "
  analyzed/total"; failed songs as group children 3100+, max 40, summary subText is the group
  header). `clearStale()` at app start drops ongoing progress left by a killed process. Called from
  `LocalMusicWorker`/`LoudnessWorker`.
- `PlaylistImportNotifications.kt` — PLAYLIST_IMPORT channel (low) (Koin single, fed by
  `ImportTask`): one ongoing progress notification per import (7000, tag per import id: the names,
  the step or "Searching x of y" with a determinate bar, ≤1 count update/s), then one result per
  playlist (7001, tag per placeholder key): "imported successfully" with "All N songs were found on
  YouTube Music" (every song looked up or searched) or "were imported", else "imported with
  errors"/"couldn't be imported" as a group summary whose subText (the only text shown) is that
  title, each song not imported a child (7002, max 20, playlist name as subText, reason as text);
  `clearStale()` at app start. The import's snackbar has no channel, so no OTHER notification.
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

recognition/

- `SongRecognizer.kt` — `SongRecognizer` (start/cancel/`finish(heard)` → `RecognizedSong?`, Koin
  single) and `RecognizedSong` (title, artist, album, cover, `explicit`, `pageUrl`, `searchQuery`).
- `ShazamSongRecognizer.kt` (under `app/src/nonfree/`, not in foss) — the bound `SongRecognizer`:
  records the microphone (`AudioRecord`,
  16 kHz mono 16-bit, 44.1 kHz when 16 kHz isn't offered, 20 s at most) on a thread of its own,
  `finish` hands the raw PCM and the app's locale to `:shazam`'s `Shazam` (the search query is
  the `q` of Shazam's YouTube Music search, else title + artist); a microphone that
  couldn't start throws on `finish`.

download/

- `LegacyDownloads.kt` — songs the old Kreate app had downloaded (Koin single): the id list kept in
  `noBackupFilesDir/legacy_downloads.json`, `load` (offers the ids some user has in `user_song`),
  `downloadAll` (items built from the library, nothing looked up: `SongDownloads.downloadUnmarked`,
  then `UserSongRepository.markDownloadedForEveryUser`), `details` (the picker's lookup, 100 ids a
  request), `download(items)`, `dismiss`;
  `parseLegacyDownloadIds`. Dialog and picker: `ui/screen/account/LegacyDownloads*.kt`.
- `SongDownloads.kt` — `SongStorage` (CACHED/DOWNLOADING/PAUSED/DOWNLOADED; `resume`),
  `overview()` (`DownloadsOverview`: the active user's whole files with size and quality, unfinished
  downloads as `UnfinishedDownload`/`DownloadStage`, `DownloadWait`, this user's and all users'
  bytes; polls the main-thread `currentDownloads` every second while one runs or waits),
  `pause`/`retry` (the notification's actions from the app); failure reasons kept per download for
  the app's run from the `DataSourceException` reason (`playbackReasonRes`),
  `DownloadPlan` (cached vs
  set quality, `movesCache`), `SongDownloads` (Koin single): `songs` map by song address (badges,
  menu), `plan`/`download(item)`/`item(id)`/`delete`/`deleteAll` (every user's, Media3 remove-all)/
  `removeCached`, listens to the
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
  an album's songs show only the artists, an artist's only the album. `songsOf(tab, item)`: a
  collection's playable songs, unshuffled, for `VoiceSearch`.
- `VoiceSearch.kt` — "play … on Kreate": `queue(query, extras)` searches the origins of
  `AUTO_VOICE_SEARCH_ORDER` not in `AUTO_VOICE_SEARCH_SKIPPED` in turn (LOCAL = the library via the
  repos' `listSearch`, YouTube search + signed-in playlists, OpenSubsonic `searchLibrary` +
  playlists), first origin with a match wins; best match by `nameScore` (same 3 / starts 2 / holds
  1, case ignored, +1 for the heard artist), ties songs > artists > albums > playlists; a song plays
  with radio, a collection with all its songs. `VoiceRequest` reads the Assistant's
  `MediaStore.EXTRA_MEDIA_*` (focus narrows the kinds). `anything()`: liked song listened longest →
  song listened longest → random library song, + radio. `voiceSearchOrigins` decodes the prefs.
- `ArtworkProvider.kt` — non-exported `ContentProvider` (`<applicationId>.artwork`) serving covers
  as JPEG through the app's Coil loader (http/https/content and the player's cover files,
  `isServedSource`); `Context.autoArtworkUri` (`content://<pkg>.artwork/cover?source=…`, idempotent;
  Auto's browse items, `smallCover` at `SMALL_COVER`), `Context.grantArtwork(package)` (prefix read
  grant to the browser), `MediaItem.withServedArtwork` (queued songs, address also has
  `song`/`origin`/`album`: the player's `songThumbnailModels` at `HEADER_ARTWORK`; applied by
  `PlaybackService.queued` and `PersistentQueue`'s restore, so Auto's player, the notification and
  the lock screen load covers here), `Uri.coverSource()` (unwrap, `null` for a device song queued
  without a cover; used by `thumbnailModel`/`songThumbnailModels`), `Context.smallCoverJpeg`
  (`smallCover` as bytes, for the resumption card's `artworkData`). Manifest also declares Media3's
  `MediaButtonReceiver`.
  Manifest: service actions `MediaLibraryService` + `MediaBrowserService`,
  `com.google.android.gms.car.application` → `res/xml/automotive_app_desc.xml`; `KreateActivity`
  takes `MEDIA_PLAY_FROM_SEARCH` and starts `PlaybackService` with `VOICE_SEARCH_ACTION`.

preferences/`PreferenceDsl.kt` — `Preference<T>`/`Secret` `.collectAsStateWithLifecycle()` for
Compose.

utils/ — `AndroidVersion.kt` (`IS_ANDROID_<n>_AND_UP` version checks lint reads as SDK_INT checks),
`PermUtil.kt` (audio/notification permission helpers), `ScrollbarModifier.kt` (
`Modifier.verticalScrollbar`).

English texts: `res/values/*.strings.xml` by kind (`common` app name, sources, units; `statuses`;
`settings`; `actions` buttons and menus; `screens` titles, tabs, section headers, screen texts;
`semantics` icon descriptions; `wizard`; `dialogs`; `messages` errors and snackbars; `statistics`;
`notifications`; `constants` enum values), `res/values/plurals.xml`; which prefix goes where:
`.claude/rules/strings.md`.

res: `res/resources.properties` (default `en-US`, for the generated locale config),
`res/raw/terms_of_service.txt`, `res/raw/keep.xml` (keeps AboutLibraries' generated
`raw/aboutlibraries` through resource shrinking; overrides for POMs lacking details in
`app/aboutlibraries/licenses/`), `res/font/inter.ttf`; source SVGs for launcher/splash in
repo-root `assets/`.
