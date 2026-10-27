# Rule index

Which rule or doc covers what. Rules (`.claude/rules/`) load on their own when a file under their
paths is read; read one by hand only for work no path triggers (builds, planning, a new area).
Docs (`.claude/docs/`) are read only when needed. `app/…` is `app/src/main/kotlin/**/`.

## Rules

| Rule              | Loads for                                                                                         | Covers                                                                                                                                                                       |
|-------------------|---------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `workflow.md`     | always                                                                                            | asking once, file index, reading in parts, helper scripts, local models, batching builds, commits, lean CLAUDE.md and rules, where agent files go, stopping Gradle, stutters |
| `modules.md`      | `build.gradle.kts`, `settings.gradle.kts`, `model/`                                               | module shape and dependencies, `:model` vocabulary, interfaces bridged in `:app`, enums stored by name, flavors, versionCode, APK paths                                      |
| `ui.md`           | `app/…ui/`                                                                                        | shared buttons, row padding, top-level helpers, origin icons, mini-player padding, literals that stay, long-press menus, selection menus, inline icons in hints              |
| `strings.md`      | `res/values*/`, `EnumLabels.kt`                                                                   | placeholders, string key prefixes, plurals, errors and snackbars in code, `Context` in view models                                                                           |
| `previews.md`     | `app/…ui/component/`, `app/…ui/screen/`                                                           | component and screen previews, Koin-free content, rendering previews                                                                                                         |
| `covers.md`       | `app/…ui/`, `app/…source/`, `app/…playback/`                                                      | `Thumbnail` fallback, hardware bitmaps, `artworkUri` on items, served artwork for Auto                                                                                       |
| `player.md`       | `app/…ui/screen/player/`                                                                          | page button bars and their reset, disabled buttons, text over covers (rejected ideas)                                                                                        |
| `drawables.md`    | `res/drawable*/`                                                                                  | icon color and size, header, Material icon paths                                                                                                                             |
| `playback.md`     | `app/…playback/`, `notification/`, `ui/screen/player/`, `res/layout/`                             | controller threads, foreground before `play()`, loudness, dislikes leaving the queue, sleep timer                                                                            |
| `data-model.md`   | `database/`, `provider/`, `app/…source/`, `app/…playback/`                                        | tri-state reactions, hidden dislikes, what gets stored, ids and URLs, local album art                                                                                        |
| `sync.md`         | `provider/`, `app/…source/`, `app/…ui/screen/detail/`, `LibrarySyncSettings.kt`                   | pulling likes, `liked_at`, sync settings, reactions sent to servers, bookmark/follow, rejected sync ideas                                                                    |
| `database.md`     | `database/`                                                                                       | schema changes and versions, `1.json` regeneration, bundled SQLite, paging tables                                                                                            |
| `sources.md`      | `provider/`, `app/…source/`, `app/…playback/`, `app/…settings/network/`, `app/…ui/screen/detail/` | source gates, Coil fetchers that throw, proxies, source settings pages                                                                                                       |
| `youtube.md`      | `**/youtube/`, `libs.versions.toml`                                                               | YouTube screens, signed-out errors, signed-out streams, innertubex pin, BotGuard keys, probing                                                                               |
| `opensubsonic.md` | `**/opensubsonic/`                                                                                | sign-in in POST bodies, self-signed certs, ReplayGain, `getTopSongs`                                                                                                         |
| `storage.md`      | `preferences/`, `app/…preferences/`, `res/xml/`                                                   | DataStore layout, what goes in `Preferences`, secrets, device-only files                                                                                                     |
| `backup.md`       | `app/…backup/`, `PreferencesBackup.kt`, `DatabaseBackup.kt`                                       | zip contents, staged restore and rollback, backup file name, DB version vs restore                                                                                           |
| `lyrics.md`       | `lyrics/`, `Lyrics*`/`SyncedLyrics*` in the player, `YouTubeLyricsSource.kt`                      | lyrics table, loading rules, open judgment calls, one source at a time                                                                                                       |
| `testing.md`      | `src/test/`, `src/androidTest/`                                                                   | test variants, forcing reruns, no credentials in tests, instrumented tests, worktree `local.properties`, test pitfalls                                                       |

## Docs

| Doc                    | Covers                                                                                                                                              |
|------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------|
| `emulator.md`          | device permission, launching, database, driving the UI, previews, instrumented tests, release builds, test audio, Perfetto jank traces, sign-ins    |
| `android-gotchas.md`   | background playback on Android 16, Android Auto, intents that ask the UI to act, playback resumption, RemoteViews notifications, Database Inspector |
| `playback-pipeline.md` | resolving songs, data source chain, caches, quality copies, formats and gain, Navidrome observations, open items                                    |
| `youtube-streams.md`   | innertubex setup, PO tokens, signed-out vs session, client behavior                                                                                 |
| `cover-cache.md`       | cover sizes, disk cache keys, hardware bitmaps                                                                                                      |
| `proxies.md`           | proxy fallback, library behavior, untested cases                                                                                                    |
| `lyrics-servers.md`    | how BetterLyrics, LyricsPlus and LRCLIB answered when probed                                                                                        |
