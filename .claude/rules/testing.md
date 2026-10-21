---
paths:
  - "**/src/test/**"
  - "**/src/androidTest/**"
---

# Tests

- `:app` tests run only on the `githubUniversal` variant:
  `./gradlew :app:testGithubUniversalDebugUnitTest`.
  A root `testDebugUnitTest` silently skips `:app`.
- To force tests to run again, name the real tasks with `--rerun` (e.g.
  `testGithubUniversalDebugUnitTest --rerun`). `./gradlew test --rerun` and `cleanTest test` report
  success in under a second without running anything.
- No tests containing IP addresses, usernames or passwords (proxy/server credentials). A fake host
  name with empty credentials is fine. Say what stays untested instead. Older tests with fake
  credentials (`OpenSubsonicProviderTest`, `PreferencesBackupTest`) predate this; leave them unless
  asked.
- Never run `connectedGithubUniversalDebugAndroidTest` against a device with real data: it
  uninstalls the app and wipes its sign-ins. Use `am instrument` (`.claude/docs/emulator.md`).
- A fresh git worktree has no `local.properties`: copy it from the main checkout or Gradle fails
  with "SDK location not found".

## Pitfalls

- `asSnapshot()` (paging-testing) hangs forever on `PagingData.empty()`, which repositories emit
  with no active user. Test those cases another way.
- Database tests use `AndroidSQLiteDriver` under Robolectric (`BundledSQLiteDriver` has no host
  library). The bundled driver is only testable on a device.
- The `--add-exports=java.base/jdk.internal.access=ALL-UNNAMED` and
  `--add-opens=java.base/java.io=ALL-UNNAMED` jvmArgs in `testOptions` are required for
  Robolectric on JDK 21. Don't remove them.
- `fun x() = runBlocking { … }` whose last expression isn't `Unit` makes JUnit reject the whole
  class: end it with `Unit`.
- A ViewModel test using `Dispatchers.setMain` must not leave work on `Dispatchers.Default` (it
  resumes after `resetMain()` and fails the next test). Inject the background dispatcher (like
  `LiveDetailViewModel(background)`).
- App tests run on a plain `Application` without Koin: code reading `Preferences` takes the value
  through an `internal` constructor/parameter instead (`SongCaches(context, cacheSize, …)`).
- Media3 `SimpleCache` allows one instance per folder per process: `release()` both caches of
  `SongCaches` in `@After`. Get a Context with `RuntimeEnvironment.getApplication()`.
- `ProfileUserSyncTest` timing out in `start()` is a regression (a real race was fixed), not a
  flake.
- QuickJS can't load in JVM tests (`UnsatisfiedLinkError` → `YouTubeException`), so YouTube cipher
  paths only run on a device; provider tests lack Robolectric (`Uri` is stubbed).
