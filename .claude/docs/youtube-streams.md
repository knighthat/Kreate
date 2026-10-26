# YouTube streams

## Current setup

- `:provider` depends on innertubex (pinning and bumping: `.claude/rules/youtube.md`).
- `YouTubeStreams.kt` wraps `InnerTubeExtractor` (`PlayerConfigRepository.disabled()`, HLS/SABR
  off) behind `InnerTube.stream`. A signed-out instance serves every quality except ORIGINAL; a
  second instance with the session cookie + `ContentHints.withPremium()` serves ORIGINAL.
- Live, the fastest automatic client is usually VISIONOS 0.1 (~100 ms warm). It needs visitorData
  and no bounded ranges, chunks or media headers.
- PO tokens: `WebPoTokenProvider` (innertubex `TokenProvider`, WEB_BOTGUARD, 8 s cap, one WebView
  per visitor id) + `PoTokenWebView` (hidden WebView, BotGuard Create/GenerateIT over the app's Ktor
  client) + `BotGuard.kt` + `provider/src/main/assets/po_token.html`, ported from Metrolist/NewPipe.
  With VISIONOS refused, `WEB_REMIX__po` URLs seek and download whole (≈5 s cold, 0.4–1 s warm).
- `stream(songId, refused)` + `YouTubeStream.client`: on a CDN 403 the player asks again with that
  client refused.
- R8: `provider/consumer-rules.pro` keeps the `@JavascriptInterface` methods. Re-check the release
  dex when the stream chain changes.

## Signed-out vs session

- Streams stay signed out except ORIGINAL (see `.claude/rules/youtube.md`). Age-restricted songs
  therefore return `null`, except that with ORIGINAL picked and someone signed in they may play as a
  side effect.
- Premium isn't detected: a non-Premium account on ORIGINAL gets 251 (High), possibly after a 403 →
  refused retry.
- Even a non-null signed-out stream can be refused (a `WEB_EMBEDDED_PLAYER__nopo` URL that 403s on
  every range was seen), which is why the 403 → refused retry exists.

## Client behavior (probed without sign-in or PO token)

- ANDROID_VR, IOS and ANDROID return direct `url`s (no `n`, no signatureCipher).
- Without a PO token, GVS URLs serve only the first ~1 MB: any range starting past it, any request
  asking for 1 MB+, and a request with no range return 403.
- Regular music videos streamed whole on some of those clients; YTM Topic tracks stopped at 1 MB on
  all of them, so the main YTM catalog needs a PO token or another route.
- Without visitorData, ANDROID_VR answered "Sign in to confirm you're not a bot" for Topic tracks;
  visitorData from a WEB_REMIX answer fixed it.
- TVHTML5 and WEB/WEB_REMIX need signatureTimestamp from the player JS plus decipher;
  ANDROID_MUSIC/IOS_MUSIC need sign-in.
- VISIONOS (id 101) has no PO token policy and needs no JS player; "made for kids" and age-limited
  videos don't play with it. Version 1.02 "can stall" on clean sessions and once 403'd on fresh
  mid-file seeks while 0.1 worked; 0.1 once answered HTTP 400. Both are intermittent, hence the
  fallback.

## References

- yt-dlp `yt_dlp/extractor/youtube/_base.py` `INNERTUBE_CLIENTS` (Unlicense).
- innertubex (GPL-3.0): client catalog in `extraction/strategy/PlaybackClientCatalog.kt`, fallback
  in `ContentAwareFallbackStrategy.kt`. Metrolist (GPL-3.0) uses it and re-resolves with another
  client when the CDN refuses a URL (`markStreamClientFailed`).
- Local copies may exist under the git-ignored `.ignore.d/` and can be behind: ask once whether to
  `git pull --rebase` them yourself before reading.
