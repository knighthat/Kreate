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
