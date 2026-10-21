---
paths:
  - "preferences/**"
  - "app/src/main/kotlin/**/backup/**"
  - "app/src/main/kotlin/**/preferences/**"
  - "app/src/main/res/xml/**"
---

# Preferences, secrets and backups

## Layout

- One global DataStore (profile list, active profile) plus one preferences file per profile. The
  database is shared: catalog tables once, per-user tables keyed by user id; never one DB per
  profile.
- The `preferences` module hides DataStore behind an interface and never leaks its types (the
  backend may change; hundreds of settings are expected). Key names are stable, never renamed;
  changes go through append-only `SchemaMigration` steps.
- `Preferences` holds user settings only. Values derived at runtime (the `THUMBNAIL` theme seed
  color, anything computed from playback) are parameters or in-memory state, never preferences.

## Secrets (details beyond CLAUDE.md)

- Identifying account data counts as a secret too (usernames, YouTube channel page id).
- A secret's key name is stable; `MOVED_FROM_PREFERENCES` entries are never removed.
- Code that acts on a secret reads it with the suspend `current()`: an empty value at startup (e.g.
  the channel page id) would make like sync act on the wrong account and null out likes.
- The file isn't encrypted; Keystore encryption hasn't been asked for.

## Files that must stay on the device

A new file that must never leave the device (credentials, tokens, device-specific state) is kept
out of backup and device transfer. Ask once whether to do that in the same change without asking
each time; the change summary always says so:

- Fixed path: add an `<exclude>` to `res/xml/backup_rules.xml` (API ≤ 30) **and**
  `data_extraction_rules.xml` (both `<cloud-backup>` and `<device-transfer>`).
- Path with a variable part (`profiles/<id>/…`): the XML has no wildcards, so put it under
  `noBackupFilesDir`.

## Zip backup / restore (`backup/Backup.kt`)

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
