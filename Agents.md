# Agents.md — Change Log

  ## Branch: fix/api24-desugaring

  ### Purpose
  Fix app crash on Android 7 (API 24) / Huawei P9 Lite caused by missing Java 8+ API desugaring
  and `SecureRandom.getInstanceStrong()` unavailability on pre-API 26 devices.

  ---

  ### Fix 1 — composeApp/build.gradle.kts

  **`isCoreLibraryDesugaringEnabled = true`** (in `android { compileOptions { } }`)
  - Status: Already present — no change needed.

  **`coreLibraryDesugaring(libs.desugar.jdk.libs)`** (in root `dependencies { }` block)
  - Status: ADDED — appended after existing `coreLibraryDesugaring(libs.desugaring.nio)`.
  - Reason: The NIO variant (`desugaring-nio`) was already present but the base `desugar_jdk_libs`
    library is needed for full API 24 compatibility of core Java 8 APIs used by innertube/extensions.

  ---

  ### Fix 2 — gradle/libs.versions.toml

  **Under `[versions]`**
  - ADDED: `desugarJdkLibs = "2.1.4"`

  **Under `[libraries]`**
  - ADDED: `desugar-jdk-libs = { module = "com.android.tools.build:desugar_jdk_libs", version.ref = "desugarJdkLibs" }`

  ---

  ### Fix 3 — modules/innertube/ and extensions/

  **`SecureRandom.getInstanceStrong()` replacement**
  - Status: NOT FOUND — no occurrences in modules/innertube/ or extensions/. No changes made.

  ---

  ### No other files were modified.
  