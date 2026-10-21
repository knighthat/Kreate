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
