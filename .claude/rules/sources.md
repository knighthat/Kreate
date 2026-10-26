---
paths:
  - "provider/**"
  - "app/src/main/kotlin/**/source/**"
  - "app/src/main/kotlin/**/playback/**"
  - "app/src/main/kotlin/**/ui/screen/settings/network/**"
  - "app/src/main/kotlin/**/ui/screen/detail/**"
---

# External sources (YouTube, OpenSubsonic)

Per source: `youtube.md`, `opensubsonic.md`; sync: `sync.md`.

## Gates

- Nothing contacts the OpenSubsonic server while `Preferences.OPEN_SUBSONIC_ENABLED` is off, saved
  credentials or not: check it before every request, stream and cover URL included, and treat off
  as "no source". `OPEN_SUBSONIC_AUTH` picks password or API key; the other is kept, unused.
- A Coil 3 `Fetcher` that returns `null` hands the data to the next fetcher (the Ktor network one),
  which would request the URL unsigned while the server is off. Fetchers guarding a gate **throw**
  (`IOException`), never return `null` (see `OpenSubsonicCoverFetcher`).
- `YOUTUBE_ACCOUNT_ENABLED` off: YouTube requests carry no cookies (signed out; search still
  works, no library or like sync). The saved session is kept.

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
