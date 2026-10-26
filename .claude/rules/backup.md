---
paths:
  - "app/src/main/kotlin/**/backup/**"
  - "**/PreferencesBackup.kt"
  - "**/DatabaseBackup.kt"
---

# Zip backup / restore (`backup/Backup.kt`)

- Contents: `manifest.json`, `kreate.db`, global prefs, `profiles/<id>/preferences.preferences_pb`.
  Never secrets.
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
