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

Disliked songs are hidden everywhere, always (no setting; the maintainer plans a screen of hidden
songs to take dislikes back). `UserSongRepositoryImpl.query` leaves them out of every library list,
`find` still gives them. Lists fetched live drop the ids of `UserSongRepository.dislikedIds` where
they're built (`source/DislikedSongs.kt` helpers; `LiveDetailViewModel` re-filters its state, so a
song disliked while shown goes at once; paged lists combine after `cachedIn`). Radio and Android
Auto use `listDislikedIds`. A new list of songs, local or live, must do the same.

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
  (`PlayedSongRecorder`: added to the library, no like), when it's added to a playlist or gets a
  like/dislike (the same store, `SongStore`: playlist links and reactions need its `song` row) or
  when synced as liked. A planned Import button will store a remote item only on an explicit user
  action.
- Sync details beyond CLAUDE.md:
    - `liked_at`: the server's like time replaces the stored one; with none given, an already liked
      song keeps its time and a newly liked/inserted one gets now − index ms (keeps server order).
      Rows are never deleted by sync, only `liked_at` changes. Dislikes can't be listed by YouTube
      or
      Subsonic, so sync never touches them.
  - OpenSubsonic likes go back to the server (`OpenSubsonicLibrary.react`, while its liked-songs
    sync is on): becoming liked stars the song, stopping unstars it. The API has no dislike, so a
    dislike stays on the device by the maintainer's choice; disliking a starred song only
    unstars it (else the next pull turns it back into a like). Never map a dislike to a rating.
    A pull that overlapped a reaction being saved or sent is dropped, not stored.
  - A local → remote sync that fails takes the local state back to what it was before (unless a
    newer reaction replaced it meanwhile) and shows an error message saying why; it is not
    retried. Applies to every source that sends changes to its server.
  - YouTube song reactions go both ways too (`YouTubeLibrary.react`, while signed in and its
    liked-songs sync is on): each change sends the new state, `like/like`, `like/dislike` or
    `like/removelike`. Signed out they stay on the device. The pull ("Liked Music") overrides
    neutral and liked; YouTube can't list dislikes, so they stay, but a local dislike of a song
    YouTube still lists as liked (made while signed out) becomes liked again: the sync wins, by
    the maintainer's choice (2026-10-22). Both libraries share the
    save → send → take back on failure and the dropped overlapping pull (`ReactionSender`).
  - Album Bookmark and artist Follow (detail screens' top bar): device items write
    `bookmarked_at`/`follow_since`; remote ones are never stored, the button shows and changes
    the source's own state live (`SourceToggle`): OpenSubsonic star (`albumId`/`artistId`),
    YouTube library save (`like/like` of the album's playlist) and subscription (the page's
    `channelId`, not the artist id). Sent whatever the sync settings. Signed out of YouTube the
    buttons still work, on the device only (`DeviceReactions`: the album or artist is stored
    then, like `SongStore` does for songs); signed in, YouTube's own state shows and local ones
    are ignored, by the maintainer's choice (2026-10-22). Server playlists get no like: neither
    API can keep one (maintainer, 2026-10-22).
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

## Paging

- `pagedFlow(…, tables)` (and `UserSongRepositoryImpl.paged(tables = …)`) must name every table the
  query reads, including tables used only in a filter or `ORDER BY` subquery. A view covers only
  its own tables: `user_song_view` doesn't read `song_playlist_map`, so a playlist's song list
  watching the view alone missed songs added, moved or removed.
