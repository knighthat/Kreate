# File index: `:api`

Paths are relative to `api/src/main/kotlin/me/knighthat/kreate3/api/`; checked against the tree on
2026-11-17, verify a path still exists before relying on it. Map of the parts:
`.claude/file-index.md`.

Requests to the project's own API (`api.knighthat.me`). Depends only on libraries (Ktor core,
kotlinx serialization JSON, Kermit, coroutines); knows nothing of the app, which hands it a client
and stores the tokens. The address comes from `KREATE_API_URL` (environment, else
`local.properties`) into `BuildConfig.API_URL` when built; never written in the code.

- `KreateApi.kt` — `API_URL` (no trailing `/`, empty without one), `isApiAvailable`;
  `ApiUnavailableException` (no address, an `IOException` so it reads as offline),
  `PatreonSignedOutException` (refresh 401: discard the blob), `ApiException` (status + `error`
  code); `JsonObject.text`/`texts`; `PatreonFeature` (feature ids such as `BACKUPS`).
- `PatreonProfile.kt` — `PatreonProfile` (id, name, username, avatar, tier, tierTitle,
  features, `PatronStatus`, nextChargeDate; display only, never for access), from the API's
  `profile`
  (login, refresh, web view fragment), `encode`/`decode` for storing it; `toString` hides it.
- `PatreonTokens.kt` — `PatreonTokens` (`token` JWT, `refresh` blob, `profile`; `toString` hides
  them),
  `PatreonClaims` (`userId`, `tier`, `features` + `has(feature)`, `PatronStatus`, `expiresAt`; read
  from the payload unchecked,
  `UNKNOWN` when unreadable, unknown statuses → `NONE`).
- `PatreonAuth.kt` — `PatreonAuth(http, redirect, apiUrl)`: system browser with PKCE (`start()` →
  `PatreonSignIn` with the login URL and verifier; `finish(signIn, url)` parses the redirect and
  posts `/patreon/token`, `invalid_grant` → `Expired`), web view (`webViewUrl`,
  `webViewResult(url)` reads the `/patreon/done` fragment), `refresh(blob)` (`/patreon/refresh`;
  one at a time is the caller's job); `PatreonLogin` (`Success`, `Declined`, `Expired`, `Failed`).
