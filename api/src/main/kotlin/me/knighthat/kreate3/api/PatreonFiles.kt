/*
 * Kreate
 * Copyright (C) 2026 KnightHat
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package me.knighthat.kreate3.api

import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import java.io.IOException
import kotlin.time.Instant

private const val TAG = "PatreonFiles"

/**
 * A file the user keeps online.
 *
 * @property size in bytes
 * @property lastModified when it was last uploaded, `null` when the answer's time can't be read.
 *   The server deletes it 180 days after that
 */
data class RemoteFile(val name: String, val size: Long, val lastModified: Instant?)

/**
 * The token was refused (401: expired, or missing): refresh it and ask again, sign in again only
 * when the refresh is refused too.
 */
class PatreonTokenRefusedException(error: String?) :
    IOException("The files API refused the token: ${error.orEmpty()}")

/**
 * The tier doesn't have the files (403 `tier_too_low`). The user may have changed the pledge
 * since the token was issued, a refresh tells.
 *
 * @property patronStatus why, to pick the app's own text
 * @property message the server's English text, shown as it is or not at all, never parsed
 */
class PatreonTierException(val patronStatus: PatronStatus, override val message: String?) :
    IOException("The tier has no files: $patronStatus")

/**
 * The user's files kept online, on the host of [FILES_URL]. Every request takes the token
 * (`PatreonTokens.token`) and none is kept here; the file data itself never passes through it.
 * All requests fail with [ApiUnavailableException] when the build has no [FILES_URL].
 *
 * @param http the app's client, so its proxy and timeouts apply
 * @param filesUrl where the files API is, [FILES_URL] outside tests
 */
class PatreonFiles(
    private val http: HttpClient,
    private val filesUrl: String = FILES_URL,
) {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Every file the user keeps online, each page of the list asked in turn.
     *
     * @throws PatreonTokenRefusedException when [token] expired: refresh, then ask again
     * @throws PatreonTierException when the tier has no files
     * @throws IOException when the API can't be reached or fails
     */
    suspend fun list(token: String): List<RemoteFile> {
        if (filesUrl.isEmpty()) throw ApiUnavailableException()

        val files = mutableListOf<RemoteFile>()
        var cursor: String? = null
        do {
            val url = URLBuilder("$filesUrl/api/files").apply {
                cursor?.let { parameters.append("cursor", it) }
            }.buildString()
            val response = http.get(url) { header(HttpHeaders.Authorization, "Bearer $token") }
            val body = response.bodyAsText()
            cursor = withContext(Dispatchers.Default) {
                val answer = parse(body)
                if (response.status != HttpStatusCode.OK) throw failure(response.status, answer)

                (answer?.get("files") as? JsonArray)?.mapNotNullTo(files) { fileOf(it as? JsonObject) }
                answer?.text("next")?.ifEmpty { null }
            }
        } while (cursor != null)
        return files
    }

    private fun fileOf(file: JsonObject?): RemoteFile? {
        val name = file?.text("name")?.ifEmpty { null } ?: return null
        val size = (file["size"] as? JsonPrimitive)?.longOrNull ?: 0
        val lastModified = file.text("lastModified")?.let {
            try {
                Instant.parse(it)
            } catch (_: IllegalArgumentException) {
                null
            }
        }
        return RemoteFile(name, size, lastModified)
    }

    private fun failure(status: HttpStatusCode, answer: JsonObject?): IOException {
        val error = answer?.text("error")
        Logger.w(tag = TAG) { "The files API answered $status $error" }
        return when {
            status == HttpStatusCode.Unauthorized -> PatreonTokenRefusedException(error)
            error == "tier_too_low" ->
                PatreonTierException(
                    patronStatusOf(answer.text("patronStatus")),
                    answer.text("message"),
                )

            else -> ApiException(status.value, error)
        }
    }

    private fun parse(body: String): JsonObject? =
        runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
}
