---
paths:
  - "provider/**"
  - "app/src/main/kotlin/**/source/**"
  - "app/src/main/kotlin/**/playback/**"
  - "app/src/main/kotlin/**/ui/screen/settings/network/**"
  - "app/src/main/kotlin/**/ui/screen/detail/**"
---

# External sources (YouTube, OpenSubsonic)

## Gates

- Nothing contacts the OpenSubsonic server while `Preferences.OPEN_SUBSONIC_ENABLED` is off, saved
  credentials or not: check it before every request, stream and cover URL included, and treat off
  as "no source". `OPEN_SUBSONIC_AUTH` picks password or API key; the other is kept, unused.
- A Coil 3 `Fetcher` that returns `null` hands the data to the next fetcher (the Ktor network one),
  which would request the URL unsigned while the server is off. Fetchers guarding a gate **throw**
  (`IOException`), never return `null` (see `OpenSubsonicCoverFetcher`).
- `YOUTUBE_ACCOUNT_ENABLED` off: YouTube requests carry no cookies (signed out; search still
  works, no library or like sync). The saved session is kept.

## OpenSubsonic

- The sign-in goes in a POST form body, never the address (the Ktor logger prints addresses).
  Covers are fetched with a POST through the provider too.
- Self-signed servers: trust on first use, only for chains ending in a self-signed cert; the cert
  is pinned per host and a changed one prompts before it's trusted (`SelfSignedTrustManager`).
- Volume levelling needs ReplayGain tags on the server; songs without them aren't levelled.
  Measuring server songs in the app was dropped, don't propose it.

## YouTube

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
- Details: `.claude/docs/youtube-streams.md`, `.claude/docs/playback-pipeline.md`.

## Proxies

The app proxy (`NETWORK_PROXY_*`, Network page) covers every request; a source whose own proxy is
on uses that instead. A proxy that's on but has no host or port blocks the requests it covers, it
never falls through. A new source reads its proxy through `sourceProxy()` (`Proxies.kt`); the
shared Ktor client gets `networkProxy()` for free. Library pitfalls: `.claude/docs/proxies.md`.

## Settings pages

Each source has its own settings page behind a "Configure X" row on the Network page (summary: a
short status such as "Off", the account, or the server's name and version). A `MainSwitch` sits at
the top; when off, nothing else shows and saved values are kept. New options go on the source's
page under a section heading, never back on the Network page.
