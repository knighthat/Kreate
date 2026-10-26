# File index

Where each source file lives and what it does, in parts under `.claude/file-index/`. Grep the
folder for a file or class name (`grep -rn 'SongCaches' .claude/file-index/`), or read only the
part for the folder you work in. Module shape and rules: `.claude/rules/modules.md`.

| Part              | Covers                                                                                                                                      |
|-------------------|---------------------------------------------------------------------------------------------------------------------------------------------|
| `app.md`          | `:app` root (application, activities, profiles, Koin modules), `notification/`, `auto/` (Android Auto), `preferences/`, `utils/`, resources |
| `app-playback.md` | `:app` `playback/`: service, controller, data sources, caches, loudness, sleep timer                                                        |
| `app-source.md`   | `:app` `source/`: app-side glue for local, OpenSubsonic and YouTube sources, covers, reactions                                              |
| `app-ui.md`       | `:app` `ui/component/`, `ui/navigation/`, `ui/theme/`                                                                                       |
| `app-screens.md`  | `:app` `ui/screen/`: every screen and its view model                                                                                        |
| `model.md`        | `:model`: shared enums and small types                                                                                                      |
| `database.md`     | `:database`: Room database, DAOs, repositories, paging, schema                                                                              |
| `preferences.md`  | `:preferences`: settings, secrets, profiles, backups of preferences                                                                         |
| `provider.md`     | `:provider`: OpenSubsonic and YouTube providers, plus the proxy files of every module                                                       |
| `lyrics.md`       | `:lyrics`: lyrics sources, finder, parser                                                                                                   |
| `tests.md`        | unit tests of every module                                                                                                                  |

A new file goes in the part for its folder; a new folder of `:app` big enough to need its own part
gets one, added to this table.
