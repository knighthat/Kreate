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

package me.knighthat.kreate3.discord

import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.content.TextContent
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.putJsonArray

private const val TAG = "DiscordAssets"

/** How many addresses [DiscordAssets] remembers, the oldest is forgotten first. */
private const val REMEMBERED = 128

/**
 * Turns the address of a picture into the path the gateway takes for it (`mp:external/…`):
 * Discord fetches the picture once and serves its own copy. The address has to open for anyone.
 * Answers are remembered while the app runs, an address is asked once.
 */
internal class DiscordAssets(
    private val http: HttpClient,
    private val appId: String,
) {

    private val json = Json { ignoreUnknownKeys = true }

    // In the order they were asked, the oldest first
    private val paths = object : LinkedHashMap<String, String>(REMEMBERED, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean =
            size > REMEMBERED
    }

    // Address of each of the app's assets by name, once asked
    @Volatile
    private var appAssets: Map<String, String>? = null

    /**
     * Where the app's asset [key] on the developer portal can be fetched, `null` when there's no
     * such asset or Discord couldn't be asked. A session signed in with OAuth2 doesn't take an
     * asset by its key, only as an address made into a path like any picture.
     */
    suspend fun assetUrl(key: String): String? {
        appAssets?.let { return it[key] }
        return try {
            val response = http.get("https://discord.com/api/v10/oauth2/applications/$appId/assets")
            if (!response.status.isSuccess()) {
                Logger.w(tag = TAG) { "Discord didn't list the app's assets: ${response.status}" }
                return null
            }
            val body = response.bodyAsText()
            val assets = withContext(Dispatchers.Default) {
                json.parseToJsonElement(body).jsonArray.mapNotNull { asset ->
                    val fields = asset as? JsonObject ?: return@mapNotNull null
                    val id = fields.text("id") ?: return@mapNotNull null
                    val name = fields.text("name") ?: return@mapNotNull null
                    name to "https://cdn.discordapp.com/app-assets/$appId/$id.png"
                }.toMap()
            }
            appAssets = assets
            assets[key]
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.w(e, TAG) { "Couldn't list the app's assets" }
            null
        }
    }

    /** The gateway's path of the picture at [url], `null` when Discord didn't give one. */
    suspend fun path(url: String, access: String): String? {
        synchronized(paths) { paths[url] }?.let { return it }

        val path = try {
            val response =
                http.post("https://discord.com/api/v9/applications/$appId/external-assets") {
                    header(HttpHeaders.Authorization, "Bearer $access")
                    setBody(
                        TextContent(
                            buildJsonObject { putJsonArray("urls") { add(url) } }.toString(),
                            ContentType.Application.Json,
                        )
                    )
                }
            if (!response.status.isSuccess()) {
                Logger.w(tag = TAG) { "Discord didn't take a picture: ${response.status}" }
                return null
            }
            val body = response.bodyAsText()
            withContext(Dispatchers.Default) {
                json.parseToJsonElement(body).jsonArray
                    .firstNotNullOfOrNull { (it as? JsonObject)?.text("external_asset_path") }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.w(e, TAG) { "Couldn't hand a picture to Discord" }
            null
        } ?: return null

        return "mp:$path".also { synchronized(paths) { paths[url] = it } }
    }
}
