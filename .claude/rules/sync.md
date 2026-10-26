---
paths:
  - "provider/**"
  - "app/src/main/kotlin/**/source/**"
  - "app/src/main/kotlin/**/ui/screen/detail/**"
  - "app/src/main/kotlin/**/ui/screen/settings/LibrarySyncSettings.kt"
---

# Sync and reactions

## Pull from the server

- External sources sync liked songs, albums, artists and playlists separately. Of the synced data,
  only liked songs are written to the database, and the server wins. Albums, artists and playlists
  are never stored by sync: lists and detail screens show only what the server returns, live.
- `liked_at`: the server's like time replaces the stored one; with none given, an already liked
  song keeps its time and a newly liked/inserted one gets now − index ms (keeps server order). A
  liked song of that origin the server doesn't list as liked gets `liked_at = NULL`. Rows are never
  deleted by sync, only `liked_at` changes. Dislikes (`-1`) can't be listed by YouTube or Subsonic,
  so sync never touches them.
- Signing out or turning off a source or its sync only stops syncing, it never clears synced likes.
- Each source has four per-profile settings: liked-songs sync plus show/hide of the live albums,
  artists and playlists. OpenSubsonic albums/artists are `SyncMode` (`OFF`/`LIKED`/`ALL`).
- Rejected, don't propose again: a sync that never inserts and shows liked songs live (likes need
  their timestamp), and an OpenSubsonic "all songs" mode (fetching the whole library bloats the
  app).

## Song reactions sent to the server

- OpenSubsonic (`OpenSubsonicLibrary.react`, while its liked-songs sync is on): becoming liked
  stars the song, stopping unstars it. The API has no dislike, so a dislike stays on the device by
  the maintainer's choice; disliking a starred song only unstars it (else the next pull turns it
  back into a like). Never map a dislike to a rating.
- YouTube (`YouTubeLibrary.react`, while signed in and its liked-songs sync is on): each change
  sends the new state, `like/like`, `like/dislike` or `like/removelike`. Signed out they stay on the
  device. The pull ("Liked Music") overrides neutral and liked; YouTube can't list dislikes, so they
  stay, but a local dislike of a song YouTube still lists as liked (made while signed out) becomes
  liked again: the sync wins, by the maintainer's choice (2026-10-22).
- Both share `ReactionSender`: save → send → on failure take the local state back to what it was
  (unless a newer reaction replaced it meanwhile) and show an error saying why, never retried. A
  pull that overlapped a reaction being saved or sent is dropped, not stored. Applies to every
  source that sends changes to its server.

## Album bookmark and artist follow

Detail screens' top bar. Device items write `bookmarked_at`/`follow_since`. Remote ones are never
stored: the button shows and changes the source's own state live (`SourceToggle`), whatever the
sync settings:

- OpenSubsonic: star (`albumId`/`artistId`).
- YouTube: library save (`like/like` of the album's playlist) and subscription (the page's
  `channelId`, not the artist id). Signed in, YouTube's own state shows and local ones are ignored.
  Signed out the buttons still work, on the device only (`DeviceReactions`: the album or artist is
  stored then, like `SongStore` does for songs). Taking the bookmark/follow back deletes the
  `user_album`/`user_artist` row unless it holds a name, cover or artists of the user's own; the
  `album`/`artist` row stays. Device albums and artists keep their rows (the scan put them in the
  library). All by the maintainer's choice (2026-10-22).
- Server playlists get no like: neither API can keep one (maintainer, 2026-10-22).
