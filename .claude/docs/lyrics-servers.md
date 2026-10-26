# Lyrics servers

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
