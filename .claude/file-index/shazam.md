# File index: `:shazam`

Paths are relative to `shazam/src/main/kotlin/me/knighthat/kreate3/shazam/`; checked against the
tree on 2026-11-14, verify a path still exists before relying on it. Map of the parts:
`.claude/file-index.md`.

Depends only on libraries (Ktor core, kotlinx serialization JSON, Kermit, coroutines, Koin); knows
nothing of the app, which records the sound. Protocol notes: `.claude/docs/shazam.md`.

- `Signature.kt` — `Signature.of(pcm)` (16 kHz mono 16-bit → `data:audio/vnd.shazam.sig;base64,…`,
  `sampleMillis`, `peaks`): Hann-windowed 2048-point FFT every 128 samples, peak spreading and
  picking, four bands 250–5500 Hz, stops past 12 s once 255 peaks are in; Shazam's header + CRC32.
  Adapted from Metrolist's `ShazamSignatureGenerator` (a SongRec port, both GPL-3.0), credited in
  the file. `resample(pcm, from)` to 16 kHz by linear interpolation.
- `Shazam.kt` — `Shazam(client, json).recognize(pcm, sampleRate, locale)` → `ShazamTrack?` (title,
  artist, album, cover hq, ISRC, `explicit` from `hub.explicit`, `shazamUrl` from `share.href`,
  `youTubeMusicSearch` from the `YOUTUBEMUSIC` hub provider): main-safe, no request for sound
  without peaks,
  requests at least 3 s apart (`REQUEST_GAP`), 404 or no `track` = `null`, other errors and network
  failures → `ShazamException`. `parse` is internal for tests.
- `ShazamModule.kt` — `shazamModule`: `single { Shazam(get(), get()) }` on the app's `HttpClient`
  and
  `Json`.
