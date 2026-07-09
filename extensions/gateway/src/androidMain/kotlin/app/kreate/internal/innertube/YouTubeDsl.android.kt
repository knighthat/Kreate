package app.kreate.internal.innertube

import app.kreate.gateway.innertube.responses.BrowseResponse
import app.kreate.internal.innertube.responses.BrowseResponseImpl
import com.metrolist.innertube.InnerTube
import com.metrolist.innertube.models.YouTubeClient
import io.ktor.client.call.body
import kotlinx.serialization.json.JsonObject


private val innertube = InnerTube()

internal actual suspend fun browse(
    browseId: String,
    params: String?,
    continuation: String?,
    setLogin: Boolean
): BrowseResponse =
    innertube.browse( YouTubeClient.WEB_REMIX, browseId, params, continuation, setLogin )
             .body<BrowseResponseImpl>()

internal actual suspend fun accountMenu(): JsonObject =
    innertube.accountMenu( YouTubeClient.WEB_REMIX )
             .body<JsonObject>()