# File index: `:app` playback/

Paths are relative to `app/src/main/kotlin/me/knighthat/kreate3/`; checked against the tree on
2026-10-20, verify a path still exists before relying on it. Map of the parts:
`.claude/file-index.md`.

playback/

- `PlaybackErrors.kt` — `PlaybackException.userMessage(item)`, `cantPlayMessage(item, reason)`;
  `PlaybackRecovery` posts them to `Notifier` (dismissable); `togglePlay` re-prepares after a
  failure.
- `PlaybackRecovery.kt` — service-side listener: on a player error, or loading longer than
  `PLAYBACK_STALL_SECONDS` while playWhenReady (stall → `player.stop()`), follows
  `PLAYBACK_FAILURE_ACTION`: retry once per song (`prepare()`, reset on transition), skip to the
  next (at most `MAX_SKIPS_IN_A_ROW` 3 until one plays), or stay idle; posts the reason unless it
  retries.
- `PlaybackService.kt` — `MediaLibraryService`, owns the player + notification; `SessionCallback`
  adds `withSongUri` to queued items, answers Android Auto's browsing from `AutoLibrary`
  (`onGetLibraryRoot`/`onGetChildren`/`onSubscribe`, no recent root) and turns a tapped Auto node
  into its queue in `onSetMediaItems` (items without `EXTRA_ORIGIN` whose id parses); follows
  `PLAYBACK_PRELOAD_SECONDS` into the player's preload. `onPlaybackResumption` plays
  `PersistentQueue.saved()` again when play comes with nothing queued (headphones, system media
  controls; `MediaButtonReceiver` in the manifest), going foreground first under
  `RESUME_FOREGROUND_ID` 5000 (`AppChannel.RESUME`); for System UI's card after a reboot only the
  playing song, cover as `artworkData`.
- `PlaybackController.kt` / `PlaybackControllerImpl.kt` — `PlaybackState` (incl.
  `repeatMode: RepeatMode` OFF/ONE/ALL, `isShuffled`) + interface screens use (`queue` in its own
  order, `shuffledOrder` = queue positions in play order while shuffled else `null`, walked from
  the controller's timeline by `Timeline.shuffledOrder()`; `toggleShuffle`,
  `cycleRepeat`, `sleepTimer` (read from session extras), `startSleepTimer`/`changeSleepTimer` (
  custom session commands), `audioSessionId()` from session extras); impl wraps a `MediaController`
  on the main thread.
- `SleepTimer.kt` — `SleepTimer` (Running endsAt elapsedRealtime / Paused remaining / EndOfSong(
  isPaused)), `SleepTimerAction` (TOGGLE_PAUSE/ADD/REMOVE/STOP), `changedBy(step, add)` (
  UNTIL_SONG_ENDS → EndOfSong either way, EndOfSong takes no minutes, remove can't take all),
  session-extras codec. `SleepTimerRunner.kt` — service-side timer per session (countdown on the
  player scope, EndOfSong via `pauseAtEndOfMediaItems`, dropped when the queue empties or it ended
  while nothing played), `SleepTimerCommands` (session commands, only the app's own controllers get
  them in `SessionCallback.onConnect`), notification button intents handled in
  `PlaybackService.onStartCommand`; running out → pause + `finished` notification whose Restart (
  `SleepTimerCommands.RESTART`, extras: duration + ended song) replays via `restart()` (foreground
  first, see `.claude/docs/android-gotchas.md`). `SleepTimerNotification.kt` — id 4000, channel
  SLEEP_TIMER, DecoratedCustomViewStyle + `res/layout/sleep_timer_notification.xml` (Chronometer
  countdown, −/pause/+/stop; icons tinted `@color/notification_icon` light/night —
  `setColorStateListAttr(textColorPrimary)` resolved in the app's light theme and turned them
  black); swiped away = timer goes on, every user change re-posts, the remove-button refresh only
  updates a showing one.
- `AudioEffectSession.kt` — per session: broadcasts OPEN/CLOSE_AUDIO_EFFECT_CONTROL_SESSION, puts
  the audio session id in the session extras (`EXTRA_AUDIO_SESSION_ID`).
- `KreatePlayer.kt` — `ExoPlayer` delegate wrapper; enforces repeat-one ⇄ shuffle exclusion for
  every controller (app and system media controls).
- `DataSourceResolver.kt` — `QueuedSong` (`kreate://<ORIGIN>/<id>`, `quality`, `cacheKey` = uri
  `#QUALITY`), `MediaItem.withSongUri`, resolver: queued address → provider URL at read time in the
  copy's quality, YouTube stream cache/refusals, OpenSubsonic `ServerStream` reuse for seeks,
  `answered` (Content-Type) (`.claude/docs/playback-pipeline.md`).
- `SongFormatRecorder.kt` — stores YouTube/OpenSubsonic `SongFormat` (gain) before a song is read,
  called by the resolver; OpenSubsonic: `raw` row from getSong once + transcoded `{codec}-{kbps}`
  rows, `recordAnswered` tags from Content-Type; formats of unstored songs kept in memory
  (`pendingFormatsOf`/`pendingFormats`) until `flush`.
- `StreamQuality.kt` — `Context.streamQuality(origin, youTube)` (Wi-Fi/mobile by metered network,
  YouTube `_PREMIUM` pair while signed in, LOCAL = ORIGINAL), `Context.isOnline()`.
- `CacheCopies.kt` — picks the per-quality cache copy at song start (best whole ≥ setting → part ≥
  setting → new copy → offline best), pins it for seeks, `readEnded` drops lower copies once the
  read one is whole.
- `SongDataSource.kt` — `songDataSourceFactory`: local → direct, remote → downloads (read-only) →
  cache → ResolvingDataSource → `StreamDataSource` (app/OpenSubsonic OkHttp, 403 → `refused`, server
  Content-Type → `answered`); router sets the `CacheCopies` copy as DataSpec key + customData,
  OpenSubsonic direct play skips the cache.
- `SongCaches.kt` — the two SimpleCaches (streamed cache in cacheDir, downloads in
  noBackupFilesDir); `copies(song)` (quality → whole?), `drop`, `cachedLength`.
- `LoudnessNormalizer.kt` — volume change in dB from a song's gain (`normalizationDb`,
  `cutToVolume`, `replayGainLoudness`), from the recorder's in-memory formats first, then the DB;
  to `LOUDNESS_TARGET_LUFS`, OpenSubsonic `raw` gains read against `OPEN_SUBSONIC_REPLAY_GAIN_LUFS`,
  unchanged while `LOUDNESS_NORMALIZATION` is off (all followed live).
