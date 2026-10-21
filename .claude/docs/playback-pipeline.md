# Playback pipeline

How a queued song turns into bytes for ExoPlayer. Not all of it is verified on a device yet; the
open items are at the end.

## Resolving

- MediaItems carry no URI. `PlaybackService`'s session callback (`onAddMediaItems`) gives each item
  `kreate://<ORIGIN>/<mediaId>` (`withSongUri`, origin from `EXTRA_ORIGIN`).
- `playback/DataSourceResolver.kt` asks `Providers.playbackUri` (local, OpenSubsonic) or
  `YouTubeProvider.stream` at read time.
- YouTube streams are cached per song until `expiresAt − 10 min`, with their headers. A CDN 403
  calls `refused()`, so ExoPlayer's retry asks again without that client. Streams with `rangeSize`
  (chunked-only clients) are refused up front.
- OpenSubsonic streams read through `OpenSubsonicProvider.streamClient()` (trusts the pinned
  self-signed cert). A server song's address is reused for seeks for 1 h (`SERVER_ADDRESS_LIFE`).
- Failures map to `ERROR_CODE_SOURCE_OFF` / `ERROR_CODE_NOT_PLAYABLE` (`PlaybackErrors.kt`).
- Wake mode is NETWORK.
- A remote song not yet in the DB is stored when it starts playing (`PlayedSongRecorder`), then
  `SongFormatRecorder.flush` stores the format found while it loaded and `PersistentQueue.refresh`
  re-saves the queue (unstored songs are left out of the saved queue).

## Data source chain (`SongDataSource.kt`)

- Local (and non-`kreate://` URIs) → Resolving → `StreamDataSource`.
- Remote → `CacheDataSource(downloads, read-only)` → `CacheDataSource(cache)` →
  `ResolvingDataSource` → `StreamDataSource`. The resolver only runs for cache holes, so formats
  are recorded only on network reads.
- OpenSubsonic "Direct play" bypasses the caches entirely.

## Caches (`SongCaches.kt`)

- Two `SimpleCache`s sharing `exoplayer_internal.db` (excluded from backups):
  `cache` = `cacheDir/songs`, LRU sized by `Preferences.SONG_CACHE`;
  `downloads` = `noBackupFilesDir/downloads`, no eviction, read-only in playback. Nothing writes
  `downloads` yet; its cap `SONG_DOWNLOADS` is stored but unused.
- YouTube guard: with cached parts of length L, streams of another `contentLength` are skipped; if
  none match, the cached parts are dropped. OpenSubsonic has no such guard (length unknown).

## Quality and cache copies

- `Context.streamQuality(origin, youTube)` (`StreamQuality.kt`): Wi-Fi vs metered mobile settings
  per source; YouTube has a separate `_PREMIUM` pair used while signed in. LOCAL = ORIGINAL.
- innertubex mapping: LOW → 139, MEDIUM → 140 (falls back to HIGH), HIGH → 251, ORIGINAL → HIGH in
  the session + premium hint (141 if Premium).
- OpenSubsonic: ORIGINAL → `format=raw`; otherwise the transcode decision API when the server lists
  the `transcoding` extension (Navidrome 0.64+), else `stream?maxBitRate=64/128/320` +
  `estimateContentLength=true`.
- One cache copy per quality: `QueuedSong.cacheKey` = `kreate://ORIGIN/id#QUALITY`.
  `CacheCopies.pick` at song start: best whole copy ≥ setting, else (online) best partial ≥ setting,
  else (online) a new copy at the setting, else (offline) best whole, else best partial. Seeks reuse
  the pinned copy. Lower copies are dropped only once the read copy is whole (`readEnded`).
- A future downloader must store under the copy key, and the router should then consider download
  copies.

## Formats and gain (`SongFormatRecorder.kt`)

- Runs from the resolver's `beforeRead`, so the gain is stored before playback starts.
- YouTube: `tag` = itag, gain = `loudnessDb`.
- OpenSubsonic: `raw` row (from `getSong`, holds the gain: −4 − trackGain, album gain as fallback)
  and `{codec}-{kbps}` rows (e.g. `opus-96`) per transcoded output, without gain. Non-transcoding
  servers: tag from the response Content-Type (`recordAnswered`).
- Songs not in the DB can't hold rows (FK): their formats wait in memory (`pending`, 50 songs)
  until `flush` stores them. `LoudnessNormalizer` reads `pendingFormatsOf` at the song change and
  follows `pendingFormats` + the DB, keeping a found gain while the formats move to the DB.

## Navidrome observations

Once a transcode is in its cache the estimated length is exact and ranges give 206. Cold, a range
gets 200 from the start (Media3 skips ahead) and the estimate was ~4 % too long (song ends early).

## Open

- OpenSubsonic songs without ReplayGain have no gain, and a row stored without gain is never
  re-asked.
- Chunked (bounded-range) reading, if refusing `rangeSize` clients proves too strict.
- Proposed: keep innertubex's `AGE_RESTRICTED` reason as a `YouTubeException` flag. Auto-skip after
  a failure not decided.
- A new copy's stream failing while online errors out instead of falling back to a lower cached
  copy (offline is decided only by Android's VALIDATED flag).
- Not verified on a device: quality selection, cache copies, whether a cold OpenSubsonic estimate
  gets corrected so the copy counts as whole.
- The transcode decision path has no unit test.
- Offered, not taken up: an emulator check against Navidrome (play at Low, check `song_format`
  rows, seek in a cold transcode); lowering High's Opus cap to 256.
