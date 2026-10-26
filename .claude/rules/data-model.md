---
paths:
  - "database/**"
  - "provider/**"
  - "app/src/main/kotlin/**/source/**"
  - "app/src/main/kotlin/**/playback/**"
---

# Data model

Sync and reactions sent to servers: `sync.md`; Room schema and paging: `database.md`.

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
- Local album art is meant to be stored as the MediaStore album art id, with the `content://` URI
  built at render time (the stored URI form differs by Android version). Not done yet: don't build
  on the stored URI as if it were final.
