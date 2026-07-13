package app.kreate.internal.innertube

import app.kreate.gateway.innertube.responses.BrowseResponse
import app.kreate.gateway.innertube.responses.NextResponse
import app.kreate.gateway.innertube.responses.SearchResponse
import app.kreate.gateway.innertube.responses.SearchSuggestionsResponse
import app.kreate.internal.innertube.responses.BrowseResponseImpl
import app.kreate.internal.innertube.responses.NextResponseImpl
import app.kreate.internal.innertube.responses.SearchResponseImpl
import app.kreate.internal.innertube.responses.SearchSuggestionsResponseImpl
import com.metrolist.innertube.InnerTube
import com.metrolist.innertube.models.YouTubeClient
import io.ktor.client.call.body
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive


private val innertube = InnerTube()
private val VISITOR_DATA_REGEX = Regex("^Cg[t|s]")

private suspend fun verifyVisitorData() {
    if( innertube.visitorData.isNullOrBlank() ) {
        val swjs = innertube.getSwJsData().bodyAsText().substring(5)

        Json.parseToJsonElement( swjs )
            .jsonArray[0]
            .jsonArray[2]
            .jsonArray
            .first {
                (it as? JsonPrimitive)?.contentOrNull
                    ?.let( VISITOR_DATA_REGEX::containsMatchIn )?: false
            }
            .jsonPrimitive
            .content
            .also { visitorData ->
                innertube.visitorData = visitorData
            }
    }
}

internal actual suspend fun browse(
    browseId: String?,
    params: String?,
    continuation: String?,
    setLogin: Boolean
): BrowseResponse {
    verifyVisitorData()

    return innertube.browse( YouTubeClient.WEB_REMIX, browseId, params, continuation, setLogin )
                    .body<BrowseResponseImpl>()
}

internal actual suspend fun accountMenu(): JsonObject =
    innertube.accountMenu( YouTubeClient.WEB_REMIX )
             .body<JsonObject>()

internal actual suspend fun searchSuggestions( query: String ): SearchSuggestionsResponse =
    innertube.getSearchSuggestions( YouTubeClient.WEB_REMIX, query )
             .body<SearchSuggestionsResponseImpl>()

actual suspend fun searchResults( query: String?, params: String?, continuation: String? ): SearchResponse {
    verifyVisitorData()
    return innertube.search( YouTubeClient.WEB_REMIX, query, params, continuation )
                    .body<SearchResponseImpl>()
}

internal actual suspend fun getNext(
    videoId: String?,
    playlistId: String?,
    params: String?,
    continuation: String?
): NextResponse =
    innertube.next( YouTubeClient.WEB_REMIX, videoId, playlistId, null, null, params, continuation )
             .body<NextResponseImpl>()

internal actual suspend fun getYouTubeNext( videoId: String?, params: String? ): NextResponse =
    innertube.next( YouTubeClient.WEB, videoId, null, null, null, params, null )
             .body<NextResponseImpl>()