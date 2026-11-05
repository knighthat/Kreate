<!--
  ~ Kreate
  ~ Copyright (C) 2026 KnightHat
  ~
  ~ This program is free software: you can redistribute it and/or modify
  ~ it under the terms of the GNU General Public License as published by
  ~ the Free Software Foundation, either version 3 of the License, or
  ~ (at your option) any later version.
  ~
  ~ This program is distributed in the hope that it will be useful,
  ~ but WITHOUT ANY WARRANTY; without even the implied warranty of
  ~ MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  ~ GNU General Public License for more details.
  ~
  ~ You should have received a copy of the GNU General Public License
  ~ along with this program.  If not, see <https://www.gnu.org/licenses/>.
 -->

# Logging

Kermit (`co.touchlab:kermit`) everywhere, never `android.util.Log`. The setup is
`logging/AppLog.kt`.

- **Calls:** `Logger.w(throwable, TAG) { "text" }`, `Logger.i(tag = TAG) { "text" }`; messages are
  lazy
  lambdas. One `TAG` constant per file, named after it.
- **Kermit 2.2 overloads:** `Logger.w("Tag") { }` (tag first) is deprecated. Pass the throwable
  first, `Logger.w(e, TAG) { }`, or name the tag, `Logger.w(tag = TAG) { }`.
  `Logger.log(severity, tag, throwable, message)` is fine.
- **English only**, never a string resource: logs are for the maintainer's bug reports.
- **Levels:** the user picks the least important level kept (`GlobalPreferences.LOG_LEVEL`, default
  WARNING). Failures that were handled are `w`, ones that stop what was asked `e`, what happened
  normally (a task ran, a song changed, a sync stored N items) `i`, steps for finding a fault `d`,
  noise `v`. Migrations (preferences, database) are logged as warnings by choice, so they show at
  the
  default level.
- **Never log:** passwords, API keys, tokens, cookies, usernames, account/channel ids, PO tokens,
  visitor data, request or response bodies (OpenSubsonic sign-ins carry passwords in them), what
  the user said to a voice search, addresses with their query. Name songs by id. `redact()`
  (`Redaction.kt`) scrubs every written text as a second line of defense, it doesn't excuse a
  careless
  message.
- **Files:** `kreate.log` and `kreate.N.log` in `externalCacheDir/logs`
  (`Android/data/<id>/cache/logs`);
  rolls over at `LOG_ROLLOVER_SIZE`, keeps `LOG_KEEP` rolled files. Written on a thread of its own,
  until the settings load messages wait and are then filtered. Crash logs go to
  `getExternalFilesDir()/crashlogs`, excluded from backup (both XMLs, domain `external`), deleted at
  startup after `CRASH_LOG_RETENTION_DAYS`, written whatever the level is.
- **Recent activity:** Kermit's minimum is the user's level or Info, whichever is more detailed, and
  the writer applies the user's level to Logcat and the files itself. Everything that passes is kept
  in memory (`AppLog.recent`, last 200 entries, never written to the files for it) and goes into the
  crash log as "Recent activity"; "Earlier in this session" holds what the files kept before that.
  Debug builds fill it fast with Coil's per-image lines.
- **Crash screen:** `CrashHandler` saves the log, starts `CrashActivity` in the `:crash` process and
  kills the app's. `KreateApplication.onCreate` returns early in that process: nothing there may use
  Koin or `Preferences`. A crash while the app is in the background may not be allowed to open the
  screen (Android 10+); the crash log is still saved.
- **Network:** Ktor `Logging` through `appLogging`: debug builds log address, headers (secret ones
  masked) and status at `d`, release builds one line per request and answer at `i`; never a body.
  Streams read by Media3 go through OkHttp directly and aren't covered.
- **Coil:** `KermitCoilLogger`: debug builds from Debug with stack traces, release builds warnings
  and
  errors in one line.
- **Playback:** `PlaybackLogger` (analytics listener), `DataSourceResolver`, `YouTubeStreams`,
  `PlaybackService` lifecycle.
- **Background:** workers wrap `doWork` in `logged(TAG)`; downloads, library syncs, backups and
  scheduling log their steps.
