<!--
  ~ Kreate
  ~ Copyright (C) 2026 KnightHat
  ~
  ~ This program is free software: you can redistribute it and/or modify
  ~ it under the terms of the GNU General Public License as published by
  ~ the Free Software Foundation, either version 3 of the License, or
  ~ (at your option) any later version.
  ~
  ~ This program is distributed in the hope that it will be useful,
  ~ but WITHOUT ANY WARRANTY; without even the implied warranty of
  ~ MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  ~ GNU General Public License for more details.
  ~
  ~ You should have received a copy of the GNU General Public License
  ~ along with this program.  If not, see <https://www.gnu.org/licenses/>.
 -->

# Discord's Rich Presence

How the app talks to Discord (`:discord`, `presence/` in `:app`), as tried on 2026-11-13 with the
maintainer's account on the emulator. The approach follows Metrolist (`.ignore.d/Metrolist`,
`discord/`), which moved from token scraping to OAuth2 in June 2026.

## The app on the developer portal

- Application id `1370148610158759966` (`BuildConfig.DISCORD_APP_ID`, not a secret), **Public
  Client** on: there's no backend, the code is traded with a PKCE verifier and no client secret.
  Turning it off later breaks the sign in and refresh of every version already out.
- Social SDK enabled through its Getting Started form; no review or verification was asked.
- Redirect `kreate3://discord/callback`, matched exactly (`BuildConfig.DISCORD_REDIRECT_URI`,
  `DiscordCallbackActivity` in the manifest).
- Scopes `openid sdk.social_layer_presence`, asked in the authorize link; the portal's URL
  Generator doesn't list `sdk.*` scopes, that's normal. `/users/@me` answers with these two, no
  `identify` needed.
- Art assets: `app_logo` (the fallback picture), `embedded_background`.

## Gateway

- `wss://gateway.discord.gg/?v=10&encoding=json`, IDENTIFY with `token: "Bearer <access>"`,
  `intents: 0` and Metrolist's `properties` (`os: android`, `browser: Discord Android`,
  `device: <app id>`); not tried with other properties. Presence is op 3 with one activity.
- Buttons: labels in `buttons`, URLs in `metadata.button_urls` (the gateway's shape, not the RPC's),
  and the activity needs `application_id`: without it the buttons show and a click does nothing
  (Metrolist leaves it out; Kizzy sends it). A user can't use the buttons of their own presence,
  test from another account.
- Pictures: a session signed in with OAuth2 **doesn't show an asset by its key** (`app_logo` showed
  nothing). Every picture, the app's assets too, goes through
  `POST /api/v9/applications/{id}/external-assets` (`{"urls": [...]}` → `external_asset_path`,
  sent as `mp:<path>`); an asset's URL is
  `https://cdn.discordapp.com/app-assets/{app id}/{asset id}.png`, ids from
  `GET /api/v10/oauth2/applications/{id}/assets` (no sign in).
- Leaving on purpose closes with 1000 (the presence goes at once); other closes use 4000 to keep
  the session resumable.

## Litterbox

Covers only the app can open (OpenSubsonic, the device's) are uploaded to
`https://litterbox.catbox.moe/resources/internals/api.php` (`reqtype=fileupload`, `time=1h`,
`fileToUpload`). The file part needs its file name in its own `Content-Disposition`: Ktor's
`append(key, value, headers)` with a second Content-Disposition header made PHP answer
`412 No file!`; `append(key, filename, contentType, size) { write(...) }` fixes it.

Not working yet: after that fix the emulator got `403` with a bot-protection HTML page, with and
without a `User-Agent` of the app's own. Not traced further (working around a bot check isn't
done); litterbox uploads are unverified.
