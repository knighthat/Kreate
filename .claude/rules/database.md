---
paths:
  - "database/**"
---

# Room schema and paging

## Schema

- The app is in production: every schema change raises `DATABASE_VERSION` and ships a `Migration`
  (or `AutoMigration` when Room can derive it), and exports the new `N.json` under `schemas/`.
  Never edit entities in place or tell the maintainer to clear data. Ask the maintainer once
  whether to write migrations by hand or let `AutoMigration` do it, and whether each version bump
  needs their approval.
- A version bump also decides whether older backups restore, see `backup.md`.
- Room's identity hash ignores column order and doesn't rewrite the current `N.json` while the
  hash matches: after reordering entity fields, delete that `N.json` and run
  `./gradlew :database:kspDebugKotlin --rerun-tasks :database:copyRoomSchemas` (no version bump
  or data clear needed).
- Keep `BundledSQLiteDriver` (~1.2 MB per ABI): queries use `NULLS LAST`, aggregate
  `FILTER (WHERE …)` and `json_group_array`, which the framework SQLite only has from API 31
  (minSdk is 24). Unit tests run `AndroidSQLiteDriver` on the host's newer SQLite, so they won't
  catch a query that breaks on old devices. Shrink the native library through packaging instead.

## Paging

- `pagedFlow(…, tables)` (and `UserSongRepositoryImpl.paged(tables = …)`) must name every table the
  query reads, including tables used only in a filter or `ORDER BY` subquery. A view covers only
  its own tables: `user_song_view` doesn't read `song_playlist_map`, so a playlist's song list
  watching the view alone missed songs added, moved or removed.
