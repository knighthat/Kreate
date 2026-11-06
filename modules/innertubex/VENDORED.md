# Vendored: InnerTubeX

- Upstream: https://github.com/MetrolistGroup/innertubex
- Version: `v0.9.2` (commit c19d4860b0bc8afa0b1b3cc717abeb8119bbb605)
- License: GPL-3.0 (see `LICENSE`)

## Local changes
- `build.gradle.kts` rewritten to use Kreate's version catalog, AGP 8.x (`androidLibrary {}`) and compileSdk from the catalog.
- Only Android and JVM (`desktop`) targets are built; iOS/native sources, the harness, publishing, dokka, ktlint and API-validator setup were removed.
- `src/` is otherwise unmodified.

## Updating
Export the new tag (`git archive <tag> src`), replace `src/`, drop `nativeMain`/`nativeTest`, update the version above.
