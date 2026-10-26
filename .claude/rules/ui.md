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
- Every file of `ui/component` that draws something ends with its own previews, in a
  `//<editor-fold defaultstate="collapsed" desc="Previews">` … `//</editor-fold>` block
  (`SeekBar.kt`): private composables with `@ComponentPreviews`, wrapped in `PreviewTheme` and a
  `Surface` unless the component has its own background, built from the samples of
  `PreviewSamples.kt`. Preview-only helpers and fakes stay private inside the block. A new
  component, or a new state of one (a flag, a mode), gets its preview in the same change. A part
  that needs Koin, the navigator or the menu host is previewed through its pieces that don't
  (`SongMenu.kt`). What only an effect shows has no preview, a static preview never runs it
  (`MessageHost`). No shared preview files: previews live beside what they show.
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
- The name of an entry of a more (three dot) button is `semantic_*`: the entry is an icon with a
  name, so the name describes the icon. The text of any other menu item is `menu_*`, the heading of
  a page of a menu is `menu_title_*`. A plural that takes no number in its text is still a plural
  (`menu_go_to_artist`).
- A text only the setup wizard shows is `wizard_*`. The words it shares with the rest of the app
  (Skip, Next, Theme, Name, …) use their usual keys.
- Prefixes never share a key, even for the same words: the label of a tab (the library's bottom
  bar, the player) is `tab_*`, the title of the screen it opens is `screen_title_*`; the title of a
  setting is `setting_title_*`, the title of a dialog `dialog_title_*`.
- A noun that is shown in the singular or the plural, and is not a `tab_*` nor a `screen_title_*`,
  is a plural named `unit_{noun}` (`unit_album`: album, albums), never cut from another string.
  Texts in `plurals.xml` are lowercase, a heading or label capitalizes them in code
  (`replaceFirstChar`). A menu entry that goes to a noun is `menu_go_to` with the plural as its
  argument.
- A dialog has `dialog_title_{dialog}` and `dialog_body_{dialog}`; when it has several bodies they
  are numbered, `dialog_body_{dialog}_1`. The buttons use `action_*`.
- An error the user sees is `error_*`. When a message is built from a text and an error (`Couldn't
  play "Song": its file is gone`), only the error is a string; the rest stays a literal for now.
  Code without a `Context` holds the error as a `@StringRes` and its caller resolves it.
- A message shown in a snackbar is `snackbar_*`, an error in it `error_*` (see above). Code that
  builds one without a `Context` takes it as a parameter (`PlaybackController.queue(context, …)`).
- The heading of a group in a list or a sheet is `section_header_*` (the headers of the settings are
  `setting_header_*`).
- An empty state or a hint of one screen is `screen_{screen}_status_*` or `screen_{screen}_hint_*`;
  one shared by several screens is `status_*`.
- A text only a notification shows is `notification_{channel}_title_*` or
  `notification_{channel}_subtitle_*`; the name and the description of a channel are
  `notification_{channel}_title_channel` and `_subtitle_channel`.
- The names of the sources are `source_local`, `source_ytm` and `source_opensubsonicapi`; a
  playlist's
  origin label uses them (`Origin.sourceRes`).
- A view model takes `context: Context` as a constructor parameter (`viewModelOf` resolves it, a
  test
  passes `RuntimeEnvironment.getApplication()`). Only code with no constructor to put it in, like a
  data builder or a lyrics source, gets one from Koin: `GlobalContext.get().get<Context>()`. Never
  call it while composing; a test that reaches it has to start Koin with the Robolectric
  application.
- The text of a value of an enum is `constant_{enum class}_{value}` (`constant_sync_mode_liked`),
  looked up by a `labelRes` extension in `EnumLabels.kt`; `labelsOf` hands a choice its labels. A
  value that means something different per source has the source in the name
  (`constant_stream_quality_ytm_low`). The one exception is `sleep_timer_to_end_of_song`.
- An origin is named by its source: `Origin.sourceRes` gives `source_local`, `source_ytm` or
  `source_opensubsonicapi`, there is no other label for it.
  A lyrics provider is named the same way, `LyricsProvider.sourceRes` (`source_lrclib`), never from
  the name of its enum value.
- Strings only a preview shows (`KreateThemePreview.kt`) are not extracted.
- An action can be several words (`action_sign_out`).
- The title of a screen (its top bar, the row that opens it, a page of the setup wizard) is
  `screen_title_*`, so the titles of every screen group together:
  `screen_title_network`. The `YouTube` and `OpenSubsonic` screens use `source_ytm` and
  `source_opensubsonicapi`.
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
