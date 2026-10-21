---
paths:
  - "database/**"
  - "provider/**"
  - "app/src/main/kotlin/**/source/**"
  - "app/src/main/kotlin/**/playback/**"
---

# Data model

## Reactions are tri-state

`likedAt` (songs), `bookmarkedAt` (albums) and `followSince` (artists) are `Instant?`:
`null` = neutral, epoch millis `-1` = disliked, any other value (including `0`) = positive, the
value being when it happened. Never treat `!= null` as "liked"; check `-1` first. Anything mapping
them to a rating or UI state needs three states (`MediaItemDsl.kt` maps to `ThumbRating`, not
`HeartRating`).

## What gets stored

- Store a provider's own ids and real URLs as-is. No namespaced ids (`opensubsonic:<host>:<id>`)
  and no invented URI schemes in the database: `origin` tells sources apart, so scope queries by
  `origin`. A URL that needs credentials is stored without them; they're added when it's loaded.
  An id collision between origins is a primary-key question to raise, not something to hide with
  prefixes. Known, unfixed: album/artist PK is `id` alone.
- Don't store server-owned values the server can expire or revoke (share links, tokens,
  permission-derived state): fetch them when needed (e.g. reuse a matching share from `getShares`,
  create one only when none exists). Caching for the app's run is fine when being wrong is cheap.
- Browsing is live. YouTube search/browse results and every remote album, artist and playlist are
  shown from the provider and never written. A remote song is stored when it **starts playing**
  (`PlayedSongRecorder`: added to the library, no like) or when synced as liked. A planned Import
  button will store a remote item only on an explicit user action.
- Sync details beyond CLAUDE.md:
    - `liked_at`: the server's like time replaces the stored one; with none given, an already liked
      song keeps its time and a newly liked/inserted one gets now − index ms (keeps server order).
      Rows are never deleted by sync, only `liked_at` changes. Dislikes can't be listed by YouTube
      or
      Subsonic, so sync never touches them.
    - Each source has four per-profile settings: liked-songs sync plus show/hide of the live albums,
      artists and playlists. OpenSubsonic albums/artists are `SyncMode` (`OFF`/`LIKED`/`ALL`).
    - Rejected, don't propose again: a sync that never inserts and shows liked songs live (likes
      need their timestamp), and an OpenSubsonic "all songs" mode (fetching the whole library bloats
      the app).
- Local album art is meant to be stored as the MediaStore album art id, with the `content://` URI
  built at render time (the stored URI form differs by Android version). Not done yet: don't build
  on the stored URI as if it were final.

## Room schema

- Schema changes: ask once whether to change entities in place at the current version (every
  install then needs its data cleared or a reinstall) or to raise `@Database(version)` with a
  migration. **Never raise the version or add `AutoMigration`/`Migration` without that answer.**
  In place: delete any stray `2.json`, and after the change say the installed app needs its data
  cleared or a reinstall.
- Room's identity hash ignores column order and doesn't rewrite `1.json` while the hash matches:
  after reordering entity fields, delete `1.json` and run
  `./gradlew :database:kspDebugKotlin --rerun-tasks :database:copyRoomSchemas` (no data clear
  needed).
- A version bump also breaks restoring older backups, see `storage.md`.
- Keep `BundledSQLiteDriver` (~1.2 MB per ABI): queries use `NULLS LAST`, aggregate
  `FILTER (WHERE …)` and `json_group_array`, which the framework SQLite only has from API 31
  (minSdk is 24). Unit tests run `AndroidSQLiteDriver` on the host's newer SQLite, so they won't
  catch a query that breaks on old devices. Shrink the native library through packaging instead.
