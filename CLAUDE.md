# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

Android app (Kotlin, Jetpack Compose, Material 3), built with the Gradle wrapper. Dependencies come from the version catalog at `gradle/libs.versions.toml` (`libs.*` aliases); repositories are declared only in `settings.gradle.kts` (`FAIL_ON_PROJECT_REPOS`), so don't add them to module build files.

- Build debug APK: `./gradlew assembleGithubUniversalDebug` (flavors: `platform` × `abi`)
- Unit tests: `./gradlew test` (JVM, `app/src/test`)
- Single unit test:
  `./gradlew :app:testGithubUniversalDebugUnitTest --tests "me.knighthat.kreate3.ExampleUnitTest"`
- Instrumented tests (needs device/emulator): `./gradlew connectedGithubUniversalDebugAndroidTest`;
  it uninstalls the app and wipes its data, read `.claude/rules/testing.md` before running it
- Lint: `./gradlew lint`

## Notes

- Package/namespace is `me.knighthat.kreate3`; minSdk 24, compileSdk/targetSdk 37, Java 21
  source/target.
- Release builds are minified and resource-shrunk by R8; obfuscation is off (`-dontobfuscate`).
- Enum values use SCREAMING_SNAKE_CASE (`NORMAL`, `AUTOMATIC_DYNAMIC`), not PascalCase.
- In a class or object, declare properties before functions.
- Work that must outlive a screen runs on the global `CoroutineScope` (Koin singleton,
  `SupervisorJob` + `Dispatchers.Default`); in UI code use `rememberCoroutineScope()` /
  `viewModelScope` instead.
- Keep the main thread free: suspend functions of providers are main-safe (they read and parse
  answers off the caller's thread); work a screen starts does its mapping and list building in
  `withContext(Dispatchers.Default)` (a custom `PagingSource.load` too, Paging calls it on the
  collector's thread); long CPU work in the background runs on threads at
  `THREAD_PRIORITY_BACKGROUND`.
- Rules load from `.claude/rules/` by the paths they cover; `.claude/rule-index.md` lists which
  rule or doc covers a topic. Check it before work no loaded rule covers (builds, planning, a new
  area), and read only the rule or doc it names. Read `.claude/docs/` when a rule points there.

## License

The project is GPL-3.0-or-later (`LICENSE`). The copyright notice lives in `.idea/copyright/GPL3.xml` (the Android Studio copyright profile, applied automatically to new files). Start every new source file (`.kt`, `.kts`) with that notice as a `/* ... */` block comment above the `package` line, with `$today.year` replaced by the current year.
