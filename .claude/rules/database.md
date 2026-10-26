---
paths:
  - "database/**"
---

# Room schema and paging

## Schema

- Schema changes: ask once whether to change entities in place at the current version (every
  install then needs its data cleared or a reinstall) or to raise `@Database(version)` with a
  migration. **Never raise the version or add `AutoMigration`/`Migration` without that answer.**
  In place: delete any stray `2.json`, and after the change say the installed app needs its data
  cleared or a reinstall.
- A version bump also breaks restoring older backups, see `backup.md`.
- Room's identity hash ignores column order and doesn't rewrite `1.json` while the hash matches:
  after reordering entity fields, delete `1.json` and run
  `./gradlew :database:kspDebugKotlin --rerun-tasks :database:copyRoomSchemas` (no data clear
  needed).
- Keep `BundledSQLiteDriver` (~1.2 MB per ABI): queries use `NULLS LAST`, aggregate
  `FILTER (WHERE …)` and `json_group_array`, which the framework SQLite only has from API 31
  (minSdk is 24). Unit tests run `AndroidSQLiteDriver` on the host's newer SQLite, so they won't
  catch a query that breaks on old devices. Shrink the native library through packaging instead.

## Paging

- `pagedFlow(…, tables)` (and `UserSongRepositoryImpl.paged(tables = …)`) must name every table the
  query reads, including tables used only in a filter or `ORDER BY` subquery. A view covers only
  its own tables: `user_song_view` doesn't read `song_playlist_map`, so a playlist's song list
  watching the view alone missed songs added, moved or removed.
