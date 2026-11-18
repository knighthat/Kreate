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
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLBuilder
import io.ktor.http.content.ByteArrayContent
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

/** Storage has no file of that name (404), like one the server deleted after 180 days. */
class PatreonFileMissingException(name: String) : IOException("No file online named $name")

/** The file is bigger than [maxBytes], the per-file limit or the space left (413). */
class PatreonFileTooLargeException(val maxBytes: Long?) :
    IOException("The file is over the $maxBytes bytes left")

/**
 * No room for another file (403 `file_limit_reached`, 50 files) or its size
 * (`storage_limit_reached`, 1 GiB). Only the server's 180-day clean up frees room, nothing the app
 * can delete.
 */
class PatreonStorageFullException(val error: String) : IOException("The storage is full: $error")

/**
 * The user's files kept online, on the host of [FILES_URL]: list, upload, download, delete.
 * Every request takes the token (`PatreonTokens.token`) and none is kept here; the file data
 * itself never passes through it.
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

    /**
     * Uploads [data] as [name], replacing a file of that name. The link is asked for its exact
     * size just before, and asked again once when storage refuses it (expired); the token never
     * goes to storage.
     *
     * @param name letters, digits, `.`, `_` and `-`, 128 at most, not starting with `.`
     * @throws PatreonTokenRefusedException when [token] expired: refresh, then upload again
     * @throws PatreonTierException when the tier has no files
     * @throws PatreonFileTooLargeException when [data] is over the size allowed
     * @throws PatreonStorageFullException when there is no room left
     * @throws IOException when the API or storage can't be reached or fail
     */
    suspend fun upload(token: String, name: String, data: ByteArray) {
        if (filesUrl.isEmpty()) throw ApiUnavailableException()
        if (data.isEmpty()) throw IOException("An empty file can't be stored")

        repeat(2) { attempt ->
            val link = uploadLink(token, name, data.size.toLong())
            val headers = link.headers
            val response = http.put(link.url) {
                headers.forEach { (key, value) ->
                    // The body sets these two itself
                    if (!key.equals(HttpHeaders.ContentType, ignoreCase = true) &&
                        !key.equals(HttpHeaders.ContentLength, ignoreCase = true)
                    )
                        header(key, value)
                }
                val type = headers.entries
                    .firstOrNull { it.key.equals(HttpHeaders.ContentType, ignoreCase = true) }
                    ?.let { ContentType.parse(it.value) }
                    ?: ContentType.Application.OctetStream
                // A whole body, so its length is sent as it is and never chunked
                setBody(ByteArrayContent(data, type))
            }
            when {
                response.status == HttpStatusCode.OK -> return
                // The link expired, or the size isn't the one it was signed for
                response.status == HttpStatusCode.Forbidden && attempt == 0 ->
                    Logger.w(tag = TAG) { "Storage refused the upload link, asking for another" }

                else -> {
                    Logger.w(tag = TAG) { "Storage refused the upload: ${response.status}" }
                    throw IOException("Storage answered ${response.status}")
                }
            }
        }
    }

    /**
     * The content of the file [name], from the link asked for it, asked again once when storage
     * refuses it (expired); the token never goes to storage.
     *
     * @throws PatreonTokenRefusedException when [token] expired: refresh, then download again
     * @throws PatreonTierException when the tier has no files
     * @throws PatreonFileMissingException when there is no such file
     * @throws IOException when the API or storage can't be reached or fail
     */
    suspend fun download(token: String, name: String): ByteArray {
        if (filesUrl.isEmpty()) throw ApiUnavailableException()

        repeat(2) { attempt ->
            val response = http.get(downloadLink(token, name))
            when {
                response.status == HttpStatusCode.OK -> return response.bodyAsBytes()
                response.status == HttpStatusCode.NotFound -> throw PatreonFileMissingException(name)
                // The link expired
                response.status == HttpStatusCode.Forbidden && attempt == 0 ->
                    Logger.w(tag = TAG) { "Storage refused the download link, asking for another" }

                else -> {
                    Logger.w(tag = TAG) { "Storage refused the download: ${response.status}" }
                    throw IOException("Storage answered ${response.status}")
                }
            }
        }
        throw IOException("Storage refused the download")
    }

    /**
     * Deletes the file [name] at once, for good: ask the user first.
     *
     * @return `false` when there was no such file (404 `file_not_found`), it's gone either way
     *
     * @throws PatreonTokenRefusedException when [token] expired: refresh, then delete again
     * @throws PatreonTierException when the tier has no files, deleting needs them too
     * @throws IOException when the API can't be reached or fails
     */
    suspend fun delete(token: String, name: String): Boolean {
        if (filesUrl.isEmpty()) throw ApiUnavailableException()

        val url = URLBuilder("$filesUrl/api/delete").apply {
            parameters.append("name", name)
        }.buildString()
        val response = http.delete(url) { header(HttpHeaders.Authorization, "Bearer $token") }
        if (response.status == HttpStatusCode.NoContent || response.status == HttpStatusCode.OK)
            return true

        val body = response.bodyAsText()
        return withContext(Dispatchers.Default) {
            val answer = parse(body)
            val gone = response.status == HttpStatusCode.NotFound &&
                    answer?.text("error") == "file_not_found"
            if (!gone) throw failure(response.status, answer)
            false
        }
    }

    private suspend fun downloadLink(token: String, name: String): String {
        val url = URLBuilder("$filesUrl/api/download").apply {
            parameters.append("name", name)
        }.buildString()
        val response = http.get(url) { header(HttpHeaders.Authorization, "Bearer $token") }
        val body = response.bodyAsText()
        return withContext(Dispatchers.Default) {
            val answer = parse(body)
            if (response.status != HttpStatusCode.OK) throw failure(response.status, answer)
            answer?.text("url")?.ifEmpty { null }
                ?: throw IOException("The API's answer has no download link")
        }
    }

    private class UploadLink(val url: String, val headers: Map<String, String>)

    private suspend fun uploadLink(token: String, name: String, size: Long): UploadLink {
        val url = URLBuilder("$filesUrl/api/upload").apply {
            parameters.append("name", name)
            parameters.append("size", size.toString())
        }.buildString()
        val response = http.get(url) { header(HttpHeaders.Authorization, "Bearer $token") }
        val body = response.bodyAsText()
        return withContext(Dispatchers.Default) {
            val answer = parse(body)
            if (response.status != HttpStatusCode.OK) throw failure(response.status, answer)

            val link = answer?.text("url")?.ifEmpty { null }
                ?: throw IOException("The API's answer has no upload link")
            val headers = (answer["headers"] as? JsonObject)
                ?.mapNotNull { (key, value) ->
                    (value as? JsonPrimitive)?.takeIf { it.isString }?.let { key to it.content }
                }
                ?.toMap()
                .orEmpty()
            UploadLink(link, headers)
        }
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
            error == "file_too_large" ->
                PatreonFileTooLargeException((answer["maxBytes"] as? JsonPrimitive)?.longOrNull)

            error == "file_limit_reached" || error == "storage_limit_reached" ->
                PatreonStorageFullException(error)

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
