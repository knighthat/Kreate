# File index: `:preferences`

Paths are relative to `preferences/src/main/kotlin/me/knighthat/kreate3/`; checked against the tree
on
2026-10-20, verify a path still exists before relying on it. Map of the parts:
`.claude/file-index.md`.

- `preferences/Preferences.kt` — every setting (`object Preferences`), keys on disk never renamed.
  `Preference.kt` — single setting following the active profile.
- `preferences/Secrets.kt` + `Secret.kt` + `SecretStore.kt` — passwords/tokens in
  `noBackupFilesDir/profiles/<id>/secrets.preferences_pb` (`.claude/rules/storage.md`).
- `app/res/xml/backup_rules.xml` (API ≤30) + `data_extraction_rules.xml` (API 31+) — backup/transfer
  exclusions (`.claude/rules/storage.md`).
- `preferences/PreferencesBackup.kt` — preference files for a backup (global + profiles/<id>
  /preferences), path check, `restore`, schema versions.
- `preferences/ProfileManager.kt` — profile ids, active profile, file layout. `Profile.kt` —
  constants.
- `preferences/PreferenceEngine.kt` (active-profile snapshot, write retries/revert),
  `PreferenceFailure.kt` (load/save failure + reason, `FailureReports` owned by `ProfileManager`,
  `retrying` for every store; `Preferences.failures`, shown by `KreateApp`),
  `PreferenceStorage.kt` +
  `DataStorePreferenceStorage.kt` (per-profile DataStore), `PreferencesDataStore.kt`, `Codec.kt`,
  `SchemaMigration.kt` (GLOBAL/PROFILE/SECRET steps), `PreferencesModule.kt`.
- constant/ — `ThemeMode`, `ContrastLevel`, `OpenSubsonicAuth`, `StreamQuality` (
  LOW/MEDIUM/HIGH/ORIGINAL, order = ordinal), `SyncMode` (OFF/LIKED/ALL), `SleepTimerStep` (sleep
  timer add/remove amounts), `ProxyType` (HTTP/SOCKS), `AutoTapAction`
  (OPEN/PLAY_FROM_START/PLAY_RANDOMLY, Android Auto), `PlaybackFailureAction`
  (SKIP/RETRY_THEN_SKIP/RETRY_THEN_STOP/STOP, `retries`/`skips`) enums.
