# nonfree

Code that talks to non-free network services, which F-Droid flags as the NonFreeNet anti-feature:

- Discord's Rich Presence (`presence/`, `ui/…/discord/DiscordSettingsScreen.kt`, uses `:discord`)
- Shazam's song recognition (`recognition/ShazamSongRecognizer.kt`, uses `:shazam`)

Only the `github` and `patreon` builds compile this folder (see `app/build.gradle.kts`); `foss`
leaves it out, along with the `:discord` and `:shazam` modules. Crowdin's texts translated after
the build (the Crowdin SDK) are patreon's alone, in `app/src/patreon/`.

Anything here that `main` calls needs a stand-in with the same name in `app/src/foss/`, or the
foss build won't compile. `NonFreeNet.kt` gives the Koin modules; foss's turns the Discord and
Shazam switches off in each profile instead. UI in `main` checks `BuildConfig.NON_FREE_NET` and
shows "Not available in FOSS build" in foss.
