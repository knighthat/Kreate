---
paths:
  - "app/src/main/kotlin/**/backup/**"
  - "**/PreferencesBackup.kt"
  - "**/DatabaseBackup.kt"
  - "database/**/legacy/**"
---

# Zip backup / restore (`backup/Backup.kt`)

- Contents: `manifest.json`, `kreate.db`, global prefs, `profiles/<id>/preferences.preferences_pb`,
  and the pictures users picked, `profiles/<id>/custom_thumbnails/<file>` (`CoverFiles.backupFiles`;
  they live in `noBackupFilesDir`, so only this zip carries them, never the system's or the cloud's
  backup). A restore replaces them with the zip's. Never secrets. An older app refuses a zip with
  them as "not a backup" (unknown paths are refused).
- Restore never overwrites in place: it's staged under `noBackupFilesDir/restore` and applied on
  the next start; current data is first exported to `cacheDir/rollback.zip`, which is staged
  instead if applying fails. `applyPendingRestore` always deletes the pending folder (crash-loop
  guard); keep that.
- Only the default profile may back up or restore (a backup holds every profile).
- File name: `kreate_backup_{versionName}_{yyyy-MM-dd_HH-mm-ss}.zip`.
- A new per-profile file that belongs in backups needs `PreferencesBackup.files()`/`isBackupPath`
  updated.
- The manifest rejects only backups from newer versions. Bumping the DB version means Room must
  migrate a restored older DB; without migrations it fails after the restore is applied and the
  rollback is gone. Raise this whenever a DB version bump comes up.

## Import from the old Kreate app (`LegacyBackup`, `database/…/legacy/`)

- One zip: `manifest.json` (`format`, `source` = `kreate-legacy`, `profiles`: `name`, `database`,
  `default`) and `profiles/<number>/data.db`. Other entries are skipped, never extracted.
- It replaces everything like a restore: the archive's profiles become the only ones with default
  settings (old preferences are not imported); `default` keeps `Profile.DEFAULT_ID`, the others get
  new UUIDs, user and profile share the id. Secrets stay.
- `downloaded.json` (optional, a JSON array of YouTube Music ids) is kept as
  `noBackupFilesDir/legacy_downloads.json`, moved there by `applyPendingRestore` (every restore
  drops a
  list left from before). After the restart `KreateApp` asks: Download all / Select /
  Close (the only way out; it forgets the list). The picker has Clear all / Select all and no
  Download
  button: leaving it (arrow or system back) downloads what is picked, nothing picked is fine and
  forgets the list. Only songs some user has in `user_song` are offered:
  the files are shared, then each user who has the song is marked `downloaded_at`, never a new
  library row (`SongDownloads.downloadUnmarked` + `markDownloadedForEveryUser`; Download all builds
  its items from
  the library and asks YouTube for nothing, only the picker looks songs up), because a download
  no user marks is swept at the next start.
- Staged through `Backup.stageBuilt`, so the rollback and `applyPendingRestore` rules above apply.
- Old data is read by column name, not by version. Left out on purpose: device songs, albums and
  artists (the scan finds them; albums and artists by a numeric id, their `youtube` flag is
  unreliable), the saved queue (no order), lyrics (no source), downloads (the files can't be moved).
  Playlists become local. `modified:` texts become the user's overrides.
- The bundled SQLite driver has no host library: the `:app` test covers the archive only, the
  database build is tested in `:database` with the framework driver.
