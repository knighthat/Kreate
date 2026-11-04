# Versioning and releases

One run of `.claude/scripts/bump.sh` is one release. The maintainer commits and tags.

## Which part to bump

The highest qualifying change wins; the parts below it reset to 0 (`3.0.4` + a feature = `3.1.0`).

| Change                                                                                                       | Bump  |
|--------------------------------------------------------------------------------------------------------------|-------|
| Bug fix, dependency bump with no user impact                                                                 | patch |
| Feature added, removed or changed (it does B now instead of A; a setting's default flipped)                  | minor |
| Dependency bump that changes something the app uses and users notice: count it as a changed feature          | minor |
| Support dropped (minSdk, a platform, an ABI)                                                                 | minor |
| Part of the app replaced by a new code base or service, and the old setup can't carry over (old DB unusable) | major |

- The test is what the user has to do, not how much code changed. Moving a screen to a new library
  with the same behavior is not a major (not even a bump on its own).
- A new service the user can switch to with little effort is a minor (changed feature).
- Once the app is in production, a schema change needs a DB version bump (see `rules/database.md`);
  the app version follows the user-visible change, and a schema change alone is not in the
  changelog.
- Translations ship with every release and are never in the changelog. Refactors and other changes
  that don't touch the front end aren't either.
- A release only for refactors or translations doesn't need cutting; if one rides along, patch.

## Changelog

`app/src/main/res/raw/changelogs.yml` holds the notes of the release being cut (the app shows it),
English only, never translated: lists `adds`, `removes`, `changes`, `fixes`. Describe the feature
change, not the library that changed. Entries are short phrases without a trailing period and
never start with Added/Removed/Changed/Fixed: the list name already says it. Write it before running
the script; the script doesn't clear
it.

`.claude/scripts/changelog.sh [<from-ref>] [--write] [--max N]` drafts it with the local Ollama
model, one commit at a time, from the last stable tag. It keeps only what users directly see or
tap, lists the most important first, caps adds/removes/changes together at N (default 20) and never
drops a fix. It prints the draft (`--write` replaces the file). A
small model misjudges and can't see that a later commit fixes or reworks an earlier one of the same
release: read the draft and merge such lines by hand.

The script checks it against the bump (any adds/removes/changes needs at least a minor; a major is
never inferred) and writes the shortened notes for the stores to
`fastlane/metadata/android/en-US/changelogs/<versionCode × 10>.txt` (500 bytes at most, labels
Added/Removed/Changed/Fixed). Stores get the universal APK, whose code is `versionCode × 10`.

## Betas

`X.Y.Z-beta.N`, mainly for the `patreon` build. No changelog; the script prints the commit subjects
since the last stable tag. `versionCode` goes up unless `--keep-code` is given (installing over the
same code works). `bump.sh beta` raises N.

## Platforms

GitHub and Patreon ship the universal APK and the four ABI APKs; the `foss` flavor (F-Droid and
other
stores) ships the universal one only.

## Steps

1. Draft `changelogs.yml` with `changelog.sh`, then edit it (stable only).
2. `./gradlew test lint`, a release build on an emulator.
3. `.claude/scripts/bump.sh <minor|patch|major|beta|X.Y.Z> [--dry-run]`.
4. Commit `bump version to X.Y.Z` (with the new fastlane file), tag `vX.Y.Z`.
