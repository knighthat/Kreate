# File index: `:api`

Paths are relative to `api/src/main/kotlin/me/knighthat/kreate3/api/`; checked against the tree on
2026-11-17, verify a path still exists before relying on it. Map of the parts:
`.claude/file-index.md`.

Requests to the project's own API (`api.knighthat.me`, files on `kreate.knighthat.me`). Depends only
on libraries (Ktor core,
kotlinx serialization JSON, Kermit, coroutines); knows nothing of the app, which hands it a client
and stores the tokens. The addresses come from `KREATE_API_URL` and `KREATE_FILES_URL`
(environment, else `local.properties`) into `BuildConfig.API_URL`/`FILES_URL` when built; never
written in the code.

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
  `PatreonClaims` (`userId`, `tier`, `features` + `has(feature)`, `listsFeatures` (old tokens lack
  the claim), `PatronStatus`, `expiresAt`; read
  from the payload unchecked,
  `UNKNOWN` when unreadable, unknown statuses → `NONE`).
- `PatreonAuth.kt` — `PatreonAuth(http, redirect, apiUrl)`: system browser with PKCE (`start()` →
  `PatreonSignIn` with the login URL and verifier; `finish(signIn, url)` parses the redirect and
  posts `/patreon/token`, `invalid_grant` → `Expired`), web view (`webViewUrl`,
  `webViewResult(url)` reads the `/patreon/done` fragment), `refresh(blob)` (`/patreon/refresh`;
  one at a time is the caller's job); `PatreonLogin` (`Success`, `Declined`, `Expired`, `Failed`).
- `PatreonFiles.kt` — `PatreonFiles(http, filesUrl)`: `list(token)` (`/api/files`, every page
  through `cursor`) → `RemoteFile` (name, size, `lastModified` as `Instant`); 401 →
  `PatreonTokenRefusedException` (refresh, ask again), `tier_too_low` → `PatreonTierException`
  (`patronStatus`, server `message`), others `ApiException`. `upload(token, name, data)`: asks
  `/api/upload` for the exact size, PUTs the bytes with the link's headers and no token, a 403 from
  storage asks for a new link once; `file_too_large` → `PatreonFileTooLargeException(maxBytes)`,
  `file_limit_reached`/`storage_limit_reached` → `PatreonStorageFullException`.
  `download(token, name)`: link from `/api/download`, then the bytes without the token,
  a 403 asks for a new link once, 404 → `PatreonFileMissingException`. `delete(token, name)`:
  `DELETE /api/delete`, 204 → `true`, `file_not_found` → `false` (gone already).
