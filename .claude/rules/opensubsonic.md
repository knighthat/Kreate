---
paths:
  - "**/opensubsonic/**"
---

# OpenSubsonic

- The sign-in goes in a POST form body, never the address (the Ktor logger prints addresses).
  Covers are fetched with a POST through the provider too.
- Self-signed servers: only for chains ending in a self-signed cert, and only what the user
  accepted. `SelfSignedTrustManager` always runs and refuses an unaccepted cert with its details
  (`UntrustedCertificate.selfSigned`); the settings row shows them in a dialog (Accept pins the
  cert per host). While off, nothing of the cert shows; turning it off forgets the pinned cert, so
  allowing again asks again. While allowed, a different cert than the pinned one prompts as changed.
  The server address is edited on the sign-in screen, not the settings page.
- Volume levelling needs ReplayGain tags on the server; songs without them aren't levelled.
  Measuring server songs in the app was dropped, don't propose it.
- `getTopSongs` ranks by outside metadata (Last.fm/ListenBrainz on Navidrome) and is uneven: on
  one server it gave anywhere from 10 down to 0 songs per artist, and some servers don't implement
  it. Never rely on it alone: fill from `search3` by the artist's name, keeping only songs of that
  artist id (`OpenSubsonicProvider.topSongs`).
- The API can't change a playlist's picture (`getCoverArt` only reads). Navidrome can through its
  own API (`POST /auth/login`, then `POST`/`DELETE /api/playlist/<id>/image`, probed on 0.64.2),
  but the maintainer chose not to use it (2026-11-06): the edit screen shows the picture and never
  changes it. Don't propose it again.
