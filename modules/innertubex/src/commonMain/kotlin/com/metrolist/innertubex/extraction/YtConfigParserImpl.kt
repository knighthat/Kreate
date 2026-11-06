package com.metrolist.innertubex.extraction

import com.metrolist.innertubex.InnerTube
import com.metrolist.innertubex.InnerTubeLogger
import com.metrolist.innertubex.cipher.RemotePlayerConfigStore
import com.metrolist.innertubex.cipher.YouTubeCipherService
import com.metrolist.innertubex.cipher.getTextWithoutRedirects
import com.metrolist.innertubex.d
import com.metrolist.innertubex.models.YouTubeClient
import com.metrolist.innertubex.w
import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.isSuccess
import io.ktor.http.takeFrom
import kotlinx.coroutines.CancellationException

public class YtConfigParserImpl(
    private val httpClient: HttpClient,
    private val innerTube: InnerTube,
    private val remotePlayerConfigStore: RemotePlayerConfigStore? = null,
    private val logger: InnerTubeLogger = InnerTubeLogger.NONE,
    /** When set, the signature-timestamp fallback reuses the cipher service's player download. */
    private val cipherService: YouTubeCipherService? = null,
) : YtConfigParser {
    override suspend fun fetchConfig(
        videoId: String,
        useLoginCookies: Boolean,
    ): PlayerConfig {
        require(SAFE_VIDEO_ID.matches(videoId)) { "Invalid video ID" }
        return fetchConfigPage(
            videoId,
            useLoginCookies,
            "https://www.youtube.com/watch?v=$videoId&bpctr=9999999999&has_verified=1",
            "watch",
        )
    }

    override suspend fun fetchEmbeddedConfig(
        videoId: String,
        useLoginCookies: Boolean,
    ): PlayerConfig {
        require(SAFE_VIDEO_ID.matches(videoId)) { "Invalid video ID" }
        return fetchConfigPage(
            videoId,
            useLoginCookies,
            "https://www.youtube.com/embed/$videoId?html5=1",
            "embed",
            "https://www.reddit.com/",
        )
    }

    private suspend fun fetchConfigPage(
        videoId: String,
        useLoginCookies: Boolean,
        pageUrl: String,
        pageKind: String,
        referer: String? = null,
    ): PlayerConfig {
        val cookie =
            innerTube.cookie
                ?.takeIf { useLoginCookies }
                ?.split(';')
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() && !it.startsWith("PREF=", ignoreCase = true) }
                ?.joinToString("; ")
                ?.takeIf { it.isNotEmpty() }

        // The fields sit in the first ~60 KB of a ~1.4 MB streamed page; STS often only near its middle,
        // so stop once it is either on the page or already known from the remote player config.
        var storeStsPlayerUrl: String? = null
        var storeSts: Int? = null
        val hasConfig: suspend (String) -> Boolean = hasConfig@{ html ->
            val playerUrl = extractPlayerUrl(html) ?: return@hasConfig false
            if (extractVisitorData(html) == null || extractClientVersion(html) == null) return@hasConfig false
            if (pageKind == "embed" && extractEncryptedHostFlags(html) == null) return@hasConfig false
            if (extractSignatureTimestamp(html) != null) return@hasConfig true
            if (storeStsPlayerUrl != playerUrl) {
                storeStsPlayerUrl = playerUrl
                storeSts = remotePlayerConfigStore?.getSignatureTimestamp(playerUrl)
            }
            storeSts != null
        }

        suspend fun fetchHtml(requestCookie: String?) =
            getText(Url(pageUrl), PAGE_MAX_BYTES, hasConfig) {
                header(HttpHeaders.UserAgent, YouTubeClient.USER_AGENT_WEB)
                header(HttpHeaders.Accept, "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                referer?.let { header("Referer", it) }
                requestCookie?.let { header(HttpHeaders.Cookie, it) }
                header(HttpHeaders.AcceptLanguage, innerTube.locale.acceptLanguageHeader())
                timeout {
                    requestTimeoutMillis = PAGE_TIMEOUT_MS
                    connectTimeoutMillis = PAGE_TIMEOUT_MS
                    socketTimeoutMillis = PAGE_TIMEOUT_MS
                }
            }
        val html =
            try {
                fetchHtml(cookie)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (cookie == null) throw error
                logger.w(
                    TAG,
                    "authenticated config page unavailable; retrying without cookies",
                    details = mapOf("exceptionType" to (error::class.simpleName ?: "Exception")),
                )
                fetchHtml(null)
            }
        val playerUrl =
            extractPlayerUrl(html) ?: fetchIframePlayerUrl()
                ?: error("Unable to parse YouTube player JavaScript URL")
        val sts = extractSignatureTimestamp(html)
        val resolvedSts = sts ?: remotePlayerConfigStore?.getSignatureTimestamp(playerUrl) ?: fetchPlayerSignatureTimestamp(playerUrl)
        val visitorData = extractVisitorData(html)
        val encryptedHostFlags = extractEncryptedHostFlags(html).takeIf { pageKind == "embed" }
        logger.d(
            TAG,
            "fetchConfig parsed page=$pageKind htmlSize=${html.length} " +
                "hasSts=${resolvedSts != null} hasVisitorData=${visitorData != null} " +
                "hasEncryptedHostFlags=${encryptedHostFlags != null}",
        )
        return PlayerConfig(playerUrl, resolvedSts, visitorData, extractClientVersion(html), encryptedHostFlags)
    }

    private suspend fun fetchPlayerSignatureTimestamp(playerUrl: String): Int? =
        try {
            val js =
                cipherService?.playerCode(playerUrl) ?: getText(Url(playerUrl), PLAYER_JS_MAX_BYTES) {
                    header(HttpHeaders.UserAgent, YouTubeClient.USER_AGENT_WEB)
                    timeout {
                        requestTimeoutMillis = PLAYER_TIMEOUT_MS
                        connectTimeoutMillis = PLAYER_TIMEOUT_MS
                        socketTimeoutMillis = PLAYER_TIMEOUT_MS
                    }
                }
            extractSignatureTimestamp(js)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            logger.d(TAG, "player JS timestamp unavailable type=${error::class.simpleName ?: "Exception"}")
            null
        }

    private suspend fun fetchIframePlayerUrl(): String? =
        try {
            val api =
                getText(Url("https://www.youtube.com/iframe_api"), IFRAME_MAX_BYTES) {
                    header(HttpHeaders.UserAgent, YouTubeClient.USER_AGENT_WEB)
                    timeout {
                        requestTimeoutMillis = PLAYER_TIMEOUT_MS
                        connectTimeoutMillis = PLAYER_TIMEOUT_MS
                        socketTimeoutMillis = PLAYER_TIMEOUT_MS
                    }
                }
            extractPlayerId(api)?.let { validatePlayerUrl("https://www.youtube.com/s/player/$it/player_ias.vflset/en_GB/base.js") }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            logger.d(TAG, "iframe player URL unavailable type=${error::class.simpleName ?: "Exception"}")
            null
        }

    internal fun extractPlayerUrl(html: String): String? {
        // Only the loose path pattern needs the whole page unescaped; the keyed patterns unescape their match.
        val keyed =
            KEYED_PLAYER_URL_PATTERNS.asSequence().mapNotNull {
                it
                    .find(html)
                    ?.groupValues
                    ?.get(1)
                    ?.unescapeJs()
            }
        val loose =
            sequence {
                LOOSE_PLAYER_URL_PATTERN
                    .find(html.unescapeJs())
                    ?.groupValues
                    ?.get(1)
                    ?.let { yield(it) }
            }
        return (keyed + loose)
            .mapNotNull { path ->
                validatePlayerUrl(
                    if (path.startsWith("http")) path else "https://www.youtube.com${if (path.startsWith('/')) path else "/$path"}",
                )
            }.firstOrNull()
    }

    private fun String.unescapeJs(): String = replace("\\/", "/").replace("\\u0026", "&")

    internal fun extractPlayerId(script: String): String? = PLAYER_ID_REGEX.find(script.replace("\\/", "/"))?.groupValues?.get(1)

    internal fun extractSignatureTimestamp(html: String): Int? =
        STS_PATTERNS
            .asSequence()
            .mapNotNull {
                it
                    .find(html)
                    ?.groupValues
                    ?.get(1)
                    ?.toIntOrNull()
            }.firstOrNull()

    internal fun extractClientVersion(html: String): String? = CLIENT_VERSION_REGEX.find(html)?.groupValues?.get(1)

    internal fun extractEncryptedHostFlags(html: String): String? = ENCRYPTED_HOST_FLAGS_REGEX.find(html)?.groupValues?.get(1)

    private fun extractVisitorData(html: String): String? = VISITOR_DATA_REGEX.find(html)?.groupValues?.get(1)

    private fun validatePlayerUrl(value: String): String? =
        runCatching { Url(value) }
            .getOrNull()
            ?.takeIf {
                it.protocol.name == "https" && it.port == 443 && approvedYouTubeHost(it.host) && it.user == null && it.password == null &&
                    PLAYER_PATH.matches(it.encodedPath)
            }?.toString()

    private suspend fun getText(
        url: Url,
        maxBytes: Int,
        stopWhen: (suspend (String) -> Boolean)? = null,
        configure: io.ktor.client.request.HttpRequestBuilder.() -> Unit,
    ): String {
        var currentUrl = url
        repeat(MAX_REDIRECTS + 1) { redirectCount ->
            val response = httpClient.getTextWithoutRedirects(currentUrl, maxBytes, stopWhen, configure)
            if (response.status.isSuccess()) return requireNotNull(response.body)

            val redirectUrl =
                response.headers[HttpHeaders.Location]
                    ?.takeIf { response.status.value in REDIRECT_STATUS_CODES && redirectCount < MAX_REDIRECTS }
                    ?.let { location -> runCatching { URLBuilder(currentUrl).takeFrom(location).build() }.getOrNull() }
                    ?.takeIf(::approvedYouTubeUrl)
                    ?: error("HTTP ${response.status.value}")
            currentUrl = redirectUrl
        }
        error("Too many redirects")
    }

    private fun approvedYouTubeUrl(url: Url): Boolean =
        url.protocol.name == "https" &&
            url.port == 443 &&
            approvedYouTubeHost(url.host) &&
            url.user == null &&
            url.password == null &&
            (
                url.encodedPath == "/watch" ||
                    url.encodedPath == "/iframe_api" ||
                    EMBED_PATH.matches(url.encodedPath) ||
                    PLAYER_PATH.matches(url.encodedPath)
            )

    private fun approvedYouTubeHost(host: String): Boolean =
        host == "youtube.com" || host.endsWith(".youtube.com") || host == "youtube-nocookie.com" || host.endsWith(".youtube-nocookie.com")

    private companion object {
        private const val TAG = "YtConfigParser"
        private const val PAGE_TIMEOUT_MS = 10_000L
        private const val PLAYER_TIMEOUT_MS = 8_000L
        private const val PAGE_MAX_BYTES = 4 * 1024 * 1024
        private const val PLAYER_JS_MAX_BYTES = 8 * 1024 * 1024
        private const val IFRAME_MAX_BYTES = 1 * 1024 * 1024
        private const val MAX_REDIRECTS = 3
        private val REDIRECT_STATUS_CODES = setOf(301, 302, 303, 307, 308)
        private val SAFE_VIDEO_ID = Regex("[A-Za-z0-9_-]{1,64}")
        private val EMBED_PATH = Regex("/embed/[A-Za-z0-9_-]{1,64}")
        private val PLAYER_PATH = Regex("/s/player/[A-Za-z0-9_-]+/.+\\.js")
        private val KEYED_PLAYER_URL_PATTERNS =
            listOf(
                Regex("\"PLAYER_JS_URL\":\"([^\"]+)\""),
                Regex("\"jsUrl\":\"([^\"]+)\""),
            )
        private val LOOSE_PLAYER_URL_PATTERN = Regex("(?<![A-Za-z0-9:/])(/s/player/[^\"'\\\\]+/[^\"'\\\\]*\\.js[^\"'\\\\]*)")
        private val PLAYER_ID_REGEX = Regex("/s/player/([a-zA-Z0-9_-]+)/")
        private val STS_PATTERNS =
            listOf(
                Regex("(?:signatureTimestamp|sts)\"?\\s*:\\s*([0-9]{5})"),
                Regex("\"STS\":\\s*([0-9]{5})"),
            )
        private val CLIENT_VERSION_REGEX = Regex("\"INNERTUBE_CLIENT_VERSION\"\\s*:\\s*\"([^\"]+)\"")
        private val ENCRYPTED_HOST_FLAGS_REGEX = Regex("\"encryptedHostFlags\"\\s*:\\s*\"([^\"]+)\"")
        private val VISITOR_DATA_REGEX = Regex("\"visitorData\"\\s*:\\s*\"([^\"]+)\"")
    }
}
