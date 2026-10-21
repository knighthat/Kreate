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

## Android Studio Database Inspector

With App Inspection attached, the inspector hooks `BundledSQLiteConnection` and uses it from its own
thread (`Studio:ins_io0`): native SIGSEGV in `prepare` (the DB got recreated) and later
`SQLException: Error code: 26, file is not a database` on a valid file. Without the inspector the
same steps never crash. On such a report, check logcat for `StudioInspectors`/`Studio:ins_io0`
before suspecting app code. Workarounds: close the inspector while testing, or use
`AndroidSQLiteDriver` in debug builds (not applied).
