# File index: `:lyrics`

Paths are relative to `lyrics/src/main/kotlin/me/knighthat/kreate3/`; checked against the tree on
2026-10-20, verify a path still exists before relying on it. Map of the parts:
`.claude/file-index.md`.

Depends only on `:model` (api) plus Ktor/serialization/Koin.

- `LyricsQuery.kt` — song id + `Origin` + title/artists/album/duration.
- `LyricsSource.kt` — interface (`provider: LyricsProvider`, `name`, main-safe `find`) and
  `FoundLyrics` (raw `text` as the source gave it — plain/LRC/TTML/LyricsPlus JSON, `sync`,
  `source: LyricsProvider`, `credit`). The DB stores that raw text; parsing happens when shown.
- `LyricsContent.kt` — parsed model: `PlainLyrics`, `SyncedLyrics` of `LyricsLine` (start, end?,
  text, `words`, `background`) / `LyricsWord`; `coarsenedTo(sync)`.
- `LyricsParser.kt` — main-safe `parseLyrics(text)` sniffs the format (`<` TTML, `{` LyricsPlus,
  LRC, else plain; unreadable → plain), `parseClock`, `withEnds`. `LrcParser.kt` (LRC + enhanced
  LRC, offset, multi-time lines), `TtmlParser.kt` (SAX, namespace-aware: p/span, x-bg backing lines,
  itunes:timing), `LyricsPlusParser.kt` (syllabus, isBackground).
- `LyricsFinder.kt` — asks sources in order, never in parallel; `find(query, wanted, exclude)` stops
  at `wanted` sync, else most finely timed; `LyricsException` when nothing found and a source
  failed.
- Sources: `BetterLyricsSource.kt` (TTML), `LyricsPlusSource.kt` (mirrors, JSON),
  `LrcLibSource.kt` (/api/search, LRC) — see `.claude/rules/lyrics.md`. The YouTube one is
  `app/.../source/youtube/YouTubeLyricsSource.kt` (needs `:provider`), bound in `KreateApplication`'
  s Koin module.
- `LyricsModule.kt` — Koin `lyricsModule`: each source `bind LyricsSource::class`;
  `LyricsFinder(getAll<LyricsSource>().sortedBy { it.provider })`.
- App side: `ui/screen/player/SyncedLyricsList.kt` (LazyColumn, position every frame while playing,
  lit lines, word fill drawn with clipRect + drawText from the TextLayoutResult in the draw phase,
  auto-scroll to 1/3 unless dragged, tap line = seek); `ui/screen/player/LyricsPage.kt` (shows
  lyrics coarsened to the preferred timing; `loadLyrics` follows `.claude/rules/lyrics.md`),
  settings in `PlayerSettingsScreen` Lyrics section. DB `lyrics` table: `sync`, `source` (
  LyricsProvider by name), `credit` columns. Settings `LYRICS_SOURCE`/`LYRICS_SYNC` are enum prefs.
