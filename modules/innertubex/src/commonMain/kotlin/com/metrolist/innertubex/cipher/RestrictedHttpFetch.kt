package com.metrolist.innertubex.cipher

import com.metrolist.innertubex.bodyAsTextLimited
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.Headers
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.isSuccess

internal data class RestrictedTextResponse(
    val status: HttpStatusCode,
    val headers: Headers,
    val body: String?,
)

/**
 * Uses the caller's engine without inheriting its redirect policy or taking engine ownership.
 * The body is streamed, so [maxBytes] bounds memory and [stopWhen] can end the download early.
 */
internal suspend fun HttpClient.getTextWithoutRedirects(
    url: Url,
    maxBytes: Int,
    stopWhen: (suspend (String) -> Boolean)? = null,
    configure: HttpRequestBuilder.() -> Unit = {},
): RestrictedTextResponse {
    val directClient =
        HttpClient(engine) {
            expectSuccess = false
            followRedirects = false
            install(HttpTimeout)
        }
    return try {
        directClient.prepareGet(url, configure).execute { response ->
            if (response.status.isSuccess()) {
                RestrictedTextResponse(response.status, response.headers, response.bodyAsTextLimited(maxBytes, stopWhen))
            } else {
                response.bodyAsChannel().cancel(null)
                RestrictedTextResponse(response.status, response.headers, null)
            }
        }
    } finally {
        directClient.close()
    }
}
