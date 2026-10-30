---
paths:
  - "preferences/**"
  - "app/src/main/kotlin/**/preferences/**"
  - "app/src/main/res/xml/**"
---

# Preferences and secrets

Zip backup and restore: `backup.md`.

## Layout

- One global DataStore (profile list, active profile, `GlobalPreferences`) plus one preferences file
  per profile. The
  database is shared: catalog tables once, per-user tables keyed by user id; never one DB per
  profile.
- Settings of something every profile shares (cache and download sizes, the image memory cache)
  go in `GlobalPreferences`, not `Preferences`; only the default profile changes them, other
  profiles see them read-only. `ProfileManager` is a frontend over two settings of the same file
  (`profile_ids`, `active_profile_id`), sharing its `PreferenceEngine`.
- The `preferences` module hides DataStore behind an interface and never leaks its types (the
  backend may change; hundreds of settings are expected). Key names are stable, never renamed;
  changes go through append-only `SchemaMigration` steps.
- `Preferences` holds user settings only. Values derived at runtime (the `THUMBNAIL` theme seed
  color, anything computed from playback) are parameters or in-memory state, never preferences.

## Failures

- Every store writes through `retrying` (at once, 0.5 s, 1 s) and reports what failed to
  `ProfileManager.reports` with its own `PreferenceFailure.Store`; each store and operation has a
  `snackbar_*` text. A setting whose save keeps failing goes back to what is stored; a failed read
  falls back to defaults.
- A new DataStore passes `replaceDamaged` to `createPreferencesDataStore`, reporting the damage.
  DataStore reads its file only when it opens and saves from memory after that, so damage is found
  only then and nothing is remembered yet: an empty file, or what can be rebuilt (the profile list
  from the `profiles/<id>/` folders).

## Secrets

- Credentials never go in `Preferences`: passwords, API keys, tokens, cookies, usernames and
  account or channel ids (the YouTube channel page id too). Declare them in `Secrets`, which keeps
  them in `secrets.preferences_pb` in the profile's folder under `noBackupFilesDir`, so they never
  leave the device. To move an existing preference there, add its name to
  `SecretStore.MOVED_FROM_PREFERENCES`; those entries are never removed, and a secret's key name is
  stable.
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
