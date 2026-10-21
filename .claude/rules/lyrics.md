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

## Servers

How the servers behind the `:lyrics` sources behaved when probed. These explain choices in the
source classes that the code alone doesn't justify; re-probe before changing a source, and check
here first if lyrics stop appearing.

- **BetterLyrics** (`lyrics-api.boidu.dev/getLyrics?s&a&d&al`): answers `{"ttml":…}` only for songs
  already cached on its server. Anything else is 401 "Uncached queries require a valid API key via
  X-API-Key header". Kreate has no key, so 401 means "no lyrics". TTML carries
  `itunes:timing="Word|Line|None"`.
- **LyricsPlus** (`<mirror>/v2/lyrics/get?title&artist&duration(s)&album`): JSON with `type`
  (Word/Line/…), `metadata.source` (e.g. "Apple"), `lyrics[]` with `syllabus`. Mirrors are flaky:
  `lyricsplus.prjktla.my.id` works but sometimes 502s; `lyricsplus.binimum.org` answers 404 when its
  upstreams time out, so its 404 isn't reliable; `lyricsplus.atomix.one` didn't answer;
  `lyricsplus-seven.vercel.app` is disabled (402).
- **LRCLIB** (`/api/search`): entries have `duration`, `instrumental`, `plainLyrics`,
  `syncedLyrics`, plus newer `hasWordSync` and `lyricsfile`. No word-synced example was found, so
  word timing is detected by `<mm:ss.xx>` tags in `syncedLyrics`. It rate-limits and dislikes
  parallel requests, which is why `LyricsFinder` asks sources one at a time.
- Not checked yet: whether BetterLyrics or LyricsPlus send translations or romanization.
