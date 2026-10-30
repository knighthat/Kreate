# Running, inspecting and tracing on the emulator

Ask the maintainer before using any device, emulated or real: installing or launching builds,
running tests, driving the UI, reading its logs, settings, screen or database, and changing its
state (installing over their build, clearing the app's data, wiping the DB, granting or revoking
permissions). A maintainer may allow some of it on their own machine; keep that answer in your
local memory, never in the repo. Devices hold the maintainer's data and they may use them at the
same time. State that changes between your own actions may be theirs: compare logcat times with
your actions before suspecting the code, and don't restore what they changed. Never mention or
save which account or channel a device is signed in as.

## Launching

- Launcher activity: `adb shell am start -n me.knighthat.kreate3/.KreateActivity`.
- Install your own build before judging a device result:
  `ANDROID_SERIAL=emulator-5554 ./gradlew :app:installGithubUniversalDebug`. Without
  `ANDROID_SERIAL` Gradle installs on every attached device, a real phone included. Target the
  emulator first; with no emulator attached, ask the maintainer before installing anywhere. The log
  says how many devices got it ("Installed on 1 device").
- After a fresh install and `am start`, the window can stay white or garbled while the UI tree
  (`ui.sh dump`) is fine: the splash's exit animation is stuck (`dumpsys SurfaceFlinger --list`
  shows an `animation-leash of starting_reveal`). Going home and `am start` again clears it. Not
  traced; seen only on the emulator so far.
  The installed app may be an older Android Studio build (a `code_cache/.studio` agent in logcat).
- After clearing data (`pm clear`, reinstall, deleted DB), skip the setup wizard with
  `adb shell am start -n me.knighthat.kreate3/.KreateActivity -a me.knighthat.kreate3.SETUP_WIZARD_SKIP_ACTION`
  (only on a fresh launch). `pm clear` also revokes permissions: grant them again with
  `adb shell pm grant me.knighthat.kreate3 android.permission.READ_MEDIA_AUDIO`.
- Permissions change between sessions: check `dumpsys package me.knighthat.kreate3 | grep granted=`
  before blaming the code for a scan that does nothing. `LocalMusicWorker` logs
  `Found N songs on the device`; WorkManager's `WM-*` tags log the work ids.

## Database

- Debug builds allow `run-as`, and plain emulator images ship `sqlite3` (Google Play images don't:
  force-stop, copy `kreate.db`, `-wal` and `-shm` out with `adb exec-out run-as … cat` and query on
  the host):
  `adb shell run-as me.knighthat.kreate3 sqlite3 databases/kreate.db "\"SELECT …;\""` (the inner
  quotes are escaped because the query goes through two shells). The default profile's user id is
  `default`; library rows are in `user_song`/`user_album`/…, and the Songs tab reads
  `user_song_view`.
- Force-stop (`adb shell am force-stop me.knighthat.kreate3`) before any `sqlite3` on the DB, even a
  read: one checkpointed and deleted `kreate.db-wal` under the running app, whose later writes then
  went to the unlinked file and were lost.
- Wiping only the DB (keeps preferences, secrets and sign-ins, unlike `pm clear`): force-stop, then
  `adb shell run-as me.knighthat.kreate3 rm -f databases/<file>` once per file (`kreate.db`, `-wal`,
  `-shm`, `.lck`; a single `sh -c 'rm -f a b c'` fails), then launch with the skip-wizard action.
- Native crashes or "file is not a database" on a valid file: Android Studio's Database Inspector,
  see `android-gotchas.md`.

## Driving the UI

- `.claude/scripts/ui.sh dump [pattern]` lists the screen as `text | content-desc | bounds`;
  `ui.sh tap <label> [long]` taps (or long-presses) the node whose text or description is exactly
  `<label>`. It picks the attached emulator and refuses any other device. Tapping a filter chip
  that is already on turns it off, dump before tapping. Under it: `uiautomator dump
  /sdcard/ui.xml`, find the node by `text=`/`content-desc=`, tap the centre of its `bounds`.
  uiautomator's `enabled=` on Compose `IconButton`s isn't reliable; check a screenshot and a tap.
- Long-press: `adb shell input swipe x y x y 800`.
- The 5-minute library throttle is in memory: force-stop + relaunch makes a tab sync again at once.
- Killing the app for cold paths: `am kill` does nothing while `PlaybackService` is foreground. Use
  `adb shell run-as me.knighthat.kreate3 kill -9 $(adb shell pidof me.knighthat.kreate3)`; the
  service usually restarts (START_STICKY) and the queue comes back.
- adb sometimes reports the emulator `offline` for a moment; retry.
- Media notification tap: `adb shell cmd statusbar expand-notifications`, screenshot, tap the
  card's title area. For the cold path, swipe the app away in recents first: the activity is gone
  while `PlaybackService` keeps playing. `am start … -f 0x24000000` with the same action stands in
  for the tap; its "Activity not started, its current task has been brought to the front" warning
  doesn't mean the intent was dropped, `onNewIntent` still runs.

