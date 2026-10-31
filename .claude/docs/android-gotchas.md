# Android platform gotchas

## Starting playback from the background (Android 16)

Seen on API 36, targetSdk 37, building the sleep timer's Restart button:

- `player.play()` from a service the user didn't bring to the front logs
  `AS.HardeningEnforcer: AudioHardening focus request … ignored` and ExoPlayer pauses itself.
- Media3's `MediaSessionService` goes foreground only after playback starts, too late. Its
  `onPlayRequested` foreground trick covers API 31–32 only.
- What works: `ServiceCompat.startForeground(service, ownId, notification,
  FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)` **before** `play()` (a notification-button PendingIntent
  grants a ~30 s foreground-start allowance), then let Media3 take over.
- Use a notification id of its own (sleep timer: 4001): when Media3 calls `startForeground(1001, …)`
  the system **removes** the previous foreground notification. If nothing plays, `stopForeground`
  it yourself.
- Implemented in `SleepTimerRunner.restart` + `PlaybackService.startForegroundWith`. Media3 sources
  for reading internals are in the Gradle cache (`media3-session-<version>-sources.jar`).

## Android Auto through a Media3 `MediaLibraryService`

Learned building `auto/` and `PlaybackService`'s library callback (media3 1.11.1, read from the
sources jar; not yet checked in Auto or the Desktop Head Unit):

- Auto is a **legacy** browser (`MediaBrowserCompat`): its taps arrive as `playFromMediaId`, which
  Media3 turns into `onSetMediaItems` with a single item holding only the media id. So the id must
  say how to find the item again (`AutoNode`), and the callback tells it apart from the app's own
  items (which carry `EXTRA_ORIGIN`).
- Voice requests ("play … on Kreate") reach the session as `playFromSearch`, which Media3 turns into
  `onSetMediaItems` with one item whose `requestMetadata` holds `searchQuery` (empty for
  "play music") and the Assistant's `MediaStore.EXTRA_MEDIA_*` extras. A failed future only logs;
  `sendError(controller, …)` is what puts a message on Auto's screen. The phone's Assistant sends
  `MEDIA_PLAY_FROM_SEARCH` to an activity instead (`KreateActivity` forwards it to the service).
- The default `onSubscribe` asks `onGetItem` for the parent and rejects it unless that returns a
  browsable item, and Auto subscribes to every list it opens. Either answer `onGetItem` for every
  parent or override `onSubscribe` (done: accept any parsable parent).
- Artwork must be a local `content://` or `android.resource://` URI, for browse rows (`iconUri`)
  **and** the playing item (`ART_URI`/`ALBUM_ART_URI`/`DISPLAY_ICON_URI`), per Google's "Display
  media artwork" page. Web, OpenSubsonic and MediaStore addresses show nothing, so covers go through
  `ArtworkProvider` (not exported; `grantUriPermission` with `FLAG_GRANT_PREFIX_URI_PERMISSION` to
  the browsing package).
- Media3 has no per-controller hook for metadata: the playing item's `artworkUri` goes to Auto
  as-is, and the app's own `MediaController` sees the same items. Anything Auto needs on the playing
  item lands in the shared queue items (hence `withServedArtwork` + `coverSource()`).
- Large browse results are cut by Media3 to fit the binder limit (`truncateListBySize`); Auto
  usually asks for page 0 with `Int.MAX_VALUE` items.
- Media3 decodes a bitmap for a browse row only when it carries `artworkData`; with only
  `artworkUri`, nothing is decoded in the service.
- Root children become Auto's tabs (up to 4); `EXTRAS_KEY_CONTENT_STYLE_BROWSABLE`/`_PLAYABLE` on
  a browsable item set its children's list or grid layout.
- Lint's `MissingIntentFilterForMediaSearch` fires once the app is an Auto media app; it's ignored
  in the manifest until voice search exists.

## Intents that ask the UI to do something

`KreateActivity` calls `setContent` in `onStart`, and an intent to a running activity reaches
`onNewIntent` before `onRestart`/`onStart`. A one-shot event (a `Channel`, a consumed flag) that
the old content takes is lost when `onStart` replaces the content and cancels its effect, e.g.
mid-animation. Seen with the media notification's `OPEN_PLAYER_ACTION`: the app came back with the
player collapsed. Keep the request as state until the UI has acted on it, then clear it from the
UI (`KreateApp(openPlayer, onPlayerOpened)`).

## Media3 playback resumption

Learned adding `onPlaybackResumption` (media3 1.11.1, read from the sources jar; not yet checked on
a device):

- It's on only while `androidx.media3.session.MediaButtonReceiver` is in the manifest
  (`MediaSessionLegacyStub.canResumePlaybackOnStart`). Without it, Media3 refuses System UI's
  recent root, so the system's media controls have no card to resume from.
- System UI's `isRecent` root and its children are answered by Media3 before the library
  callback's `onGetLibraryRoot`/`onGetChildren`, from `onPlaybackResumption(isForPlayback = false)`
  (first item at the start index). The callback's own `isRecent` answer only reaches other apps.
- Android Auto (phone projection, DHU 2026-10-30) never asked for the recent root. With "Start music
  automatically" on it sends a plain play on connect, which Media3 turns into
  `onPlaybackResumption(isForPlayback = true)`; with it off it shows a "Tap to open" card with a
  disabled play button and asks nothing.
