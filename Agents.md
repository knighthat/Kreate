# Kreate — API 24 Desugaring Investigation

  ## Branch: `fix/api24-desugaring`

  ### Context

  User reported crashes on Android 7 (API 24) / Huawei P9 Lite when logging into Discord RPC and syncing YouTube accounts.

  ### Findings

  After auditing the codebase, **core library desugaring was already correctly configured in `composeApp/build.gradle.kts`** before this branch was created:

  ```kotlin
  compileOptions {
      isCoreLibraryDesugaringEnabled = true
      // ...
  }

  dependencies {
      coreLibraryDesugaring(libs.desugaring.nio)  // com.android.tools:desugar_jdk_libs_nio 2.1.5
  }
  ```

  `desugar_jdk_libs_nio` is a **superset** of `desugar_jdk_libs` — it includes all base Java 8+ API desugaring plus NIO support. No additional desugaring library is needed or should be added alongside it.

  `SecureRandom.getInstanceStrong()` was scanned for across `modules/innertube/`, `extensions/`, and the composeApp source — **no occurrences found**.

  ### What this branch adds

  - **`.github/workflows/build-debug-apk.yml`** — GitHub Actions workflow that builds a debug APK on every push to this branch and uploads it as a downloadable artifact. This lets you test APK builds directly from GitHub without needing Android Studio.

  ### Suggested next steps for debugging the crash

  1. Download the APK from the latest Actions run and test on the P9 Lite.
  2. If it still crashes, capture a logcat (`adb logcat`) during the crash to get the actual stack trace — this will reveal whether the root cause is desugaring, a missing API, a network/OAuth error, or something else entirely.
  3. The discord RPC crash may be in the `modules/kizzy` submodule — check its own build.gradle.kts for desugaring configuration if needed.
  