- `PlaybackHistoryRecorder.kt` — AnalyticsListener created in `PlaybackService`:
  `PlaybackStatsListener` play time (no pauses/loading, wall-clock) → `recordPlay` per session ≥
  `Preferences.PLAYBACK_HISTORY_MIN_SECONDS` (Player settings → History, default 1 s), nothing while
  `PLAYBACK_HISTORY_PAUSED`; on STATE_IDLE/ENDED saves the ongoing session early and `updatePlay`s
  that row when it ends; writes in the global scope (last ones come after release).
- `PlayedSongRecorder.kt` — stores a YouTube/server song once it starts playing (catalog + library,
  no like), asking its source again; then `SongFormatRecorder.flush` and `onStored` (queue re-save).
- `Radio.kt` — songs like a song, per origin: local ones scored from the user's library (
  `UserSongRepository.similar`), others asked from their source; nothing stored. `RADIO_SIZE` (30).
- `PersistentQueue.kt` — saves the player queue to `persistent_queue` (whole queue on playlist
  change; position on transition/pause/seek and every `PERSISTENT_QUEUE_SAVE_INTERVAL` s while
  playing) when `PERSISTENT_QUEUE_ENABLED`; restores it unprepared in `PlaybackService.onCreate` if
  the player is empty; `saved()` (also for playback resumption) gives the queue as items;
  `release()` before the player's. Table: PK (user_id, ordinal), `position` =
  ms of the one playing row (single-statement CASE update keeps it unique), songs missing from
  `song` skipped on insert.
- `PlaybackLifecycle.kt` — `restartAround { }`: disconnects the controller,
  `PlaybackService.stop()` (session released, its write job joined, 5 s cap then cancelled), runs
  the block, reconnects; service makes no session while `isStopped`. `restartPlayer()` (global
  scope) does the same with no block, handing the queue to the next player as a `CarriedQueue`;
  `restartNeeded` = the session's player was made with another `PlayerBuild` than now (Player
  settings' top bar restart button).
- `PlayerBuild.kt` — the settings a player is made with (`keepPlayed`), `now()` / `changes`.
- `CarriedQueue.kt` — a player's queue, shuffled order, song + position, repeat, prepared and
  playWhenReady, applied to the next player in `createSession` before `PersistentQueue` restores.
- `PlaybackModule.kt` — Koin for player/controller and `AutoLibrary`;
  `ProgressiveMediaSource` (128 KiB check interval), `preloadConfiguration(seconds)`,
  `SongLoadControl` (back buffer from the `PlayerBuild` parameter of the player factory).
- `SongLoadControl.kt` — `DefaultLoadControl` buffering by time: min = max = 60 s ceiling, the
  buffer handed to the parent scaled to `PLAYBACK_BUFFER_AHEAD_SECONDS` (read each call; `0` =
  whole song, parent loads until its heap headroom check stops it);
  `shouldStartPlayback` own: `PLAYBACK_BUFFER_TO_START_SECONDS` (≤ ahead), ≥ 2 s after running out;
  `KEPT_PLAYED_MS` 60 s back buffer.
