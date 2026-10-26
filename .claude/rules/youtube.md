---
paths:
  - "**/youtube/**"
  - "gradle/libs.versions.toml"
---

# YouTube

Details: `.claude/docs/youtube-streams.md`, `.claude/docs/playback-pipeline.md`.

- Screens are built on `YouTubeProvider` + Paging (`source/youtube/`), not on repositories.
- Library calls fail with `YouTubeException.signedOut` when the answer reports `logged_in=0`, so an
  ended session never empties the library.
- Streams are requested **signed out** (no cookies to innertubex), except `StreamQuality.ORIGINAL`,
  which asks in the session with innertubex's premium hint (itag 141). Don't pass cookies or add
  age-restricted hints anywhere else.
- innertubex is pinned from JitPack in `libs.versions.toml`, bumped by hand. Before bumping, check
  the tag built: `https://jitpack.io/api/builds/com.github.MetrolistGroup/innertubex/<tag>`. If
  playback breaks, an outdated innertubex is the first suspect; ask once whether to bump it then.
- The BotGuard `API_KEY`/`REQUEST_KEY` in `PoTokenWebView` are YouTube's public web values (via
  NewPipe/Metrolist); if Google rotates them, take the new ones from there.
- Don't probe InnerTube heavily from a dev machine (bot flag). Check yt-dlp's `INNERTUBE_CLIENTS`
  and innertubex's client catalog before changing clients.
