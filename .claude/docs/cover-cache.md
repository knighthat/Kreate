# Covers: sizes, caching, bitmaps

## Sizes and disk cache

New image sources follow the same two sizes (helpers in `app/.../source/CoverCache.kt`).

- **OpenSubsonic and local:** two sizes in Coil's disk cache. 512 for rows and cards (picked when
  drawn under 512); 1440 for the player and detail screens, also when drawn exactly 512 or when the
  size is unknown, so Coil marks small decodes as sampled and never reuses them for the player.
  Keys: `<cover uri>#<size>` and `local_cover:<songId>:<albumArt>:<DATE_MODIFIED>#<size>`.
  Re-encode only when the picture fits the size (the server sent the original): JPEG q80 (PNG if it
  has alpha), kept only when smaller.
- **YouTube:** Google encodes server-side, no device re-encode: `=w…-h…-p-nu-l80-rj` (`-nu` never
  upscales; l80 ≈ −35 % vs l90). `HEADER_ARTWORK` is 1440 so the detail header and player share one
  file; above 320 px the song's own googleusercontent cover comes before the 720-high `hq720` frame.

Measurements behind it: Navidrome originals are mostly 1200 px and its resize is JPEG q75; YTM
album art originals ≈ 1400 px; local embedded art median 70 KB but up to 7 MB / 4300 px. A cold
local row cover took 62 ms (MediaMetadataRetriever ≈ 39 ms) vs 2–4 ms from disk. Flagships render
the player cover at ≈ 1400–1450 px. WebP was rejected: only 12–17 % smaller than JPEG at equal
quality, 2–3× slower decode, worse on already-q75 server files.

## Hardware bitmaps

Coil 3's `Image.toBitmap(width, height, config)` draws onto a software `Canvas` when it can't
return the bitmap as is; with a hardware bitmap that throws "Software rendering doesn't support
hardware bitmaps". The global loader allows hardware bitmaps, so memory-cached covers are usually
hardware on API 26+, local covers included.

Safe ways to read pixels: `.allowHardware(false)` on the request, or `image.toBitmap()` then
`bitmap.copy(ARGB_8888, false)` when `config == HARDWARE`. `ui/theme/ThumbnailSeed.kt` uses
memory-cache hits only when not hardware, otherwise a 48 px request with `allowHardware(false)` and
`memoryCachePolicy(READ_ONLY)` (so the tiny copy never replaces the screen's cover in memory).
