---
paths:
  - "**/build.gradle.kts"
  - "settings.gradle.kts"
  - "model/**"
---

# Modules

Shape: `:model` (pure Kotlin/JVM, no deps) ← `:database`, `:preferences`, `:lyrics`, `:provider`,
`:app`. `:provider` → `:database`, `:preferences`; `:app` → everything. `:lyrics` sees only
`:model`; `:database` and `:preferences` don't know each other (`ProfileUserSync` in `:app` bridges
them). `:discord` (Discord's Rich Presence) depends on no module; `:app` keeps the tokens and hands
it a
client and the access token. `:shazam` (naming songs) depends on no module either; `:app` records
the microphone and hands it raw PCM.
`:api` (the project's own API: Patreon sign in, token refresh) depends on no module either; only
`patreon` has it (`patreonImplementation`, app code in `app/src/patreon/`; each platform registers
its own Koin `platformModule` in `app/src/<platform>/`, with its own screens as `PlatformEntries`
so `main` never names them; UI checks `BuildConfig.FLAVOR_platform == "patreon"`);
`:app` hands it a client and stores the tokens. Its addresses are `KREATE_API_URL` and
`KREATE_FILES_URL`
(environment, else `local.properties`) at build time, never literals in code; a build without one
has no such API.

- Keep modules independent: a module depends only on `:model` plus libraries unless it truly can't
  work without another. Don't add `project(":x")` to reach one or two types.
- Shared **vocabulary** (enums, ids, tiny data classes; no I/O, Android, Room, Koin or logic) that
  two or more separated modules need goes to `:model`. Its rules are in the header of
  `model/build.gradle.kts`.
- Shared **behavior** doesn't: the consumer module declares an interface and `:app` implements it
  and registers it in Koin (`bind` + `getAll`), like `ActiveUser`/`ProfileUserSync` or
  `YouTubeLyricsSource`.
- Enums are stored by name (Room and `enumCodec`): moving one between packages is safe, renaming a
  value is not.

## `:app` flavors

`platform` (`github` default, `foss`, `patreon`) × `abi` (`armv7`, `arm64`, `x86`, `x86_64`,
`universal` default). Unflavored `:app` task names don't exist: use `installGithubUniversalDebug`,
`testGithubUniversalDebugUnitTest`, etc. Tests run only on `githubUniversal` (see `testing.md`).
versionCode = catalog
`versionCode` × 10 + ABI digit (universal 0, armv7 1, arm64 2, x86 3, x86_64 4). `foss` must meet
F-Droid standards: no telemetry, no proprietary libraries (`verifyFoss…Dependencies` fails
`preBuild` on a banned group, extend `proprietaryGroups` in
`app/build.gradle.kts`), no dependency blob in the APK. Don't add a library there without checking
its
license and what it phones home.

`foss` also leaves out non-free network services (F-Droid's NonFreeNet): `:discord` and `:shazam`
are `githubImplementation`/`patreonImplementation` only, and the app code using them sits in
`app/src/nonfree/`, a source dir of `github` and `patreon`. What `main` calls from there exists
in `app/src/foss/` too (same name, a stand-in); `main` checks `BuildConfig.NON_FREE_NET` for UI that
says "Not available in FOSS build"; foss turns their settings off in each profile at start
(`NonFreeNetOff`), so they read as off. `app/src/foss/AndroidManifest.xml` removes what only those
services need (the Discord callback, `RECORD_AUDIO`). A new non-free service follows the same
split. Translations split by platform: `foss` and `github` get `app/src/crowdin/` (the merged
`values-*` in `res/`, a `TranslationUpdates` that does nothing in `kotlin/`); `patreon` ships
English alone (`BuildConfig.LANGUAGES` = `en`) and gets its languages from Crowdin's manifest and
their texts from the Crowdin SDK (`patreonImplementation` only), its `TranslationUpdates` and
manifest (removing the
SDK's unused sign in) in `app/src/patreon/`. APKs are
`app/build/outputs/apk/<platform><Abi>/<type>/kreate-<abi>.apk`.
