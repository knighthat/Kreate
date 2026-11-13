# File index: `:discord`

Paths are relative to `discord/src/main/kotlin/me/knighthat/kreate3/discord/`; checked against the
tree on 2026-11-13, verify a path still exists before relying on it. Map of the parts:
`.claude/file-index.md`.

Depends only on libraries (Ktor core, kotlinx serialization JSON, Kermit, coroutines); knows nothing
of the app. Protocol notes: `.claude/docs/discord.md`.

- `DiscordTokens.kt` — `DiscordTokens` (access, refresh, `expiresAt` epoch seconds; `toString`
  hides them), what `DiscordAuth` answers. The module keeps no tokens: the app stores them.
- `DiscordAuth.kt` — OAuth2 + PKCE as a public client: `start()` → `DiscordSignIn` (authorize URL,
  `state`, verifier; scopes `DISCORD_SCOPES` = `openid sdk.social_layer_presence`,
  `prompt=consent`),
  `finish(signIn, code)`, `refresh`, `revoke`, `user(access)` → `DiscordUser` (`/users/@me`, avatar
  URL); `DiscordSignedOutException` for `invalid_grant`/401.
- `DiscordActivity.kt` — `DiscordActivity` (name, `ActivityType`, details/state, `StatusText`,
  start/end ms, large/small `ActivityImage` (`Asset` key or `Link` URL) + texts, `ActivityButton`s),
  `fit` (2..128 characters, a lone character padded), `toJson` (gateway shape: `application_id`,
  assets, labels in
  `buttons`, URLs in `metadata.button_urls`, two buttons at most).
- `DiscordAssets.kt` — `path(url, access)`: `POST /api/v9/applications/{id}/external-assets` →
  `mp:external/…`, 128 remembered; `assetUrl(key)`: the app's portal assets
  (`/oauth2/applications/{id}/assets`) as CDN URLs, listed once.
- `DiscordClient.kt` — `DiscordClient`: `show(activity)` (latest wins, sent at most every 2 s,
  pictures
  resolved first with a 5 s cap), `connect(access)` (the app's token; runs until cancelled, returns
  `REJECTED` on close 4004 for the app to refresh, `REFUSED` when the app is refused; gateway
  WebSocket on a copy of the given client with `WebSockets`, HELLO/heartbeat
  with jitter and missed-ACK close, IDENTIFY `Bearer` + `intents: 0` + Metrolist's properties,
  RESUME, close codes → resume/identify/rejected/refused, backoff up to 60 s; cancelled = close
  1000,
  else 4000), `status` (`DiscordStatus`).
