# File index: `:provider`

Paths are relative to `provider/src/main/kotlin/me/knighthat/kreate3/`; checked against the tree on
2026-10-20, verify a path still exists before relying on it. Map of the parts:
`.claude/file-index.md`.

- `Provider.kt` — interface every source implements (search, details, library sections); answers
  with DB models, never writes. `Providers.kt` — Origin → Provider lookup. `ProviderModule.kt` —
  Koin (register new providers here and in `Origin`). `SearchResults.kt`, `LibrarySection.kt` (
  synced library parts). `ReactionSender.kt` (shared by the OpenSubsonic and YouTube libraries:
  saves a song reaction, sends it, takes it back on failure; keeps a liked-songs pull that
  overlapped one from being stored; `isLike`/`isDislike`).
- local/`LocalProvider.kt` — reads device songs back from DB.
- opensubsonic/ — `OpenSubsonicProvider.kt` (API client: ping, auth check, library, details, shares,
  cover URLs, `stream(id, quality)` via transcode decision or maxBitRate, `extensions()`; gated by
  `OPEN_SUBSONIC_ENABLED` — `.claude/rules/sources.md`), `OpenSubsonicLibrary.kt` (starred-song
  sync both ways: pulls stars, `react` stars/unstars a song liked/unliked in the app,
  `openSubsonicSyncSetting`), `OpenSubsonicSettings.kt`, `OpenSubsonicClients.kt` (dedicated
  OkHttp client), `OpenSubsonicMappers.kt` (JSON → models, `toSongFormat` = `raw`,
  `toTranscodedFormat`), `ServerStream.kt` (`ServerStream`, `RAW_FORMAT`, Content-Type → format
  tag), `SelfSignedTrustManager.kt` (cert pinning, `CertificatePin`), `ServerCheck.kt`
  (`ServerInfo.name`),
  `OpenSubsonicServer.kt` (object: what the server last answered with and its address, in memory;
  `name`/`version` of the active profile's server, every answer of `request` records it),
  `ServerException.kt`, `OpenSubsonicSearch.kt` (`searchLibrary` result: RemoteSong/RemoteItem
  lists), `Shareable.kt`, `ArtistWithSongs.kt`.
- youtube/ — `YouTubeProvider.kt` (public API: search, suggestions, album/artist/playlist pages,
  browse, sign-in session, channels, lyrics; `isYouTubeSession`), `InnerTube.kt` (HTTP +
  `YouTubeSession` + `YouTubeException`; `stream` delegates to `YouTubeStreams.kt`, the innertubex
  extractor: signed-out and session extractors, `StreamQuality.audioQuality`, Original = session +
  premium hint), `WebPoTokenProvider.kt` + `PoTokenWebView.kt` + `BotGuard.kt` (PO tokens via a
  hidden WebView, page in `provider/src/main/assets/po_token.html`), `YouTubeParser.kt` (InnerTube
  JSON → models), `YouTubeJson.kt` (JSON path helpers), `YouTubeModels.kt` (`YouTubeItem`,
  page/section/account/channel models, `YouTubeSearchFilter`, `YouTubeStream`),
  `YouTubeRegion.kt` (`YOUTUBE_REGIONS` from YouTube's location picker, `youTubeRegion()`: the
  `gl` sent, chosen or the device's), `YouTubeLibrary.kt` (liked-song sync both ways: pulls "Liked
  Music", `react` sends like,
  dislike or neither while signed in; `youTubeSyncSetting`), `YouTubeLinks.kt` (share links,
  `parse`/`find` read `music.youtube.com` links back into a `YouTubeLink` Song/Album/Artist/
  Playlist, `resizeArtwork`).
- test resources: `provider/src/test/resources/youtube/*.json` (captured InnerTube responses),
  `certs/*.pem`.

## Proxies (provider and app)

Provider `Proxies.kt` (`SourceProxy` Off/Incomplete/Ready, `sourceProxy()` reader,
`OkHttpClient.Builder.proxy(source, proxy)` — HTTP proxyAuthenticator, SOCKS via a process-wide
java.net.Authenticator keyed by proxy host:port, Incomplete = interceptor that fails every request),
`HttpClientFactory.kt` (app's Ktor setup, bound in NetworkModule), `youtube/YouTubeClients.kt` (
rebuilt per proxy with own pool; `youTubeProxy()`, `isYouTubeHost()`); `OpenSubsonicClients` keys on
proxy too. App: `source/SourceNetworkClient.kt` (Coil routes YouTube hosts to
`YouTubeProvider.httpClient()`), `SongDataSource` has a YouTube upstream (
`YouTubeProvider.streamClient()`), settings `ProxySettings.kt` (section at the end of both source
pages; `PreviewProxySettings` is an off proxy for previews), `network/youtube/SignInProxy.kt` (
androidx.webkit ProxyController override while the sign-in
screen shows; SOCKS with a username refused, Chromium can't sign in to SOCKS).
