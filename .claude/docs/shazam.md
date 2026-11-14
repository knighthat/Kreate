# Shazam

Shazam has no public API. `:shazam` asks the undocumented server Shazam's own Android app uses, the
way SongRec and Metrolist do. It's against Shazam's terms and can change or close without notice;
an F-Droid build with it would likely get the NonFreeNet anti-feature.

## Request

`POST https://amp.shazam.com/discovery/v5/{lang}/{COUNTRY}/android/-/tag/{UUID upper}/{UUID}`
with `sync=true&webv3=true&sampling=true&connected=&shazamapiversion=v3&sharehub=true&video=v3`,
headers `User-Agent` (the device's `http.agent`) and `Content-Language: {lang}_{COUNTRY}`, JSON body
`{"signature": {"uri", "samplems", "timestamp"}, "timestamp", "timezone"}`. No geolocation is sent
(Metrolist sends a random one).

## Answers (probed 2026-11-14, 7 requests)

- A signature of a sample that isn't a known song: `200` with no `track` (not `404`). The server
  took the signature without geolocation.
- Four 12 s segments of one known song (48 kHz stereo MP3, decoded to 44.1 kHz mono, resampled
  by the module) all matched, with `track.title`, `subtitle` (artist), `isrc`, `url`, the `SONG`
  section's `metadata` entry titled `Album`, and `images.coverarthq` (400×400). Parts of the song
  shared by two releases can match either: two segments named the Korean version, two the
  Japanese single, each with its own ISRC and album.
- A match's `matches` lists every release it fits (id, `offset` into the song in seconds, skews);
  `track` is the first. Other fields: `genres.primary`, `Label`/`Released` metadata, `albumadamid`
  and `hub.actions` (Apple Music id, a 30 s AAC preview), `hub.providers` (Spotify, YouTube Music,
  Deezer, as search links only, no video id), `share.href`. Covers are mzstatic links ending in
  `400x400cc.jpg`; `coverarthq` was the same 400 size as `coverart`.
- Signing 12 s took about 50 ms on the desktop JVM (117 ms cold).
- Metrolist treats `404` as no match and `429` as too many requests.
