---
paths:
  - "lyrics/**"
  - "app/src/main/kotlin/**/ui/screen/player/Lyrics*"
  - "app/src/main/kotlin/**/ui/screen/player/SyncedLyrics*"
  - "app/src/main/kotlin/**/source/youtube/YouTubeLyricsSource.kt"
---

# Lyrics

The `lyrics` table has one row per (song_id, source). Lyrics are stored raw and parsed when shown.

Loading rules (`loadLyrics` in `LyricsPage.kt`); don't change them without asking:

1. Opening the page: if the DB has a row for the source from settings (`LYRICS_SOURCE`), show it
   whatever its timing; else fetch from that source.
2. Changing source from the bar: same check for the picked source.

- No lyrics from a source: "<source> has no lyrics for this song." + "Press Change source below to
  pick another source." + a **Reload** button that fetches that source again, skipping the DB.
- `LYRICS_ALWAYS_LIVE` neither reads nor writes the DB.

Judgment calls the maintainer hasn't confirmed (ask once, the next time this area changes): a
fetch replaces that source's row; Retry also skips the DB; typed-word search asks every source and
saves under the one that answered; display is coarsened to `LYRICS_SYNC`; leaving the page resets
the bar.

`LyricsFinder` asks sources one at a time, never in parallel (LRCLIB rate-limits).

How the servers behind the `:lyrics` sources behaved when probed (re-probe before changing a
source, check it first if lyrics stop appearing): `.claude/docs/lyrics-servers.md`.