- That item reaches System UI as a legacy `MediaDescription`: a cover in `artworkData` is decoded
  in the app and sent as a bitmap, while an `artworkUri` would have to be readable by System UI,
  whose package never reaches `grantArtwork`.
- The receiver accepts only play/play-pause/headset-hook keys on API 26+ and starts the service
  with `startForegroundService`, so the service must call `startForeground` in time even when
  nothing gets played. Media3 does so only once the player is buffering, after `play()`: too late
  for Android 16's audio focus rule (see the first section), so `PlaybackService` goes foreground
  itself first (`RESUME_FOREGROUND_ID`) and leaves it if Media3 hasn't taken over
  (`isPlaybackOngoing`).

## Custom (RemoteViews) notifications

Learned on `SleepTimerNotification` (DecoratedCustomViewStyle +
`res/layout/sleep_timer_notification.xml`):

- A standard notification shows at most 3 action buttons; 4+ need a custom layout.
- Icon tint: `android:tint="?android:attr/textColorPrimary"` and
  `RemoteViews.setColorStateListAttr(…, textColorPrimary)` both resolve in the app's light theme →
  black icons on a dark shade. Working: `@color/notification_icon` in `values` and `values-night`.
  `TextAppearance.Compat.Notification.*` text gets the right color by itself. Untested: a dark shade
  on a light system theme.
- Lint flags `UseAppTint` and `ContentDescription` (set from code) as false positives: suppressed
  with `tools:ignore` on the root.
- Countdown: `setChronometer(id, elapsedRealtime + left, null, running)` +
  `setChronometerCountDown`;
  no per-second updates needed.
- A channel's sound applies only when the channel is first created; changing it needs a new channel
  id. Silence per notification with `setSilent`.
- Android bundles an app's notifications when 2+ show; a custom view's buttons are then hidden until
  expanded.

## Notification groups and Media3 downloads

Seen on API 36 building downloads (`DownloadNotifications`, `SongDownloadService`, 2026-10-27):

- A group summary's own action buttons never show from Android 7 on, collapsed or expanded, with
  one child or many. Put buttons on the children.
- When the foreground service whose notification is the group summary stops, Android removes the
  summary **and every child of its group**. Children that must outlive the service (a paused
  download) need a group of their own.
- Android sheds notification updates past about 5 per second per app (`Package enqueue rate is
  5.38. Shedding …` in logcat): the dropped update is simply lost. Coalesce bursts (post the
  summary once, a moment later). `getActiveNotifications()` also lags a notification just posted.
- Media3's `DownloadService` calls `resumeDownloads()` in its first `onCreate` of the process:
  re-apply an app-level `pauseDownloads()` after `super.onCreate()`. With no scheduler and
  requirements unmet (Wi-Fi only) it stays foreground and waits; once nothing runs (all paused) it
  stops itself. Its `addDownload` on an existing id replaces the request (new `customCacheKey`
  wins) and queues it again; the old key's bytes stay in the cache.
- A read-only `CacheDataSource` (no write sink) never writes the cache: hole spans are released,
  content metadata is only written while writing, and `NoOpCacheEvictor` needs no span touches.
- Starting a non-exported service from a notification button works from the background (the tap
  grants a short allowance); use `PendingIntent.getForegroundService` (API 26+) when the service
  must go foreground, `getService` otherwise. A `run-as … kill -9` keeps the app's notifications,
  `am force-stop` clears them.
- On a cold start through a notification button, a `MediaController` command can race
  `PersistentQueue`'s restore: an item added to an empty player is only there once the session
  handled it, so the restore still sees it empty. Wait for the restore first (`awaitRestored`).

## Android Studio Database Inspector

With App Inspection attached, the inspector hooks `BundledSQLiteConnection` and uses it from its own
thread (`Studio:ins_io0`): native SIGSEGV in `prepare` (the DB got recreated) and later
`SQLException: Error code: 26, file is not a database` on a valid file. Without the inspector the
same steps never crash. On such a report, check logcat for `StudioInspectors`/`Studio:ins_io0`
before suspecting app code. Workarounds: close the inspector while testing, or use
`AndroidSQLiteDriver` in debug builds (not applied).

## Opening links

Compose's `UriHandler.openUri` throws `IllegalArgumentException` ("Can't open …"), not
`ActivityNotFoundException`, when no app can open the link; uncaught it crashes the app. The
`Pixel_6a_GPlay` emulator has no browser, so every link tap hits it there. Open links through
`rememberOpenLink()` (`ui/component/OpenLink.kt`), which catches it and posts `error_no_browser`.

## The device's locale while the app has its own language

With a language picked in the app, `Locale.getDefault()` (and `LocaleList.getDefault()`, the app's
`Resources`) give the app's language, not the device's: Android 13+ sets it from the per-app locale,
older versions get it from `AppLanguage.wrap` (`Locale.setDefault`). For the device's own language
or region read `Resources.getSystem().configuration.locales[0]`, which keeps the system
configuration (`youTubeRegion()` in `YouTubeRegion.kt` does this for System). Only checked by
reading the code, not on a device.
