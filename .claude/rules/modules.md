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
them).

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

`platform` (`github` default, `fdroid`, `patreon`) × `abi` (`armv7`, `arm64`, `x86`, `x86_64`,
`universal` default). Unflavored `:app` task names don't exist: use `installGithubUniversalDebug`,
`testGithubUniversalDebugUnitTest`, etc. Tests run only on `githubUniversal` (see `testing.md`).
versionCode = catalog
`versionCode` × 10 + ABI digit (universal 0, armv7 1, arm64 2, x86 3, x86_64 4). APKs are
`app/build/outputs/apk/<platform><Abi>/<type>/kreate-<abi>.apk`.
