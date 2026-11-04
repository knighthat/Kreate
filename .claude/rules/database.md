---
paths:
  - "database/**"
---

# Room schema, SQL text and paging

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

## SQL text

- SQL goes in `"""` raw strings, one clause per line, never `"…" +` pieces with trailing spaces or
  `\n`. In a raw string `\` is one backslash: write `ESCAPE '\'`, not `'\\'`.
- `@Query` and other annotation arguments are indented raw strings (annotations take no
  `.trimIndent()`). Queries built at runtime (`RoomRawQuery`) end in `.trimIndent()`; a
  `const val` stays a plain raw string.
- A `@DatabaseView` `value` is part of Room's identity hash, whitespace included: reindenting or
  reformatting it counts as a schema change, so raise `DATABASE_VERSION` with a migration
  (`AutoMigration` recreates views) and keep the old `N.json` as it was at `HEAD`, since a build
  rewrites it when the hash differs.

## Paging

- `pagedFlow(…, tables)` (and `UserSongRepositoryImpl.paged(tables = …)`) must name every table the
  query reads, including tables used only in a filter or `ORDER BY` subquery. A view covers only
  its own tables: `user_song_view` doesn't read `song_playlist_map`, so a playlist's song list
  watching the view alone missed songs added, moved or removed.
