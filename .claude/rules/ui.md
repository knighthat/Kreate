---
paths:
  - "app/src/main/kotlin/**/ui/**"
---

# UI conventions

Strings: `strings.md`; previews: `previews.md`; covers: `covers.md`; full player: `player.md`;
charts: `.claude/docs/charts.md`.

## Components

- Buttons used on more than one screen (search, profile, settings, back, …) live in
  `ui/component/Buttons.kt` as `XButton(onClick, modifier = Modifier)` wrapping the private
  `IconAction`. Don't inline an `IconButton` in a scaffold; a one-off button can stay in its screen.
- `ItemRow` (`ItemParts.kt`) already pads rows (16dp × 6dp): never add padding to `SongItem`,
  `PlaylistItem` or other row items, or horizontal `contentPadding` to a list of them. Grid cards
  (`AlbumItem`, `ArtistItem`) have no padding of their own.
- Helpers shared across files are `internal` top-level extensions (
  `internal fun Context.restart()`),
  not companion-object functions; companions are for the class's own constants and factories.
- Origin badges and the home origin chips both use `Origin.icon`; keep them in step. The YouTube
  Music and OpenSubsonic logos are the maintainer's choice and the trademark risk was discussed:
  don't raise it again or swap in a generic icon unless asked.
- Screens with the mini player never pad around it from outside, or swiping it away leaves an
  empty band: they pad only for the rest of the bottom bar (`MiniPlayerSpace.outside`) and end
  their lists with room for it as content padding (`MiniPlayerSpace.inside`). `PlaybackMiniPlayer`
  measures into the `MiniPlayerSpace` it's given.
- A composable reads strings from `LocalResources.current` (`resources.getString`, or a helper
  taking `Resources`), never through `LocalContext.current`: `LocalResources` changes with the
  configuration (locale) and recomposes, the context doesn't. Keep `LocalContext` only for what
  needs a `Context` (starting activities, image loaders). View models keep their `Context` and
  pass `context.resources` to such helpers.
- Text stays a literal (not in `strings.xml`) only for: the labels of a setting's values (not
  extracted yet), protocol names (`HTTP`, `SOCKS5`) and examples (never translated), and
  preview-only text.

## Menus and hints

- Menus of list items open by long-press; rows have no trailing ⋮ (more) button. The pressed row
  lifts out of the list and hovers above the bottom sheet as a card, and the sheet's first button
  row is its header with no divider (`SongMenu.kt`). Menus for albums, artists and playlists reuse
  this pattern. Ask before changing the gesture.
- A new `SongItem` option that changes how the row looks goes into `SongMenuRequest` too, or the row
  lifted above the menu draws without it.
- Selection menus put the check mark **before** the label; unchecked rows keep an empty 24dp
  `leadingIcon` so labels line up.
- Hint text that names a button shows its icon inline (`appendInlineContent` +
  `InlineTextContent(Placeholder(1.2.em, 1.2.em, TextCenter))`, see `LyricsPage.kt` `Message`).
