---
paths:
  - "app/src/main/res/values*/**"
  - "app/src/main/kotlin/**/ui/component/EnumLabels.kt"
---

# String resources

What stays a literal: `ui.md`.

Only English (`values/`) is in the repo. Translations come from Crowdin (`crowdin.yml`) and are
merged into `values-*/` before a build; never add or edit a translation folder here.

## Format

- Every placeholder in `strings.xml` and `plurals.xml` is positional, even when there is only one:
  `%1$s`, `%1$d`, never `%s` or `%d`. Translations reorder them.
- Never write a source's name inside another string: `source_local`, `source_ytm`,
  `source_opensubsonicapi` (and `source_lrclib` …) carry them; pass those as the arguments
  (`stringResource(R.string.x, stringResource(R.string.source_ytm))`).
- Texts in `plurals.xml` are lowercase; a heading or label capitalizes them in code
  (`replaceFirstChar`).

## Key names

Prefixes never share a key, even for the same words (an action and a title of the same English
are two keys). An action can be several words (`action_sign_out`).

| Text                                                                                         | Key                                                                                                                                                                                                                                                      |
|----------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Button text                                                                                  | `action_*` (`action_save`, `action_search`)                                                                                                                                                                                                              |
| Single word for a status, not tied to one setting                                            | `status_*` (`status_off`)                                                                                                                                                                                                                                |
| Other single word, unit symbol                                                               | named after the word (`mib`); `quoted` = `\"%1$s\"`, `percent` = `%1$d%%`                                                                                                                                                                                |
| Screen title (top bar, row that opens it, a wizard page)                                     | `screen_title_*`; YouTube and OpenSubsonic screens use `source_ytm` / `source_opensubsonicapi`                                                                                                                                                           |
| Tab label (library bottom bar, player)                                                       | `tab_*`                                                                                                                                                                                                                                                  |
| Setting row                                                                                  | `setting_title_*`, `setting_description_*`                                                                                                                                                                                                               |
| Header naming a group of settings (`SettingsSection`)                                        | `setting_header_*`; a header and a row with the same text share one key                                                                                                                                                                                  |
| Status of one setting                                                                        | `setting_description_{setting}_status_*`, name from its `setting_title_*` (`setting_description_server_status_online`)                                                                                                                                   |
| Heading of a group in a list or sheet                                                        | `section_header_*`                                                                                                                                                                                                                                       |
| Label of a statistic (tile caption, a value's name)                                          | `statistics_*`; one song's `statistics_song_*`, album's `statistics_album_*`, artist's `statistics_artist_*`                                                                                                                                             |
| Icon description (`contentDescription`, an icon button's `description`), entry of a ⋮ button | `semantic_*` (`semantic_search`)                                                                                                                                                                                                                         |
| Other menu item / heading of a menu page                                                     | `menu_*` / `menu_title_*`; going to a noun is `menu_go_to` with the plural as argument                                                                                                                                                                   |
| Dialog                                                                                       | `dialog_title_{dialog}`, `dialog_body_{dialog}` (several: `_1`, `_2`); buttons `action_*`                                                                                                                                                                |
| Error the user sees                                                                          | `error_*`                                                                                                                                                                                                                                                |
| Snackbar message                                                                             | `snackbar_*` (its error part `error_*`)                                                                                                                                                                                                                  |
| Empty state / hint of one screen                                                             | `screen_{screen}_status_*` / `screen_{screen}_hint_*`; shared by several screens: `status_*`                                                                                                                                                             |
| Notification text                                                                            | `notification_{channel}_title_*` / `_subtitle_*`; channel name and description `notification_{channel}_title_channel` / `_subtitle_channel`                                                                                                              |
| Setup-wizard-only text                                                                       | `wizard_*`; words shared with the app (Skip, Next, Theme, …) use their usual keys                                                                                                                                                                        |
| Enum value                                                                                   | `constant_{enum class}_{value}` (`constant_sync_mode_liked`), via `labelRes` in `EnumLabels.kt`, `labelsOf` for choices; per-source meaning puts the source in the name (`constant_stream_quality_ytm_low`); only exception `sleep_timer_to_end_of_song` |

- Plurals: `second` is `%1$d second` / `%1$d seconds`; `unit_second` is `second` / `seconds`. A noun
  shown in singular or plural (not a `tab_*` or `screen_title_*`) is a plural `unit_{noun}`
  (`unit_album`), never cut from another string. A plural with no number in its text is still a
  plural (`menu_go_to_artist`).
- Time ago is `status_ago` (`%1$s ago`) with a counted plural (`day`, `week`, `month`, `year`) as
  its argument, never a plural per unit (`*_ago`). The maintainer's choice, though languages that
  inflect the noun after "ago" (German "vor 3 Tagen") may need per-unit plurals once translated.
- An origin or a lyrics provider is named only by its source: `Origin.sourceRes`,
  `LyricsProvider.sourceRes`, never from the enum value's name.

## In code

- A message built from a text and an error (`Couldn't play "Song": its file is gone`): only the
  error is a string, the rest stays a literal for now. Code without a `Context` holds the error as
  a `@StringRes` and its caller resolves it; code building a snackbar without one takes
  `Resources` as a parameter (`PlaybackController.queue(resources, …)`), see `ui.md` for where
  composables get it.
- A view model takes `context: Context` as a constructor parameter (`viewModelOf` resolves it, a
  test passes `RuntimeEnvironment.getApplication()`). Only code with no constructor to put it in
  (a data builder, a lyrics source) uses `GlobalContext.get().get<Context>()`. Never call it while
  composing; a test that reaches it has to start Koin with the Robolectric application.
