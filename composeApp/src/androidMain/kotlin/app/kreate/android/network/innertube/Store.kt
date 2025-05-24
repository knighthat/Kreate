package app.kreate.android.network.innertube

import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.models.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import me.knighthat.utils.Toaster
import org.jetbrains.annotations.Blocking
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.services.youtube.InnertubeClientRequestInfo
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper

object Store {

    private const val DEFAULT_COOKIE = "PREF=hl=en&tz=UTC; SOCS=CAI"

    private lateinit var ghostResponseHeaders: Headers
    private lateinit var ghostResponseBody: String
    private lateinit var cookie: String

    private lateinit var iosVisitorData: String

    @Blocking
    private suspend fun fetchIfNeeded() {
        if( ::ghostResponseBody.isInitialized && ::ghostResponseHeaders.isInitialized )
            return

        runCatching {
            Innertube.client.get("https://www.youtube.com/watch?v=dQw4w9WgXcQ&bpctr=9999999999&has_verified=1") {
                headers {
                    append( HttpHeaders.Connection, "Close" )
                    append( HttpHeaders.Host, "https://www.youtube.com" )
                    append( HttpHeaders.Cookie, DEFAULT_COOKIE )
                    append( HttpHeaders.UserAgent, Context.USER_AGENT_WEB )
                    append( "Sec-Fetch-Mode", "navigate" )
                }
            }
        }.fold(
            onSuccess = {
                // Cache for later use
                ghostResponseHeaders = it.headers
                ghostResponseBody = it.bodyAsText()
            },
            onFailure = {
                Toaster.e("Failed to get visitorData")
                it.printStackTrace()
            }
        )
    }

    fun getIosVisitorData(): String {
        if( ::iosVisitorData.isInitialized )
            return iosVisitorData

        val headers: MutableMap<String, List<String>> = mutableMapOf()
        headers["User-Agent"] = listOf( YoutubeParsingHelper.getIosUserAgent( Localization.DEFAULT ) )
        headers.putAll(YoutubeParsingHelper.getOriginReferrerHeaders("https://www.youtube.com"))

        iosVisitorData = YoutubeParsingHelper.getVisitorDataFromInnertube(
            InnertubeClientRequestInfo.ofIosClient(),
            Localization.DEFAULT,
            ContentCountry.DEFAULT,
            headers,
            YoutubeParsingHelper.YOUTUBEI_V1_URL,
            null,
            false
        )

        return iosVisitorData
    }

    @Blocking
    fun getCookie(): String {
        if( ::cookie.isInitialized )
            return cookie

        runBlocking( Dispatchers.IO ) { fetchIfNeeded() }

        if( ::ghostResponseHeaders.isInitialized )
            ghostResponseHeaders.getAll(HttpHeaders.SetCookie)
                                .orEmpty()
                                .joinToString("; ") {
                                    it.split(";").first()
                                }
                                .let { cookie = "$DEFAULT_COOKIE; $it" }
        else
            cookie = DEFAULT_COOKIE

        return cookie
    }
}