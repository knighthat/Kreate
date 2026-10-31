# MusicBrainz

How the MusicBrainz web service (`musicbrainz.org/ws/2`) behaved when probed on 2026-10-30, for
`app/.../musicbrainz/MusicBrainz.kt` and the song Details page. Re-probe before changing how
recordings are searched or picked.

- **Rate limit:** at most one request a second per client, with a `User-Agent` naming the app and a
  contact. Too fast, and it answers 503; after a burst of probes from this machine (spaced ~1.1 s)
  it kept answering 503 for a while. `MusicBrainz` holds every request behind one `Mutex` with 1 s
  between them; the shared `HttpClient` retries 503 with exponential delay.
- **Recording search** (`/recording?query=…&fmt=json&limit=…`, `limit` up to 100): each result has
  `id`, `score` (0–100), `title`, `length` (ms, may be missing), `first-release-date` (`YYYY`,
  `YYYY-MM` or `YYYY-MM-DD`, may be missing), `disambiguation` (`live, 1986-07-12: Wembley…`,
  `karaoke`, `2002 5.1 mix`), `artist-credit[]` (`name` + `joinphrase`) and `releases[]` (with
  `release-group.primary-type`/`secondary-types`). Lucene syntax: `recording:"…" AND artist:"…"`.
- **Famous songs rank badly.** `recording:"Bohemian Rhapsody" AND artist:"Queen"` gave 229 results,
  dozens all scored 100: live versions, karaoke, cast recordings, compilation copies. The 1975
  studio recording (`b1a9c0e9-d987-4042-ae91-78d6a3267d69`) wasn't in the top 100. Adding
  `AND status:official AND primarytype:album` cut it to 71, still led by a 5.1 mix and live
  versions. So `identify` can pick another version of a well-known song, and the search button is
  the way to the right one.
- **Not tried yet** (503 stopped the probes): `qdur:` (length in 2 s steps, `ms / 2000`) or a
  `dur:[a TO b]` range, excluding `disambiguation:live`/`karaoke` in the query, `video:false`, and
  searching by ISRC (`/isrc/<code>`) when a source gives one.
- **Recording lookup**
  (`/recording/<id>?inc=artist-credits+releases+release-groups+isrcs+genres+tags`):
  adds `isrcs[]`, `genres[]`/`tags[]` with vote `count`, and `releases[]` with `date`, `country`
  (`XW` = worldwide), `status` and `release-group.primary-type`. Many recordings have no genres or
  tags of their own (the release often has them). An unknown id answers 404. A merged id should
  answer with the recording it was merged into (a different `id`), per MusicBrainz, not probed; each
  fetch saves the id it got back for that case.
