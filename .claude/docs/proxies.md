# Proxies

## Layering

The rules are in `.claude/rules/sources.md` (the app proxy covers every request, lyrics included;
a source's own proxy overrides it; an incomplete one, `SourceProxy.Incomplete`, blocks). Beyond
them:

- With every proxy off, requests use the device's Wi-Fi proxy (the system `ProxySelector`).
- The fallback lives in `sourceProxy()` (`provider/.../Proxies.kt`). The shared Ktor client is a
  singleton and follows the proxy per request (the `proxy(source) { … }` overload with a
  `ProxySelector`); source clients are rebuilt per proxy.
- Not yet tried on a device. Test it against a real proxy by making credentials wrong (requests must
  fail), not with unit tests containing credentials.

## Library behavior

- **WebView, HTTP proxy auth:** Chromium sends a proxy's 407 to
  `WebViewClient.onReceivedHttpAuthRequest` with `host` = the proxy host (no port). When the proxy
  refuses the credentials it asks again; cancelling gives `ERR_TUNNEL_CONNECTION_FAILED`. It never
  falls back to a direct connection.
- **WebView caches proxy credentials for the process's life:** after one success, wrong credentials
  still work. Test failure in a fresh process.
- **WebView, SOCKS:** `socks5://host:port` rules are accepted, but Chromium has no SOCKS
  username/password auth (`ERR_SOCKS_CONNECTION_FAILED`), so the sign-in screen refuses a SOCKS
  proxy with a username. A first load through a proxy once took over 30 s; allow ~90 s in tests.
- **`ProxyController` is process-wide**; the app's only online WebView is sign-in (`PoTokenWebView`
  blocks network loads). Clear after set is safe even while set is pending.
- **Coil `KtorNetworkFetcherFactory(httpClient = { … })`** calls the lambda once. A client that
  changes at runtime needs a custom `coil3.network.NetworkClient` (`SourceNetworkClient`).
- **Ktor `HttpClient.config {}`** reuses the same engine, so it can't change an OkHttp proxy: build
  a new client on a new `OkHttpClient` (`HttpClientFactory`).
- **OkHttp** pools connections by address, and a mutable `ProxySelector` compares equal to itself,
  so old connections would be reused after a change: one client per proxy, each with its own
  `ConnectionPool`.
- **Android SOCKS5 auth** goes through the process-wide `java.net.Authenticator.setDefault`. It's
  asked with `requestingHost`/`requestingPort` = the proxy's address, `protocol=SOCKS5`,
  `type=SERVER`,
  so one global authenticator picks credentials per proxy address (two sources on the same
  host:port can't have different credentials). TLS over the SOCKS socket works. Without an
  authenticator: "SOCKS: Connection not allowed by ruleset".

## Untested until a live test server exists

HTTP Basic proxy auth, SOCKS5 auth, wrong credentials failing closed, the Incomplete proxy refusing,
and the sign-in WebView going through the proxy.
