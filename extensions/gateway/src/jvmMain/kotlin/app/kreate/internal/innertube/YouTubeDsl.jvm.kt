package app.kreate.internal.innertube

import app.kreate.gateway.innertube.responses.BrowseResponse
import kotlinx.serialization.json.JsonObject


internal actual suspend fun browse(
    browseId: String,
    params: String?,
    continuation: String?,
    setLogin: Boolean
): BrowseResponse = TODO("Not yet implemented")

internal actual suspend fun accountMenu(): JsonObject = TODO("Not yet implemented")