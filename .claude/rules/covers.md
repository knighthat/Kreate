---
paths:
  - "app/src/main/kotlin/**/ui/**"
  - "app/src/main/kotlin/**/source/**"
  - "app/src/main/kotlin/**/playback/**"
---

# Covers

Sizes, cache keys and bitmaps: `.claude/docs/cover-cache.md`.

- Cover fallback lives only in `Thumbnail` (`ItemParts.kt`: `image_not_available` while there's no
  cover, while loading and on error). Never set `error` or `fallback` on the app-wide Coil
  `ImageLoader`: `rememberThumbnailSeed` would seed the theme from the placeholder.
- Coil images are usually hardware bitmaps; `Image.toBitmap(w, h, config)` throws on them. To read
  pixels, request with `allowHardware(false)` or copy to `ARGB_8888`.
- Song `MediaItem`s always carry `artworkUri`, even in lists whose rows hide covers
  (`showThumbnail = false`): the same items go into the queue, the notification and the saved
  queue, which show only that address.
- Queued songs carry their cover as an `ArtworkProvider` address (`withServedArtwork`), because
  Android Auto's player reads only `content://` covers. App code reading a song's `artworkUri`
  goes through `thumbnailModel`/`songThumbnailModels`, or `coverSource()`, to load the original.
- A user's own song cover (`user_song.thumbnail`) can be a file kept on the device
  (`CoverFiles`, both under `noBackupFilesDir`): `download_covers/<hash>-512.jpg` and `-1440.jpg`
  (rows point at the 1440 one, `CoverFiles.Mapper` draws the 512 one where `coverSize` says so) or
  `profiles/<profile id>/custom_thumbnail/` for covers the user picked (goes with the profile).
  `songThumbnailModels` tries a file cover first.
  A download never replaces a custom cover whose file exists. Taking a file cover away
  (`forgetThumbnail`) deletes rows that keep nothing else, else sets it `NULL`; covers whose file is
  gone are taken away at app start (`forgetMissing`), all by the maintainer's choice (2026-10-26).
