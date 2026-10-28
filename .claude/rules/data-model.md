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
`null` = neutral, negative epoch millis = disliked, any other value (including `0`) = positive, the
value being when it happened. A song's dislike is its time made negative (the Disliked page shows
it), albums and artists still write `-1`; old `-1` song rows stay dislikes. Check the sign (`< 0`,
`>= 0`), never `== -1`, and never treat `!= null` as "liked". Anything mapping
them to a rating or UI state needs three states (`MediaItemDsl.kt` maps to `ThumbRating`, not
`HeartRating`).

Disliked songs are hidden everywhere, always (no setting; Account's Disliked page takes them back).
`UserSongRepositoryImpl.query` leaves them out of every library list, `find` still gives them. Lists
fetched live drop the ids of `UserSongRepository.dislikedIds` where
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
  like/dislike (the same store, `SongStore`: playlist links and reactions need its `song` row),
  when it starts downloading (`DownloadedSongStore`, the maintainer's call: offline needs it) or
  when synced as liked. A planned Import button will store a remote item only on an explicit user
  action.
- Artists stored from the songs crediting them have no picture: credits carry only an id and a name.
  `ArtistPictures.fillMissing` asks the artist's source (`Provider.artist`) once per run and stores
  the picture, never over one already set; device artists have no source. Only Statistics calls it
  so far, other screens show the placeholder until a row is filled.
- Local album art is meant to be stored as the MediaStore album art id, with the `content://` URI
  built at render time (the stored URI form differs by Android version). Not done yet: don't build
  on the stored URI as if it were final.

## Downloads are per user, files are shared

`user_song.downloaded_at` marks a user's download; the file (one per song, Media3's download id =
the song's `kreate://` address) is shared and kept while any user marks it. A user downloading a
song another has gets it at once, or a better copy (best quality any user asked for, never
lowered). Leaving a download (menu, notification Cancel) is the active user's only;
pause stays device-wide. A row keeping only the download (and its download cover) is deleted when
it's left. All by the maintainer's choice (2026-10-26).
