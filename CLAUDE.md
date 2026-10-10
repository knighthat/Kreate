# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

Android app (Kotlin, Jetpack Compose, Material 3), built with the Gradle wrapper. Dependencies come from the version catalog at `gradle/libs.versions.toml` (`libs.*` aliases); repositories are declared only in `settings.gradle.kts` (`FAIL_ON_PROJECT_REPOS`), so don't add them to module build files.

- Build debug APK: `./gradlew assembleDebug`
- Unit tests: `./gradlew test` (JVM, `app/src/test`)
- Single unit test: `./gradlew :app:testDebugUnitTest --tests "me.knighthat.kreate.ExampleUnitTest"`
- Instrumented tests (needs device/emulator): `./gradlew connectedDebugAndroidTest`
- Lint: `./gradlew lint`

## Notes

- Package/namespace is `me.knighthat.kreate`; minSdk 24, compileSdk/targetSdk 37, Java 21 source/target.
- Release minification is off.

## License

The project is GPL-3.0-or-later (`LICENSE`). The copyright notice lives in `.idea/copyright/GPL3.xml` (the Android Studio copyright profile, applied automatically to new files). Start every new source file (`.kt`, `.kts`) with that notice as a `/* ... */` block comment above the `package` line, with `$today.year` replaced by the current year.
