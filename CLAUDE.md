# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

Android app (Kotlin, Jetpack Compose, Material 3), built with the Gradle wrapper. Dependencies come from the version catalog at `gradle/libs.versions.toml` (`libs.*` aliases); repositories are declared only in `settings.gradle.kts` (`FAIL_ON_PROJECT_REPOS`), so don't add them to module build files.

- Build debug APK: `./gradlew assembleDebug`
- Unit tests: `./gradlew test` (JVM, `app/src/test`)
- Single unit test:
  `./gradlew :app:testDebugUnitTest --tests "me.knighthat.kreate3.ExampleUnitTest"`
- Instrumented tests (needs device/emulator): `./gradlew connectedDebugAndroidTest`
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
- Credentials never go in `Preferences`: passwords, API keys, tokens, cookies, usernames and
  account or channel ids. Declare them in `Secrets`, which keeps them in `secrets.preferences_pb`
  in the profile's folder under `noBackupFilesDir`, so they never leave the device. To move an
  existing preference there, add its name to `SecretStore.MOVED_FROM_PREFERENCES`.
- External sources sync liked songs, albums, artists and playlists separately. Of the synced data,
  only liked songs are written to the database, and the server wins: `liked_at` is the server's
  like time, or the current time when the song is inserted or newly liked (an already liked song
  keeps its time); a liked song of that origin the server doesn't list as liked gets
  `liked_at = NULL`, while dislikes (`-1`) are left alone. Albums, artists and playlists are never
  stored: lists and detail screens show only what the server returns, live. Signing out or turning
  off a source or its sync only stops syncing, it never clears synced likes.

## License

The project is GPL-3.0-or-later (`LICENSE`). The copyright notice lives in `.idea/copyright/GPL3.xml` (the Android Studio copyright profile, applied automatically to new files). Start every new source file (`.kt`, `.kts`) with that notice as a `/* ... */` block comment above the `package` line, with `$today.year` replaced by the current year.
