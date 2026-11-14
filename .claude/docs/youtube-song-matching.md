# Matching songs of other services on YouTube Music

Probed on 2026-11-14 from the dev machine with two Exportify files of Spotify playlists (258 and
1,722 tracks, drum and bass) and the app on the emulator. A small sample: a guide, not a measure.
Code: `YouTubeProvider.songMatching`, `PlaylistImporter.matchTracks`, `BatchSearches`. Probing:
`.claude/rules/youtube.md`.

## ISRC search vs title and artists

Same 20 tracks (10 per file, half with Spotify popularity under 20), one `WEB_REMIX` search each
with the songs filter:

| Query                           | Right song first | In the top 5 | Wrong results                         |
|---------------------------------|------------------|--------------|---------------------------------------|
| The ISRC alone                  | 8                | 8            | unrelated songs, any genre or country |
| Title and artists, space-joined | 18               | 19           | other versions by the same artists    |

- YouTube Music has no ISRC lookup and never returns ISRCs (search, `next`, `player`), but
  searching an ISRC as text finds the exact song when it is indexed. When it isn't, the search
  still answers with unrelated songs, so a hit must be checked like any other result.
- Hits didn't follow popularity, release date or label. The closest pattern was the ISRC's
  registrant (its first five characters, who registered the recording): `NLCK4` (Liquicity,
  Galacy) 5 of 6, but `GB2LD` and `GBUM7` one hit and one miss each.
- Trying the ISRC first costs about 1.6 requests a track and found nothing the title search
  missed: the app searches title and artists only. The ISRC still tells repeats of a recording
  apart (one file had the same recording as a single and on a compilation, different Spotify ids).
- The track missing from both ("Fade Away (Deaf Kev Remix)", Jacob Tillberg) seems not to be on
  YouTube Music.

## What a match has to check

- **Length**, within 2 s: other services give milliseconds, YouTube whole seconds, uploads of the
  same recording differ by a second. It rules out most wrong versions on its own.
- **Version words** (instrumental, remix, live, VIP, edit, extended, acoustic, …) in both titles
  or neither. Without it "Horizon (ft. Notelle)" by Telomic (269.6 s) went in as "Horizon
  (Instrumental)" (4:29) instead of "Horizon (feat. Notelle)" (4:30): both share "Horizon", both
  are the artist's, the instrumental was nearer in length. "Mix", "original" and "remaster" aren't
  version words: other services write "Original Mix" or "Remastered 2011" where YouTube often
  writes nothing.
- **Title written another way**: Spotify puts a version after " - " ("Surface - Blackmill Remix"),
  YouTube in brackets ("Surface (Blackmill Remix)"); folding to words makes them equal. "ft",
  "feat" and "featuring" are one word.
- **Artists**: Spotify lists them one by one ("Camo & Krooked;Document One"), YouTube's result
  credits often join or drop some ("Camo & Krooked" only); a name holding another's words is the
  same artist. Names in another script or spelling miss, so a result by other artists still
  matches with the same title.
- **Album** isn't checked: the right recording was sometimes on a compilation ("Freefall VIP" by
  Metrik on "Drum & Bass Summer Slammers 2013 (YOUTUBE FINGERPRINTS)", same length).

Emulator, 10 tracks of the sample after the version rule: 9 matched right, the 10th is the one
missing above. A rerun matched 5 of them from the songs the first run stored, without a search.

## How fast to search

- YouTube publishes no limit. The figure most quoted is yt-dlp's (not re-checked): signed out,
  about 300 videos or 1,000 web and API requests an hour per IP before "content isn't available"
  answers; signed in about 2,000 videos an hour. It comes from downloads and `player` requests,
  watched more closely than search.
- Signs of being throttled: HTTP 429, a redirect to a captcha (`/sorry/`), or answers that are
  empty or short without an error, which an importer must not take as "no match". The "Sign in to
  confirm you're not a bot" refusal comes from `player`, but it is per IP: heavy searching can
  stop playback on the same network for a while.
- Bursts of 20 searches at 3 a second (41 in all that day) were all answered. The app's default
  is 2 a second, one at a time (`Preferences.YOUTUBE_SEARCHES_PER_SECOND`, 1 to 5); the real
  limit wasn't probed, finding it would flag the IP.
