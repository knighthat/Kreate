# InnerTubeX

[![CI](https://github.com/MetrolistGroup/innertubex/actions/workflows/ci.yml/badge.svg)](https://github.com/MetrolistGroup/innertubex/actions/workflows/ci.yml)
[![JitPack](https://jitpack.io/v/MetrolistGroup/innertubex.svg)](https://jitpack.io/#MetrolistGroup/innertubex)
[![License: GPL-3.0](https://img.shields.io/badge/License-GPL--3.0-blue.svg)](LICENSE)

InnerTubeX is an extended Android, JVM, and iOS Kotlin Multiplatform client for
YouTube's InnerTube APIs. It retains standard browse, search, account,
playlist, and player operations while adding:

- SABR/UMP audio and video streaming with seekable segmented sources.
- SABR video representation selection for host video playback.
- Player cipher deobfuscation through Zemer, Faraday, EJS, and QuickJS.
- Layered remote, parsed-script, and cached cipher fallbacks.
- Isolated authenticated sessions with generation-safe cancellation.
- Channel discovery, account switching support, and streaming music uploads.
- Bounded remote bodies and protocol state with strict media URL validation.
- Injectable logging, player configuration, diagnostics, and token providers.

## Install

```kotlin
repositories {
    maven("https://jitpack.io") {
        content { includeGroup("com.github.MetrolistGroup.innertubex") }
    }
}

dependencies {
    implementation("com.github.MetrolistGroup.innertubex:innertubex:0.7.2")
}
```

## Minimal Usage

```kotlin
val client = InnerTube(
    httpClient = HttpClient(OkHttp),
)

val response = client.search(YouTubeClient.WEB, query = "query").body<SearchResponse>()
client.close()
```

The caller owns the supplied `HttpClient`. `InnerTube.close()` cancels only
library-owned visitor-data work and session-bound requests. Authenticated
sessions contain sensitive cookies and SAPISID values; follow
[`SECURITY.md`](SECURITY.md) when storing or logging them.

## Stream Extraction

The reusable extraction stack handles client selection, player configuration,
cipher transforms, PO-token contracts, direct/HLS/SABR transport selection,
format selection, and bounded media probing:

```kotlin
val configStore = RemotePlayerConfigStore(
    httpClient = client.httpClient,
    repository = PlayerConfigRepository.disabled(),
)
val cipher = YouTubeCipherService(client.httpClient, configStore)
val extractor = InnerTubeExtractor(
    configParser = YtConfigParserImpl(client.httpClient, client, configStore, cipherService = cipher),
    cipherService = cipher,
    innerTube = client,
)

val stream = extractor.extract(
    videoId = "dQw4w9WgXcQ",
    hints = ContentHints(wantVideo = false),
    audioQuality = AudioQuality.HIGH,
)
```

### Optional authenticated TV discovery

Hosts with an independently confirmed Premium entitlement may opt into one
additional, scoped TV comparison:

```kotlin
val stream = extractor.extractWithAuthenticatedTvDiscovery(
    videoId = "dQw4w9WgXcQ",
    confirmedPremium = hostConfirmedPremium,
    credentialProvider = object : TvBearerCredentialProvider {
        override suspend fun getCredential(videoId: String, sessionGeneration: Long) =
            hostAcquireShortLivedTvCredential(sessionGeneration)
        override suspend fun isCredentialCurrent(credential: TvBearerCredential) =
            hostCredentialIsStillCurrent(credential)
    },
    hints = ContentHints(wantVideo = false),
    audioQuality = AudioQuality.HIGH,
)
```

The provider owns consent, acquisition, secure storage, refresh, and revocation.
It should return a short-lived credential scoped to the existing `TVHTML5` or
`TVHTML5_DOWNGRADED` manifest and current session generation. The library calls
this path only when explicitly requested, isolates the bearer from cookies and
media requests, and keeps the ordinary result unless the TV audio result is
strictly higher quality. This is experimental and audio-only: video requests
use ordinary extraction, and Premium does not guarantee a better
response, and no OAuth or credential harvesting is provided.

The catalog also exposes four explicit, unverified probes through the existing
`playbackClientOverrideId` hint: `IOS_MUSIC` (26), `ANDROID_KIDS` (18),
`ANDROID_PRODUCER` (91), and `MEDIA_CONNECT_FRONTEND` (95). They are `PROBE_ONLY`, never
automatic fallbacks, and their content/transport support remains conservative.
No DASH playback is implemented by these probe profiles.

Applications can inject `TokenProvider`, `ClientHealthMonitor`,
`ClientFallbackStrategy`, and `InnerTubeLogger` implementations. Platform token
minting, persistence, playback engines, UI, and application settings remain
host-owned. Never log an `ExtractedStream`, PO-token values, signed URLs, or
authenticated session fields; library model `toString()` implementations redact
those values as defense in depth.

## Standalone playback diagnostics

Run `./gradlew :harness:run --args='--help'` to see the offline CLI help, or
`--args='--list-clients'` for the offline profile catalog. The source-built
JVM harness performs opt-in 90-second paced audio playback with real backward/forward
transport seeks, HLS/direct/SABR profiles, a quick `--seconds` smoke mode and
sanitized local reports. Run `--help` for options. Live checks are never part
of `allTests`.

## Development

```bash
./gradlew ktlintFormat
./gradlew allTests ktlintCheck apiCheck assemble publishToMavenLocal
```

Enable the repository hook with `git config core.hooksPath .githooks`.
Contributions and releases are documented in [`CONTRIBUTING.md`](CONTRIBUTING.md)
and [`RELEASING.md`](RELEASING.md).

## Status

InnerTubeX uses experimental `0.x` versioning. YouTube can change private APIs,
player scripts, and SABR behavior without notice, so pin exact versions and
review [`CHANGELOG.md`](CHANGELOG.md) before upgrading.

## Disclaimer

InnerTubeX is an independent open-source project. It is not affiliated with,
endorsed by, or sponsored by YouTube or Google. YouTube and Google are
trademarks of their respective owners. Users are responsible for complying
with applicable terms of service and law.

## License

[GNU General Public License v3.0](LICENSE)