## Previews and animations

- Render a `@Preview` without Studio: install the debug build, then
  `adb shell am start -S -W -n me.knighthat.kreate3/androidx.compose.ui.tooling.PreviewActivity --es composable <package>.<File>Kt.<Preview>`
  and `adb exec-out screencap -p`. Private previews work; keep `-S` or the previous preview stays.
- Animations: `adb shell screenrecord --time-limit 4 /sdcard/x.mp4` in the background while doing
  the gesture, pull it, then tile the frames that changed:
  `ffmpeg -i x.mp4 -vf "mpdecimate,setpts=N/FRAME_RATE/TB,scale=300:-1,tile=10x3" -frames:v 1 out.png`.
  screenrecord only writes changed frames, so frame numbers don't map to time. A temporary `Log.d`
  of the values driving the animation often finds the cause faster.

## In-app instrumented tests

They run inside the app's process, so Koin, Preferences and Secrets are live:
`./gradlew :app:assembleGithubUniversalDebug :app:assembleGithubUniversalDebugAndroidTest`,
`adb install -r` both APKs, then
`adb shell am instrument -w -e class <fqcn> me.knighthat.kreate3.test/androidx.test.runner.AndroidJUnitRunner`,
afterwards `adb uninstall me.knighthat.kreate3.test` (test APK only). **Never**
`connectedGithubUniversalDebugAndroidTest`: it uninstalls the app and wipes its data. Log results
with a `LIVE` tag and read `adb logcat -d -s LIVE:I`. Read secrets inside the test, never copy them
off the device. A player failure can be triggered with `PlaybackController.playAll` on a `file://`
URI that doesn't exist.

Image benchmarks the same way: time `SingletonImageLoader.get(context).execute(…)` with
`memoryCachePolicy(DISABLED)`. The emulator's hardware bitmap upload is slow, so compare decode
paths with hardware bitmaps off.

## Release builds

- The release APK (`app/build/outputs/apk/githubUniversal/release/kreate-universal.apk`) is
  unsigned without the production key env vars. Sign it with the debug key to install over a debug
  build: `zipalign -f 4`, then
  `apksigner sign --ks ~/.android/debug.keystore --ks-pass pass:android`,
  then `adb install -r`.
- `apkanalyzer` may be missing: use `unzip -v`, `dexdump` and `aapt2` from
  `$sdk.dir/build-tools/<latest>/`. Classes keep their names (`-dontobfuscate`), so grouping dexdump
  class sizes by package gives code size per library.
- R8 reports in `app/build/outputs/mapping/release/`: `seeds.txt`, `usage.txt`, `configuration.txt`
  (merged rules tagged by library), `resources.txt`.
- To try build changes without touching the repo, `rsync` the project (minus `build`, `.gradle`,
  `.git`, `.kotlin`) to a scratch dir and build there.

## Test audio

Generate tracks with `ffmpeg -f lavfi -i "sine=frequency=440:duration=20"` plus
`-metadata title=…` (Opus needs `-ar 48000`), push to `/sdcard/Music/<dir>/`, then rescan with
`adb shell content call --method scan_volume --uri content://media --arg external_primary`.
MediaStore files `.webm` as video and the scanner skips WAV. Delete the files and rescan afterwards.
Check playback with `adb logcat | grep onSessionPlaybackStateChanged` (`state=PLAYING(3)` and a
rising position), not `dumpsys media_session`.

## Jank tracing with Perfetto

- The emulator has `/system/bin/perfetto`. Push a text config to `/data/misc/perfetto-configs/`
  (via `/data/local/tmp`), run
  `perfetto --txt -c <cfg> -o /data/misc/perfetto-traces/x.pftrace --background`, then launch.
- Useful sources: `linux.ftrace` (sched_switch/wakeup + atrace gfx/view/dalvik, `atrace_apps` =
  package), `android.surfaceflinger.frametimeline` (real per-frame jank, works on release builds),
  `linux.perf` callstack sampling (debug only; Java stacks didn't unwind on x86_64). Use
  `fill_policy: DISCARD` for a launch trace (RING_BUFFER overwrites the launch).
- Analyse on the host with the `perfetto` pip package (+ pandas) in a throwaway venv. What worked:
  main-thread `thread_state` Running vs `R` (runnable = starved of a core), per-thread `sched` CPU
  during a frame, `Choreographer#doFrame` slices (debug only).
- Release build on a fresh DB: install debug, wipe the DB, `pm revoke` READ_MEDIA_AUDIO (installing
  over debug auto-starts the app), install the signed release, force-stop, `pm grant`, trace,
  launch; reinstall debug after. The emulator has 4 cores and its graphics HAL makes cover uploads
  look slow.

## Sign-ins

YouTube's account switch is off by default, so the emulator is usually signed out of YouTube and
signed-in screens (library save, subscribe, likes sent) don't show. When a test needs a sign-in the
emulator lacks, ask the maintainer to sign in there first, and undo what the test changed on the
real account afterwards.
