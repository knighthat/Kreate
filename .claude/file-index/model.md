# File index: `:model`

Paths are relative to `model/src/main/kotlin/me/knighthat/kreate3/`; checked against the tree on
2026-10-20, verify a path still exists before relying on it. Map of the parts:
`.claude/file-index.md`.

Pure Kotlin/JVM (`kotlin("jvm")`), package `me.knighthat.kreate3.model`, rules in its
`build.gradle.kts` header (see `.claude/rules/modules.md`).

- `Origin.kt` (moved from database/model 2026-10-19), `LyricsSync.kt` (NONE/LINE/WORD),
  `LyricsProvider.kt` (BETTER_LYRICS, LYRICS_PLUS, LRCLIB, YOUTUBE — declaration order = ask order).
  All stored by name: never rename.
