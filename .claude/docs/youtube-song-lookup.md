# Looking up songs by id: `music/get_queue`

Probed on 2026-11-05 from the dev machine (a handful of requests; don't probe more, see
`.claude/rules/youtube.md`). Code: `InnerTube.queueOf`, `YouTubeParser.queueOfSongs`,
`YouTubeProvider.songDetailsOf`; used by the old-app downloads picker.

- Body: the usual context plus `videoIds: [...]`. The answer has `queueDatas[].content` holding a
  `playlistPanelVideoRenderer` (or its wrapper), the same renderer the `next` queue uses, so
  `queueSong` parses it: title, artists, length, thumbnails, explicit badge.
- A hundred ids in one request worked (duplicates are echoed). 114 in one request also worked
  (2026-11-13, signed out, `gl` US: one answer, ~1 MB, 0.34 s, every id in order, 14 of them dead
  entries). 471 in one request worked too (same day: ~4.4 MB, 0.81 s, 34 dead entries). Larger
  sizes weren't tried.
- A video YouTube won't show signed out (likely private: `next` gives no title for it, oEmbed
  answers 403) fails the whole request with **403** "The caller does not have permission"
  (`PERMISSION_DENIED`), alone or among others (2026-11-13, `IWyZNl8h2DI` in a 23-id batch,
  signed out, `gl` US; found by splitting, 11 requests).
- An entry can carry another id than the one asked, in the asked one's place: `XauFHDC8__E`
  ("Save Me", Omri) came back as a dead ATV entry `vr-7KMTnuFU`. Match answers to asked ids by
  position, not by `videoId`.
- One id YouTube doesn't know fails the whole request: HTTP 404 "Requested entity was not found"
  without a visitor id, **500 "Internal error encountered"** once the request carries
  `visitorData` / `X-Goog-Visitor-Id` (which the app sends after its first answer). Treat both as
  "unknown"; splitting a failed request in halves finds the unknown ids in about 2·log₂ (n)
  requests each, one after the other.
- Order of the answer follows the order asked. Client version in the app: `1.20261011.16.00`; the
  older `1.20251015.03.00` answered the same.
