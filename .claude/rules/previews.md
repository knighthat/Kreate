---
paths:
  - "app/src/main/kotlin/**/ui/component/**"
  - "app/src/main/kotlin/**/ui/screen/**"
---

# Previews

- Every file of `ui/component` that draws something ends with its own previews, in a
  `//<editor-fold defaultstate="collapsed" desc="Previews">` … `//</editor-fold>` block
  (`SeekBar.kt`): private composables with `@ComponentPreviews`, wrapped in `PreviewTheme` and a
  `Surface` unless the component has its own background, built from the samples of
  `PreviewSamples.kt`. Preview-only helpers and fakes stay private inside the block. No shared
  preview files: previews live beside what they show.
- A new component, or a new state of one (a flag, a mode), gets its preview in the same change. A
  part that needs Koin, the navigator or the menu host is previewed through its pieces that don't
  (`SongMenu.kt`). What only an effect shows has no preview, a static preview never runs it
  (`MessageHost`).
- Every screen in `ui/screen` has `@ScreenPreviews` on a private
  `XPreview() = PreviewTheme { XContent(…) }` at the end of its file, with made-up data from
  `PreviewSamples.kt` (or literals there), showing the screen in use (signed in, with items), not
  empty. A new screen comes with its preview.
- Previews render without the app: no Koin, so no `Preferences`, `Secrets`, view models,
  `koinInject` or `PlaybackController`. The public `XScreen` reads those and passes values to a
  private stateless `XContent`, which the preview calls. Writes may stay in the content as lambdas
  (`{ Preferences.X.update(it) }`, they only run on a tap), never as `Preferences.X::update`,
  which reads the setting while composing. A part that reads settings itself (`ProxySettings`,
  `StreamQualitySettings`, `PlaybackMiniPlayer`) is a slot of the content: the preview passes its
  stateless overload, or `{}` for the mini player.
- Strings only a preview shows (`KreateThemePreview.kt`) are not extracted.
- Check a preview by rendering it (`PreviewActivity`, see `.claude/docs/emulator.md`).
