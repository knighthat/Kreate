# AboutLibraries

The open source licenses page reads `raw/aboutlibraries`, written at build time by the
`com.mikepenz.aboutlibraries.plugin.android` plugin from each dependency's POM (config in
`app/build.gradle.kts`). Found setting it up on 15.3.0 (2026-10-28):

- AGP 9's optimized resource shrinking drops the file even though code reads `R.raw.aboutlibraries`;
  `res/raw/keep.xml` keeps it. Check a release APK with `aapt2 dump resources … | grep raw/`.
- The plugin downloads license texts while building (`offlineMode` is off). A license named only by
  a POM with no SPDX id gets a hash as its id and no text.
- Overrides live in `app/aboutlibraries/` (`collect.configPath`). A `libraries/<x>.json` override
  **merges** its `licenses` with the POM's, so the textless one stays. Override the license itself
  instead: `licenses/<x>.json` with the hash from the generated JSON, plus `spdxId`, and the plugin
  fills in the text (InnerTubeX: `gpl-3.0-innertubex.json`).
- After a dependency bump, look at the generated
  `app/build/generated/aboutLibraries/<variant>/res/raw/aboutlibraries.json` for licenses without
  `content`.
