---
paths:
  - "**/opensubsonic/**"
---

# OpenSubsonic

- The sign-in goes in a POST form body, never the address (the Ktor logger prints addresses).
  Covers are fetched with a POST through the provider too.
- Self-signed servers: trust on first use, only for chains ending in a self-signed cert; the cert
  is pinned per host and a changed one prompts before it's trusted (`SelfSignedTrustManager`).
- Volume levelling needs ReplayGain tags on the server; songs without them aren't levelled.
  Measuring server songs in the app was dropped, don't propose it.
- `getTopSongs` ranks by outside metadata (Last.fm/ListenBrainz on Navidrome) and is uneven: on
  one server it gave anywhere from 10 down to 0 songs per artist, and some servers don't implement
  it. Never rely on it alone: fill from `search3` by the artist's name, keeping only songs of that
  artist id (`OpenSubsonicProvider.topSongs`).
