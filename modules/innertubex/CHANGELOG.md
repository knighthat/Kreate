# Changelog

All notable changes are documented here. This project follows
[Semantic Versioning](https://semver.org/spec/v2.0.0.html) while recognizing
that `0.x` releases may contain breaking API changes.

## [Unreleased]

## [0.9.2] - 2026-10-14

### Added

- `SabrBootstrap.videoVariants` lists one video rendition per height, and `withVideoVariant`
  retargets a session at one of them without touching audio or session state.
- `SabrResponseDiagnostics.networkReadMs` reports time spent waiting on response bytes, for
  throughput estimates. `PlayerResponse` formats now parse `averageBitrate`.

## [0.9.1] - 2026-10-14

### Fixed

- Republishes 0.9.0; its JitPack build failed to resolve the foojay settings plugin on the build
  worker. JitPack now retries a failed build up to three times. No library code changes.

## [0.9.0] - 2026-10-14

### Changed

- Automatic client selection follows measured startup times from a device benchmark of every client:
  - Explicit audio uses the config-free fast path and visionOS 0.1 (0.5s instead of 11-33s).
    Age-gated and account-restricted tracks fail there in under a second, then fall back.
  - Android Kids is selected for kids content ahead of Web Kids (0.6s instead of 4.7s).
  - Signed-in video keeps visionOS SABR first unless the request is Premium (4.5s instead of 19s).
  - visionOS SABR is allowed for explicit video.

## [0.8.6] - 2026-10-13

### Fixed

- Republishes 0.8.5; its JitPack build failed on a timing-sensitive QuickJS idle-release test. No
  library code changes.

## [0.8.5] - 2026-10-13

### Changed

- Video requests try SABR clients first. Direct video needs a poToken and a cipher pass, which took
  over 10s on a cold start on low-end Android devices; VisionOS SABR returns video without either.

### Fixed

- Ciphered video formats whose `n` parameter was solved are no longer rejected. The check treated
  any `n` as unresolved, so every cipher client failed video and extraction fell through.

## [0.8.4] - 2026-10-08

### Fixed

- Audio selection prefers the original track on multi-language uploads. A dubbed track could
  outrank the original by bitrate, and hosts that reject non-original audio failed playback.
- Web Embedded player requests send the embed page's visitor with its encrypted host flags. Hosts
  that persist a visitor sent their own, and YouTube answered every request as unavailable.

## [0.8.3] - 2026-10-07

### Fixed

- JitPack builds use Temurin 21 instead of SDKMAN OpenJDK 21.0.2, which cannot open jar files on
  JitPack's fresh-clone workers. 0.8.0 to 0.8.2 were never published to JitPack; this release ships
  their changes.

## [0.8.2] - 2026-10-07

### Fixed

- Republishes 0.8.0 again; the 0.8.0 and 0.8.1 JitPack builds failed on JitPack workers that could not
  read jar files. No code changes.

## [0.8.1] - 2026-10-07

### Fixed

- Republishes 0.8.0, whose JitPack build failed on a transient JitPack error. No code changes.

## [0.8.0] - 2026-10-07

### Added

- `ExtractionCipherService` is public and `InnerTubeExtractor` accepts any implementation, so hosts
  can run cipher solving out of process. `YouTubeCipherService` implements it. Kotlin callers are
  source compatible; the constructor's JVM signature changed.

### Changed

- EJS keeps the compiled n/sig functions of up to two preprocessed players, so repeated challenges
  for the same player take about 1 ms instead of recompiling a ~4 MB script (~400 ms) each time.
  A full-player preprocessing pass releases them first, since it needs nearly the whole QuickJS heap.
- EJS closes its QuickJS runtime and thread after 20 seconds without solves and bootstraps again
  on demand, releasing ~18 MB of native memory between bursts. Up to 256 solved n/sig results are
  cached per player, so repeated challenges do not restart the runtime.
- With persistent preprocessed-player storage, preprocessed players are no longer kept on the heap;
  without it, at most one is kept (was four). Only the current raw player script is cached, and
  parser solvers no longer retain it.
- `YouTubeCipherService.initialize()` no longer creates a QuickJS runtime that nothing uses.
- Watch and embed pages are streamed and read only until the player URL, visitor data, client
  version and signature timestamp are known; the timestamp may come from the remote player
  config. With a remote config this cuts a cold config fetch from ~0.9 s to ~0.2 s.

### Fixed

- Each QuickJS runtime is created, used, and closed on its own dedicated thread, so its native
  stack limit is always measured from the thread that runs the JavaScript.
- Bounded text fetches (pages, player scripts, remote configs, captions) now stream the body, so
  their byte limits apply before the response is held in memory.
- `gradlew.bat` is stored with LF endings again, as `.gitattributes` requires.

## [0.7.4] - 2026-09-30

### Added

- `YtConfigParserImpl` accepts an optional `cipherService` so its signature-timestamp
  fallback reuses the cipher service's player-script download.

### Changed

- One extraction no longer resends an identical player request across its config-free,
  cached-config, fresh-config and cookie passes.
- The extraction director falls back to the next client instead of retrying transient
  player failures, and HTTP 429 is only retried with a `Retry-After` of at most 5 seconds.
- The default `InnerTubeExtractor` strategy now scores clients with the supplied
  `ClientHealthMonitor`.
- Locale variants of the same `player_ias` build share one download and cache entry,
  and the full player script is only downloaded when no preprocessed EJS player is cached.
- Missing GitHub preprocessed-player configs are cached for 15 minutes.
- Cached watch-page configs depend only on locale and cookie mode and live for 3 hours.
- SABR media payloads are copied once instead of twice.

### Fixed

- Visitor data fetched for a tokenized client is now published to the session and
  carried into the remaining clients of the same batch.
- Join line-wrapped visitor data before using it in requests.

## [0.7.3] - 2026-09-29

### Fixed

- Allow visitor-backed legacy visionOS direct audio for eligible ordinary playback
  without promoting the probe-only client globally.
- Bound SABR protection-pending responses when the server omits a retry allowance,
  and require explicit acceptance before clearing pending protection.

## [0.7.2] - 2026-09-28

### Fixed

- Exclude the rejected legacy visionOS 0.1 client from automatic playback
  selection while retaining explicit compatibility probes.
- Use visionOS SABR for eligible normal and explicit audio without the legacy
  client's failing player requests.

## [0.7.1] - 2026-09-26

### Added

- Add a standalone playback verification harness for paced client benchmarks.

### Fixed

- Prefer sustained visionOS audio for automatic explicit and SABR selection.
- Retain cumulative SABR buffered ranges across empty responses and span
  checked timestamps instead of the latest response only.
- Avoid redundant EJS source copies in the cipher solver.

## [0.7.0] - 2026-09-18

### Added

- Add optional persistent caching for preprocessed EJS player scripts.
- Add explicit authenticated Premium TV discovery with scoped, short-lived
  bearer credentials and strict request isolation.
- Add four explicit probe-only client identities without promoting them into
  automatic fallback.

### Changed

- Preserve VISIONOS-first ordinary playback while reducing unnecessary player
  configuration and token work for eligible native audio.
- Process selected audio and video cipher challenges together and reduce
  QuickJS and SABR allocation overhead.
- Improve kids-client ordering and native video format selection while keeping
  validated progressive video as a bounded fallback.
- Start SABR seeks from the segment containing the requested time while
  preserving initialization and sequence ordering.

### Fixed

- Refresh failed cached player configurations without losing concurrent token
  minting or the extraction request budget.
- Validate requested and returned video identity before accepting playback
  responses.
- Use anonymous-first watch configuration for ordinary signed-in playback.
- Recognize quoted player timestamps and reject incomplete SABR sequence gaps.

## [0.6.0] - 2026-09-07

### Added

- Add iOS device and simulator targets with native locale, hashing, and EJS
  implementations.
- Allow playlist deletion and unlike requests to use an explicit session
  snapshot so account changes cannot redirect a mutation.
- Add strict MP4 audio selection for native players.

### Fixed

- Refresh QuickJS native stack metadata before each evaluation so authenticated
  playback cannot crash after a coroutine changes workers.
- Build `Accept-Language` consistently without duplicating or inventing a
  region.
- Handle native locale discovery and SABR end-of-stream behavior safely.

## [0.5.2] - 2026-09-02

### Fixed

- Restore the upload content type expected by YouTube Music so song uploads can
  finalize successfully.
- Retry authenticated player-config page failures without cookies.
- Allow additional time for current large player scripts to evaluate.

## [0.4.1] - 2026-08-31

### Fixed

- Transform `n` challenges on uploaded-media URLs hosted by YouTube so their
  streams do not fail with HTTP 403.

## [0.4.0] - 2026-08-31

### Fixed

- Complete SABR streams at their declared final segment when YouTube omits the
  end-of-track marker.
- Preserve WEB_REMIX as the preferred automatic SABR client.
- Accept validated uploaded-media playback URLs and attach login cookies only
  to uploaded-media requests.

## [0.3.0] - 2026-08-30

### Fixed

- Prewarm authenticated and anonymous player configurations separately,
  preprocess the active player script, and mint the initial PO token concurrently
  to reduce cold protected-playback startup time.

## [0.2.6] - 2026-08-27

### Fixed

- Prefetch PO tokens for protected streams, avoid repeated unavailable-token
  work across client fallbacks, and solve signature and n-parameter challenges
  together.

## [0.2.5] - 2026-08-27

### Fixed

- Accept current root-path YouTube resumable upload session URLs while
  continuing to reject unexpected endpoints.

## [0.2.4] - 2026-08-27

### Fixed

- Retain streams whose n-parameters were successfully transformed by the
  cipher service.

## [0.2.3] - 2026-08-27

### Fixed

- Allow EJS to process current large player scripts without exhausting its bounded QuickJS memory budget.

## [0.2.2] - 2026-08-26

### Fixed

- Automatic extraction now exhausts eligible direct clients before trying every eligible SABR fallback.

## [0.2.1] - 2026-08-25

### Added

- Extracted media metadata and perceptual loudness in stream results.
- Per-request HLS, SABR, and bounded-range capability flags for direct-only hosts.

## [0.2.0] - 2026-08-25

### Added

- Reusable player extraction stack with content-aware client selection,
  format selection, direct/HLS/SABR transport support, PO-token contracts,
  client-health integration, bounded watch/player parsing, and diagnostics.
- Platform-neutral BotGuard/page-attestation parsing and transcript/caption
  helpers for Android and desktop hosts.
- Public bounded response decoders and hardened cookie normalization helpers.

### Fixed

- Bound player, watch-page, iframe, caption, transcript, token, and sidecar
  responses before materializing them in memory.
- Validate media, caption, player-script, SABR, upload, and playback-statistics
  URLs by HTTPS host, default port, and expected endpoint path.
- Reject unresolved cipher parameters, incomplete video selections, invalid
  video media hosts, and bounded-range streams without known lengths.
- Preserve coroutine cancellation across extraction and playback-recovery
  fallbacks, and bind player-config caching to the originating session.
- Redact signed URLs, visitor data, PO tokens, account identifiers, media IDs,
  and raw exception messages from reusable diagnostics.
- Restore `application/octet-stream` for finalized song-upload bodies.

## [0.1.2] - 2026-08-22

### Added

- Playlist custom thumbnail upload and removal.
- Adding one playlist to another playlist.
- Deleting privately owned library entities.
- Optional progress reporting for in-memory song uploads.

### Fixed

- Documented JitPack's variant-aware KMP coordinate instead of its aggregate
  repository POM coordinate.
- Accept-Language headers no longer duplicate a region already present in the
  language tag.
- `accountsList` omits the active-account sync id so every signed-in account
  can be enumerated.

## [0.1.1] - 2026-08-21

### Fixed

- Preserved the generated binary API baseline format so clean CI and JitPack
  builds pass. The failed `v0.1.0` JitPack lookup did not publish artifacts.

## [0.1.0] - 2026-08-21

### Added

- Android and JVM Kotlin Multiplatform InnerTubeX client.
- Session-safe authenticated requests and account operations.
- Player cipher handling with Faraday/Zemer, EJS, and parser fallbacks.
- Experimental SABR audio/video transport with bounded UMP parsing.
- Streaming YouTube Music uploads without full-file buffering.
- JitPack publication, API compatibility validation, and CI gates.

### Security

- Restricted authenticated playback-statistics requests to approved YouTube
  HTTPS endpoints.
- Bounded remote player, config, visitor-data, and SABR response bodies.
- Redacted raw URLs and exception text from library diagnostics.
- Restricted authenticated upload, media probing, and executable player-config
  requests to their expected HTTPS providers without following redirects.
- Removed account cookies from anonymous browse, next, and queue requests.
- Bound account mutations and both upload stages to their originating session.
- Bounded retained SABR contexts, playback cookies, bootstrap blobs, and total
  response bytes, with exact full-stream byte validation.
- Added a QuickJS evaluation timeout for malformed or hostile player code.
