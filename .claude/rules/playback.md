---
paths:
  - "app/src/main/kotlin/**/playback/**"
  - "app/src/main/kotlin/**/notification/**"
  - "app/src/main/kotlin/**/ui/screen/player/**"
  - "app/src/main/res/layout/**"
---

# Playback and notifications

- How a queued song becomes bytes (resolver, caches, quality copies, formats):
  `.claude/docs/playback-pipeline.md`. Check it before changing playback resolution.
- `PlaybackController` calls are fine from any thread; the `MediaController` stays on main.
- `play()` from the background (notification button, widget) on Android 16 is refused audio focus
  unless the service is already foreground: `startForeground` with an id of its own **before**
  `play()`. Details: `.claude/docs/android-gotchas.md`.
- `LoudnessNormalizer` cuts via `player.volume` and boosts via `LoudnessEnhancer`
  (`MAX_BOOST_DB = 6`). Don't change it unasked.
- A dislike, wherever it's made (player, song menu), takes every copy of the song out of the
  queue (`PlaybackController.drop`, the maintainer's default). The current one moves to the next
  song before it's removed: removing the current item while repeat-one is on makes ExoPlayer end
  the queue even with items after it. With no next song the player stops first.

## Sleep timer

- Notification buttons (play/pause, stop, add, remove) act on the **timer**, not the music.
- Add/remove amounts are settings (Player settings → Sleep timer), including "until the song ends".
- Swiping the notification away keeps the timer; it's posted again only on a user action, never on
  a timer-driven refresh.
- Nothing about the timer may wake a sleeping listener: every notification is silent (LOW channel,
  `setSilent`, `setOnlyAlertOnce`).
- When it runs out: pause, and replace the notification with a silent, swipeable "Sleep timer
  finished" one with a Restart button (same timer as started, past the song an end-of-song timer
  ended on). Restart goes foreground first under `FOREGROUND_ID` 4001, never 4000.
