# YouTube playlists (InnerTube)

What was found by trying them on the maintainer's account (2026-11-05, `WEB_REMIX`, signed in).
Answers are nested and change often; check with a throwaway playlist before relying on a field.

- `playlist/create` (`InnerTube.createPlaylist`): body `title`, `description`, `privacyStatus`
  (`PUBLIC`, `UNLISTED`, `PRIVATE`), optional `videoIds`; answers `status` and `playlistId`.
- `playlist/get_add_to_playlist` (`addToPlaylistOptions`): body **`videoIds`** (an array, one
  `videoId` answers 400) and `excludeWatchLater`. The playlists are `playlistAddToOptionRenderer`s
  with `playlistId` and `title`, only the ones the user can edit, plus Liked Music (`LM`, its
  endpoint is a like). This client's answer has **no** `containsSelectedVideos`: it can't say
  which playlists already hold the song.
- `browse/edit_playlist` with `ACTION_ADD_VIDEO`: `DEDUPE_OPTION_SKIP` is ignored, the song was
  added again each time (a test playlist ended up with it four times). `DEDUPE_OPTION_CHECK`
  answers `STATUS_FAILED` for a song the playlist has, which `InnerTube.addToPlaylist` reads as
  "already in". A success carries `playlistEditResults` with a `setVideoId`.
- The library page doesn't tell which playlists are the user's; the playlist page does
  (`musicEditablePlaylistDetailHeaderRenderer`). `get_add_to_playlist` is the cheap way to list
  the ones that can be added to.
- A new playlist shows in the app's Playlists tab after the tab is opened again (the tab fetches
  on each visit); its page header may keep an old track count until reopened.
- Changing a playlist's name, description and privacy: `browse/edit_playlist` with
  `ACTION_SET_PLAYLIST_NAME` (`playlistName`), `ACTION_SET_PLAYLIST_DESCRIPTION`
  (`playlistDescription`) and `ACTION_SET_PLAYLIST_PRIVACY` (`playlistPrivacy`: `PUBLIC`,
  `UNLISTED`, `PRIVATE`), the names taken from ytmusicapi. **Not tried against YouTube Music yet**
  (`YouTubeProvider.updatePlaylist`). The owner's current privacy is read from the editing header
  at `editHeader.musicPlaylistEditHeaderRenderer.privacy`, also untried; the edit screen copes
  with it missing. No call sets a playlist's picture: YouTube Music builds it from the songs.
