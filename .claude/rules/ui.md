---
paths:
  - "app/src/main/kotlin/**/ui/**"
  - "app/src/main/res/**"
---

# UI conventions

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

## Strings

- Every placeholder in `strings.xml` and `plurals.xml` is positional, even when there is only one:
  `%1$s`, `%1$d`, never `%s` or `%d`. Translations reorder them.
- Never write a source's name inside another string: `source_ytm` and `source_opensubsonicapi`
  carry them, pass those as the arguments (
  `stringResource(R.string.x, stringResource(R.string.source_ytm))`).
- Settings texts are `setting_title_*`, `setting_description_*` and, for the header that names a
  group of rows (`SettingsSection`), `setting_header_*`. A header and a row with the same text share
  one key, whichever prefix it has. A plural is `second` for `%1$d second` / `%1$d seconds`, or
  `unit_second` for `second` / `seconds`.
- A status that belongs to one setting is `setting_description_{setting name}_status_*`, with the
  setting's name from its `setting_title_*` key (`setting_description_server_status_online`).
- A single word that isn't tied to one setting is a string named after the word, with `action_` for
  the text of a button and `status_` for a status: `action_save`, `status_off`. A unit symbol is one
  too
  (`mib`). `quoted` is `\"%1$s\"` and `percent` is `%1$d%%`.
- An action and a title are separate strings, even when the English is the same: the `Search` button
  is `action_search`, the title `Search` of a screen is its own key, never `action_search`.
- The description of an icon for screen readers (`contentDescription`, the `description` of an icon
  button) is `semantic_*`, separate from the action and the title of the same words:
  `semantic_search`.
- Strings only a preview shows (`KreateThemePreview.kt`) are not extracted.
- An action can be several words (`action_sign_out`). The Context a view model needs for its strings
  is injected (`private val context: Context`, Koin provides it), never read from a global.
- The title of a screen (its top bar, the label of its tab, the row that opens it, a page of the
  setup
  wizard) is `screen_title_*`, so the titles
  of every screen group together: `screen_title_network`. The `YouTube` and `OpenSubsonic` screens
  use `source_ytm` and `source_opensubsonicapi`.
- Not extracted yet, they stay literals: the labels of a setting's values. Protocol names (`HTTP`,
  `SOCKS5`) and examples are never translated and stay literals for good.

## Covers

- Cover fallback lives only in `Thumbnail` (`ItemParts.kt`: `image_not_available` while there's no
  cover, while loading and on error). Never set `error` or
  `fallback` on the app-wide Coil `ImageLoader`: `rememberThumbnailSeed` would seed the theme from
  the placeholder.
- Coil images are usually hardware bitmaps; `Image.toBitmap(w, h, config)` throws on them. To read
  pixels, request with `allowHardware(false)` or copy to `ARGB_8888`. See
  `.claude/docs/cover-cache.md`.
- Song `MediaItem`s always carry `artworkUri`, even in lists whose rows hide covers
  (`showThumbnail = false`): the same items go into the queue, the notification and the saved
  queue, which show only that address.
- Queued songs carry their cover as an `ArtworkProvider` address (`withServedArtwork`), because
  Android Auto's player reads only `content://` covers. App code reading a song's `artworkUri`
  goes through `thumbnailModel`/`songThumbnailModels`, or `coverSource()`, to load the original.

## Menus and hints

- List-item menus open by long-press (CLAUDE.md). The pressed row lifts out of the list and hovers
  above the bottom sheet as a card, and the sheet's first button row is its header with no divider
  (`SongMenu.kt`). Menus for albums, artists and playlists reuse this pattern. Ask before changing
  the gesture.
- Selection menus put the check mark **before** the label; unchecked rows keep an empty 24dp
  `leadingIcon` so labels line up.
- Hint text that names a button shows its icon inline (`appendInlineContent` +
  `InlineTextContent(Placeholder(1.2.em, 1.2.em, TextCenter))`, see `LyricsPage.kt` `Message`).

## Full player

- Each page has a button bar (`PageButtons` in `PlayerSheet.kt`). Leaving a page resets its
  buttons: a new button's state goes in the reset in `FullPlayer`. Persisted settings (lyrics text
  size) aren't buttons and stay.
- A button that acts on what's shown is disabled while nothing is shown. `ModeButton(enabled = …)`
  dims its own icon (an explicit tint isn't dimmed by `IconButton`).
- Text over bright covers is a known open problem. An adaptive scrim and a text shadow were both
  tried and rejected: don't re-propose them; ask what was disliked before trying something else.

## Drawables

Besides CLAUDE.md's color and size rule: start with the GPL XML comment header (copy `check.xml`);
for an illustration, the outline is `#000` and the inside keeps its own neutral colors. For Material
icons, take the exact `pathData` from
`https://raw.githubusercontent.com/google/material-design-icons/master/android/<category>/<name>/materialicons/black/res/drawable/baseline_<name>_24.xml`
instead of writing paths from memory.
