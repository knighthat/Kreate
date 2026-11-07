---
paths:
  - "provider/**"
  - "app/src/main/kotlin/**/source/**"
  - "app/src/main/kotlin/**/ui/screen/detail/**"
  - "app/src/main/kotlin/**/ui/screen/settings/LibrarySyncSettings.kt"
---

# Sync and reactions

## Pull from the server

- External sources sync liked songs, albums, artists and playlists separately. Sync only changes
  items the database already has (songs in the user's library, stored albums and artists), the
  server wins; it never adds one, by the maintainer's spec (2026-11-06, replacing the earlier
  "liked songs are inserted"). Lists and detail screens of albums, artists and playlists show what
  the server returns, live. Albums and artists: bookmark/follow below.
- `liked_at` of a song already in the library: the server's like time replaces the stored one;
  with none given, an already liked song keeps its time and a newly liked one gets now − index ms
  (keeps server order; the index counts only the stored songs). A liked song of that origin the
  server doesn't list as liked gets `liked_at = NULL`. Rows are never deleted by sync, only
  `liked_at` changes. Dislikes (`-1`) can't be listed by YouTube or Subsonic, so sync never
  touches an unlisted one; a listed one becomes a like. A song the server lists that the library
  lacks is ignored (and its catalog rows aren't stored).
- Signing out or turning off a source or its sync only stops syncing, it never clears synced likes.
- Each source has four per-profile settings: liked-songs sync plus show/hide of the live albums,
  artists and playlists. OpenSubsonic albums/artists are `SyncMode` (`OFF`/`LIKED`/`ALL`).
- Rejected, don't propose again: an OpenSubsonic "all songs" mode (fetching the whole library bloats
  the
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

- OpenSubsonic: star (`albumId`/`artistId`); stored albums/artists follow the stars like YouTube's
  below (`OpenSubsonicLibrary.syncAlbums`/`syncArtists`, while the setting isn't `OFF`).
- YouTube: library save (`like/like` of the album's playlist) and subscription (the page's
  `channelId`, not the artist id). Signed in, YouTube's own state shows and local ones are ignored.
  Signed out the buttons still work, on the device only (`DeviceReactions`: the album or artist is
  stored then, like `SongStore` does for songs). Taking the bookmark/follow back deletes the
  `user_album`/`user_artist` row unless it holds a name, cover or artists of the user's own; the
  `album`/`artist` row stays. Device albums and artists keep their rows (the scan put them in the
  library). All by the maintainer's choice (2026-10-22).
- YouTube (and, by the same rules, OpenSubsonic) albums and artists already stored (bookmarked or
  followed signed out, or opened from the library) take their bookmark/follow from the account on
  each sync (`YouTubeLibrary.syncAlbums` /
  `syncArtists`, while the albums/artists sync setting is on; `UserAlbumRepository.syncBookmarks`,
  `UserArtistRepository.syncFollows`): listed → bookmarked/followed (an existing time is kept),
  not listed → removed, and the row dropped if empty; a dislike of a listed one becomes a
  bookmark/follow (the account wins), one it doesn't list stays; items the database doesn't have
  are not added; a library longer than the pages fetched only adds. Only device items open the
  stored (`Local*`) pages; the live YouTube and OpenSubsonic pages send the change to the account
  first
  and, once it took it, apply `syncBookmark`/`syncFollow` to the item's row if one exists (never
  adding one). All by the maintainer's spec (2026-11-06).
- YouTube playlists: the menu's "Add to library" (`YouTubePlaylistDetailViewModel.bookmark`, the
  same `like/like` as albums, `YouTubeProvider.savePlaylist`) shows the account's own state live;
  nothing is stored, so there's no signed-out mode and no sync. Hidden while signed out, for the
  user's own playlists and when the page has no library button (`YouTubePlaylistPage.saved` is
  `null`). Chosen by the maintainer (2026-11-06) over a stored `bookmarked_at` for playlists.
- OpenSubsonic playlists get no like: its API can't keep one (maintainer, 2026-10-22).
