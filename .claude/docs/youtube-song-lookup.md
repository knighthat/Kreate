# Looking up songs by id: `music/get_queue`

Probed on 2026-11-05 from the dev machine (a handful of requests; don't probe more, see
`.claude/rules/youtube.md`). Code: `InnerTube.queueOf`, `YouTubeParser.queueOfSongs`,
`YouTubeProvider.songDetailsOf`; used by the old-app downloads picker.

- Body: the usual context plus `videoIds: [...]`. The answer has `queueDatas[].content` holding a
  `playlistPanelVideoRenderer` (or its wrapper), the same renderer the `next` queue uses, so
  `queueSong` parses it: title, artists, length, thumbnails, explicit badge.
- A hundred ids in one request worked (duplicates are echoed). Larger sizes weren't tried.
- One id YouTube doesn't know fails the whole request: HTTP 404 "Requested entity was not found"
  without a visitor id, **500 "Internal error encountered"** once the request carries
  `visitorData` / `X-Goog-Visitor-Id` (which the app sends after its first answer). Treat both as
  "unknown"; splitting a failed request in halves finds the unknown ids in about 2·log₂ (n)
  requests each, one after the other.
- Order of the answer follows the order asked. Client version in the app: `1.20261011.16.00`; the
  older `1.20251015.03.00` answered the same.

## Dead and replaced ids

Probed 2026-11-11, signed out, `gl` US (VN answered the same). YouTube re-publishes songs under a
new id (re-encode, new album id); the old id points nowhere.

- `get_queue` marks a dead id without failing the request: the entry keeps the `videoId` but has
  `unplayableText` "[Video blocked in country]", no title or length and the `meh_mini` placeholder
  cover. 12 of the 100 oldest ids of the old-app export looked like this, none broke the batch.
  The text alone can't tell taken down from replaced (or a real region block).
- `next` of a dead id still gives its full old title, artists and album: no signal. The bare
  WEB_REMIX `player` says "UNPLAYABLE" for live ids too (no PO token): no signal either.
- The old album page loads with no tracks. The new album (signed out) listed the music video (OMV)
  of the song, not the song; `next` of that OMV had no counterpart. Nothing links old to new.
- Found only by search + metadata: `azxk0-7Zjhk` → `bM0x19DYsmA` (same title, channel, 134 s,
  ATV, new album `MPREb_AvgaEVYNlCm`); BLACKFOX `Xquk9xxVcpE` → `hekAwcwLXc4` (same length, on a
  best-of); Megalovania `osUVQPnXnEo` → `BXUfJpPCxZs` with the title now
  "Megalovania (feat. ThunderScott)" and the artist name cased differently, so titles match by
  "holds" and artists by id. Code: `YouTubeProvider.isPlayable` / `replacementOf`, used by
  `PlaybackRecovery` (`ReplacedSongs.kt`).
- A word starting with `-` in a search is taken as "without it": "LEVEL5 -judgelight- Sayaka
  Sasaki" and "sister's noise -version 2022- fripSide" found nothing related, so `replacementOf`
  searches with hyphens turned into spaces. Titles are compared folded (NFKC, lower case, words
  only, the maintainer's call 2026-11-11), so a renamed one ("-version 2022-" → "(Version 2022)",
  `v-dRMNMLhyU` → `zezsOaW8jK8`) matches. All 12 dead ids of the sample had a replacement, none
  was gone for good.
- Emulator run 2026-11-11 (signed in and out): innertubex answers `UNAVAILABLE` in about 3 s, the
  check takes about 1 s, `azxk0-7Zjhk` and `uiGlDitA-i4` played their replacements, `v-dRMNMLhyU`
  ended as gone with "nothing to play".
