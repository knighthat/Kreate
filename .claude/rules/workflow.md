# Working on this project

- **Ask once.** Where these rules or docs say *ask once*, ask the maintainer the first time it
  comes up, save the answer in your local memory on their machine (not in the repo), and follow
  it from then on without asking again.
- **A requested setting means the setting only.** Asked for a preference, add the `Preferences`
  entry (with a doc saying what will read it) and its settings row, then stop: don't design or ask
  about the feature behind it. When the request itself says what the setting does and the behavior
  is small (e.g. "Icon only" hiding labels), ask once whether to build that behavior too.
- **Where things live:** `.claude/file-index.md` maps every source file to what it does; check
  it first. Ask once whether to update it yourself when files are added, moved or removed.
- **Shared templates and references are suggestions.** Take their useful ideas and follow the
  project's own design (e.g. `MutableStateFlow` with private `_state`, DataStore hidden behind an
  interface) instead of porting the reference wholesale.
- **The maintainer edits files while you work.** A change you didn't make that breaks the build or
  looks wrong may be intentional or in progress: point at it with the diff, don't revert or "fix"
  it, and don't claim it as your work. Check `git status`/`git log` before assuming the tree is as
  you left it.
- **Batch, then build.** For work spanning many files, write all of it first, then run one
  `./gradlew … --continue` so every failure shows at once, fix, repeat. Don't build after each file.
- **Commits:** the maintainer commits. "Make a commit message" means print it. Subject short and
  lowercase like `git log`; body has one `- ` line per change, never hard-wrapped. Point out files
  that probably shouldn't be staged (e.g. `.idea/*` touched by Studio) and new files to add.
- **CLAUDE.md stays lean:** only durable, approved rules; no descriptions of what exists. Details
  go in `.claude/rules/` (by path) or `.claude/docs/`.
- **Agent files live under `.claude/`:** rules in `rules/`, details in `docs/`, plus
  `file-index.md`; never a top-level `docs/`, and never `.claude/agents/`, which Claude Code reads
  as subagent definitions. What's meant to stay local goes in your memory under `~/.claude`, never
  in git-ignored files of the repo.
- Don't stop Gradle with broad `pkill -f` patterns (e.g. `"Gradle Test Executor"`): they match the
  agent's own shell. Use `./gradlew --stop` or kill specific PIDs from `pgrep -af`.
- A UI stutter is traced before it's fixed (main thread Running vs runnable): see
  `.claude/docs/emulator.md`. Debug-build composition of a new screen (50–100 ms) is not a bug.
