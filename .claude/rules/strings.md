---
paths:
  - "app/src/main/res/values*/**"
  - "app/src/main/kotlin/**/ui/component/EnumLabels.kt"
---

# String resources

What stays a literal: `ui.md`.

Only English (`values/`) is in the repo. Translations come from Crowdin (`crowdin.yml`) and are
merged into `values-*/` before each release; never add or edit a translation folder here.

## Files

English texts sit in `values/*.strings.xml`, one file per kind of text, picked by the key prefix
(`plurals.xml` stays one file). Add a string to its file; Crowdin sees a moved string as new, so
never move keys between files.

| File                        | Prefixes                                             |
|-----------------------------|------------------------------------------------------|
| `common.strings.xml`        | `app_name`, `source_*`, units, other single words    |
| `statuses.strings.xml`      | `status_*`                                           |
| `settings.strings.xml`      | `setting_*`                                          |
| `actions.strings.xml`       | `action_*`, `menu_*`                                 |
| `screens.strings.xml`       | `screen_*`, `details_*`, `tab_*`, `section_header_*` |
| `semantics.strings.xml`     | `semantic_*`                                         |
| `wizard.strings.xml`        | `wizard_*`                                           |
| `dialogs.strings.xml`       | `dialog_*`                                           |
| `messages.strings.xml`      | `error_*`, `snackbar_*`                              |
| `statistics.strings.xml`    | `statistics_*`                                       |
| `notifications.strings.xml` | `notification_*`                                     |
| `constants.strings.xml`     | `constant_*`                                         |

## Format

- Every placeholder in `*.strings.xml` and `plurals.xml` is positional, even when there is only one:
  `%1$s`, `%1$d`, never `%s` or `%d`. Translations reorder them.
- Never plug a noun, a verb or an action from another string into a sentence ("Go to
  %1$s", "Couldn't
  %1$s"): translations decline and conjugate it. One full string per noun or action
  (`menu_go_to_album`, `error_like_song`); only names, counts and reasons are placeholders.
- Never say "this song" or "the album" alone: name one item with its quoted title (`quoted`),
  several by
  count (`songsLabel`). `snackbar_this_*` ("this song") is only for when the name isn't known yet
  (`itemName`).
- Never write a source's name inside another string: `source_local`, `source_ytm`,
  `source_opensubsonic` (and `source_lrclib` …) carry them; pass those as the arguments
  (`stringResource(R.string.x, stringResource(R.string.source_ytm))`).
- Texts in `plurals.xml` are lowercase; a heading or label capitalizes them in code
  (`replaceFirstChar`).
- Settings rows (`SettingsComponents.kt`) cut the title at one line and the summary at two, with
  "…"; a note after `\n` gets two lines of its own. English budgets, measured with Inter on a 360dp
  phone: `setting_title_*` ≤ 35 characters, `setting_description_*` ≤ 75, ≤ 65 under a
  `MainSwitch`. Placeholders count at their usual value (`YouTube Music`, a server name).

## Key names

Prefixes never share a key, even for the same words (an action and a title of the same English
are two keys). An action can be several words (`action_sign_out`).

| Text                                                                                         | Key                                                                                                                                                                                                                                                      |
|----------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Button text                                                                                  | `action_*` (`action_save`, `action_search`)                                                                                                                                                                                                              |
| Single word for a status, not tied to one setting                                            | `status_*` (`status_off`)                                                                                                                                                                                                                                |
| Other single word, unit symbol                                                               | named after the word (`mib`); `quoted` = `\"%1$s\"`, `percent` = `%1$d%%`                                                                                                                                                                                |
| Screen title (top bar, row that opens it, a wizard page)                                     | `screen_title_*`; YouTube and OpenSubsonic screens use `source_ytm` / `source_opensubsonic`                                                                                                                                                              |
| Tab label (library bottom bar, player)                                                       | `tab_*`                                                                                                                                                                                                                                                  |
| Setting row                                                                                  | `setting_title_*`, `setting_description_*`                                                                                                                                                                                                               |
| Header naming a group of settings (`SettingsSection`)                                        | `setting_header_*`; a header and a row with the same text share one key                                                                                                                                                                                  |
| Status of one setting                                                                        | `setting_description_{setting}_status_*`, name from its `setting_title_*` (`setting_description_server_status_checking`)                                                                                                                                 |
| Heading of a group in a list or sheet                                                        | `section_header_*`                                                                                                                                                                                                                                       |
| Label of a statistic (tile caption, a value's name)                                          | `statistics_*`; one song's `statistics_song_*`, album's `statistics_album_*`, artist's `statistics_artist_*`                                                                                                                                             |
| Icon description (`contentDescription`, an icon button's `description`), entry of a ⋮ button | `semantic_*` (`semantic_search`)                                                                                                                                                                                                                         |
| Other menu item / heading of a menu page                                                     | `menu_*` / `menu_title_*`; going to a noun is `menu_go_to_{noun}`, one string each                                                                                                                                                                       |
| Dialog                                                                                       | `dialog_title_{dialog}`, `dialog_body_{dialog}` (several: `_1`, `_2`); buttons `action_*`                                                                                                                                                                |
| Error the user sees                                                                          | `error_*`                                                                                                                                                                                                                                                |
| Snackbar message                                                                             | `snackbar_*` (its error part `error_*`)                                                                                                                                                                                                                  |
| Empty state / hint of one screen                                                             | `screen_{screen}_status_*` / `screen_{screen}_hint_*`; shared by several screens: `status_*`                                                                                                                                                             |
| Notification text                                                                            | `notification_{channel}_title_*` / `_subtitle_*`; channel name and description `notification_{channel}_title_channel` / `_subtitle_channel`; a status also shown in the app uses its `status_*` key                                                      |
| Setup-wizard-only text                                                                       | `wizard_*`; words shared with the app (Skip, Next, Theme, …) use their usual keys                                                                                                                                                                        |
| Enum value                                                                                   | `constant_{enum class}_{value}` (`constant_sync_mode_liked`), via `labelRes` in `EnumLabels.kt`, `labelsOf` for choices; per-source meaning puts the source in the name (`constant_stream_quality_ytm_low`); only exception `sleep_timer_to_end_of_song` |

- An action that failed on one item is `error_{action}` taking its quoted name (`error_like_song`),
  `error_{action}_unnamed` when the name isn't known yet (`error_cant_share_album_unnamed`). A
  source
  that didn't take a change is `error_sync_failed` with a reason from `error_reason_*`.
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
