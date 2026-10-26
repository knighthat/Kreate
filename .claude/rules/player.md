---
paths:
  - "app/src/main/kotlin/**/ui/screen/player/**"
---

# Full player

- Each page has a button bar (`PageButtons` in `PlayerSheet.kt`). Leaving a page resets its
  buttons: a new button's state goes in the reset in `FullPlayer`. Persisted settings (lyrics text
  size) aren't buttons and stay.
- A button that acts on what's shown is disabled while nothing is shown. `ModeButton(enabled = …)`
  dims its own icon (an explicit tint isn't dimmed by `IconButton`).
- Text over bright covers is a known open problem. An adaptive scrim and a text shadow were both
  tried and rejected: don't re-propose them; ask what was disliked before trying something else.
