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
import io.ktor.client.plugins.pluginOrNull
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.parameter
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

private const val TAG = "DiscordClient"

private const val GATEWAY_URL = "wss://gateway.discord.gg"

// Opcodes of the gateway
private const val DISPATCH = 0
private const val HEARTBEAT = 1
private const val IDENTIFY = 2
private const val PRESENCE_UPDATE = 3
private const val RESUME = 6
private const val RECONNECT = 7
private const val INVALID_SESSION = 9
private const val HELLO = 10
private const val HEARTBEAT_ACK = 11

/** The least time between two presences, the gateway closes on too many. */
private val PRESENCE_GAP = 2.seconds

/** How long a picture may take to be handed to Discord before the presence goes without it. */
private val PICTURE_WAIT = 5.seconds

/** Longest wait before connecting again after failures. */
private val LONGEST_BACKOFF = 60.seconds

/** Whether the client is connected, see [DiscordClient.status]. */
enum class DiscordStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,

    /** The gateway refused the token, it needs a refresh. */
    REJECTED,

    /** Discord refuses the app on its gateway, connecting again won't help. */
    REFUSED,
}

/**
 * Shows the user's activity on their Discord profile, through the gateway, signed in with the
 * OAuth2 token the app gives [connect]; the app keeps and refreshes the tokens (see
 * [DiscordAuth]). The connection lasts while [connect] runs; [show] sets what it shows.
 *
 * @param http the app's client, the gateway goes through a copy of it with WebSockets. Its
 * engine must not time out reads, the gateway is quiet between heartbeats
 */
class DiscordClient(
    http: HttpClient,
    private val appId: String,
) {

    private val sockets: HttpClient =
        if (http.pluginOrNull(WebSockets) != null) http else http.config { install(WebSockets) }
    private val assets = DiscordAssets(http, appId)
    private val json = Json { ignoreUnknownKeys = true }

    private val _status = MutableStateFlow(DiscordStatus.DISCONNECTED)
    private val activity = MutableStateFlow<DiscordActivity?>(null)

    /** Whether the client is connected now. */
    val status: StateFlow<DiscordStatus> = _status.asStateFlow()

    /** Shows [activity] on the profile, `null` to show nothing. Sent once connected. */
    fun show(activity: DiscordActivity?) {
        this.activity.value = activity
    }

    /**
     * Connects with the OAuth2 [access] token and stays connected, connecting again when the
     * connection drops, until it's cancelled or Discord won't take the token or the app.
     *
     * @return why it gave up: [DiscordStatus.REJECTED] when the gateway refused the token (refresh
     * it and connect again), [DiscordStatus.REFUSED] when it refused the app
     */
    suspend fun connect(access: String): DiscordStatus {
        val state = Resumable()
        var failures = 0
        try {
            while (true) {
                _status.value = DiscordStatus.CONNECTING
                val end = try {
                    session(access, state) { failures = 0 }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Logger.w(e, TAG) { "The gateway's connection dropped" }
                    Ending.RESUME
                }

                when (end) {
                    Ending.REJECTED -> {
                        _status.value = DiscordStatus.REJECTED
                        return DiscordStatus.REJECTED
                    }

                    Ending.REFUSED -> {
                        _status.value = DiscordStatus.REFUSED
                        return DiscordStatus.REFUSED
                    }

                    Ending.IDENTIFY -> state.forget()
                    Ending.RESUME -> Unit
                }
                _status.value = DiscordStatus.CONNECTING
                // At once after a connection that went well, longer after each failure
                delay(backoff(failures))
                failures++
            }
        } finally {
            if (_status.value == DiscordStatus.CONNECTING || _status.value == DiscordStatus.CONNECTED)
                _status.value = DiscordStatus.DISCONNECTED
        }
    }

    /**
     * One connection to the gateway, until it ends.
     *
     * @param state the session to resume, updated as frames come
     * @param onReady called once the gateway took the session
     * @return what to do next
     */
    private suspend fun session(
        access: String,
        state: Resumable,
        onReady: () -> Unit,
    ): Ending {
        val socket = sockets.webSocketSession(state.url ?: GATEWAY_URL) {
            // Version and encoding of the gateway, added to every address of it
            parameter("v", 10)
            parameter("encoding", "json")
        }
        // Leaving on purpose ends the session, which takes the presence off at once. Any other
        // close keeps it to be resumed
        var leaving = false
        try {
            return coroutineScope {
                val hello = socket.next() ?: return@coroutineScope Ending.RESUME
                if (hello.op != HELLO) return@coroutineScope Ending.RESUME
                val interval = hello.d?.let { it as? JsonObject }?.get("heartbeat_interval")
                    ?.jsonPrimitive?.longOrNull?.milliseconds ?: 41.seconds

                var acknowledged = true
                val heartbeat = launch {
                    // The first one after a random part of the interval, as Discord asks
                    delay(interval * Random.nextDouble())
                    while (isActive) {
                        if (!acknowledged) {
                            Logger.w(tag = TAG) { "The gateway missed a heartbeat" }
                            socket.close(CloseReason(4000, "Heartbeat missed"))
                            break
                        }
                        acknowledged = false
                        socket.sendOp(HEARTBEAT, state.sequence?.let(::JsonPrimitive) ?: JsonNull)
                        delay(interval)
                    }
                }

                val sessionId = state.sessionId
                if (sessionId != null) {
                    socket.sendOp(RESUME, buildJsonObject {
                        put("token", "Bearer $access")
                        put("session_id", sessionId)
                        put("seq", state.sequence)
                    })
                } else {
                    socket.sendOp(IDENTIFY, buildJsonObject {
                        put("token", "Bearer $access")
                        put("intents", 0)
                        // What Metrolist sends, which Discord takes for a token of the Social SDK
                        putJsonObject("properties") {
                            put("os", "android")
                            put("browser", "Discord Android")
                            put("device", appId)
                        }
                    })
                }

                var presence: Job? = null
                val ready = {
                    if (presence == null) {
                        _status.value = DiscordStatus.CONNECTED
                        onReady()
                        presence = launch { sendPresences(socket, access) }
                    }
                }

                val end = receive(socket, state, ready) { acknowledged = true }
                heartbeat.cancel()
                presence?.cancel()
                end
            }
        } catch (e: CancellationException) {
            leaving = true
            throw e
        } finally {
            withContext(NonCancellable) {
                runCatching {
                    if (leaving)
                        socket.close(CloseReason(CloseReason.Codes.NORMAL, "Leaving"))
                    else
                        socket.close(CloseReason(4000, "Connecting again"))
                }
            }
        }
    }

    /** Reads the gateway's frames until it closes, and says what to do then. */
    private suspend fun receive(
        socket: DefaultClientWebSocketSession,
        state: Resumable,
        onReady: () -> Unit,
        onAcknowledged: () -> Unit,
    ): Ending {
        while (true) {
            val payload = socket.next() ?: break
            payload.s?.let { state.sequence = it }
            when (payload.op) {
                DISPATCH -> when (payload.t) {
                    "READY" -> {
                        val data = payload.d as? JsonObject
                        state.sessionId = data?.text("session_id")
                        state.url = data?.text("resume_gateway_url")
                        Logger.i(tag = TAG) { "Connected to the gateway" }
                        onReady()
                    }

                    "RESUMED" -> {
                        Logger.i(tag = TAG) { "Resumed the gateway's session" }
                        onReady()
                    }
                }

                HEARTBEAT ->
                    socket.sendOp(HEARTBEAT, state.sequence?.let(::JsonPrimitive) ?: JsonNull)

                HEARTBEAT_ACK -> onAcknowledged()
                RECONNECT -> {
                    Logger.i(tag = TAG) { "The gateway asked to connect again" }
                    return Ending.RESUME
                }

                INVALID_SESSION -> {
                    val resumable = (payload.d as? JsonPrimitive)?.booleanOrNull == true
                    Logger.w(tag = TAG) { "The gateway ended the session, resumable: $resumable" }
                    if (resumable) return Ending.RESUME
                    state.forget()
                    // Discord asks for 1 to 5 seconds before identifying again
                    delay((1000 + Random.nextLong(4000)).milliseconds)
                    return Ending.IDENTIFY
                }
            }
        }

        val reason = withTimeoutOrNull(1.seconds) { socket.closeReason.await() }
        val code = reason?.code?.toInt()
        Logger.w(tag = TAG) { "The gateway closed: $code ${reason?.message.orEmpty()}" }
        return when (code) {
            // Authentication failed: the app refreshes the token
            4004 -> Ending.REJECTED
            // Wrong shard, sharding, version or intents: connecting again won't help
            4010, 4011, 4012, 4013, 4014 -> Ending.REFUSED
            // Not authenticated, already authenticated, wrong sequence, session timed out
            4003, 4005, 4007, 4009, 1000, 1001 -> {
                state.forget()
                Ending.IDENTIFY
            }

            else -> Ending.RESUME
        }
    }

    /** Sends the activity to show each time it changes, no closer than [PRESENCE_GAP]. */
    private suspend fun sendPresences(socket: DefaultClientWebSocketSession, access: String) {
        var last: TimeSource.Monotonic.ValueTimeMark? = null
        activity.collectLatest { shown ->
            last?.let { delay(PRESENCE_GAP - it.elapsedNow()) }
            val activities = if (shown == null) null else {
                val large = shown.largeImage?.let { picture(it, access) }
                val small = shown.smallImage?.let { picture(it, access) }
                shown.toJson(large, small, appId)
            }
            socket.sendOp(PRESENCE_UPDATE, buildJsonObject {
                put("since", JsonNull)
                putJsonArray("activities") { activities?.let { add(it) } }
                put("status", "online")
                put("afk", false)
            })
            last = TimeSource.Monotonic.markNow()
            Logger.d(tag = TAG) { "Presence sent, showing something: ${shown != null}" }
        }
    }

    /** What the gateway names [image], `null` when Discord didn't take it in time. */
    private suspend fun picture(image: ActivityImage, access: String): String? =
        when (image) {
            is ActivityImage.Asset -> withTimeoutOrNull(PICTURE_WAIT) {
                assets.assetUrl(image.key)?.let { assets.path(it, access) }
            } ?: image.key

            is ActivityImage.Link ->
                withTimeoutOrNull(PICTURE_WAIT) { assets.path(image.url, access) }
        }

    private suspend fun DefaultClientWebSocketSession.sendOp(op: Int, data: JsonElement) =
        send(Frame.Text(buildJsonObject {
            put("op", op)
            put("d", data)
        }.toString()))

    /** The next payload, `null` once the gateway closed. Frames that aren't text are skipped. */
    private suspend fun DefaultClientWebSocketSession.next(): Payload? {
        while (true) {
            val frame = incoming.receiveCatching().getOrNull() ?: return null
            if (frame !is Frame.Text) continue
            val text = frame.readText()
            val payload = withContext(Dispatchers.Default) {
                runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull()
            } ?: continue
            return Payload(
                op = payload["op"]?.jsonPrimitive?.intOrNull ?: continue,
                d = payload["d"],
                s = (payload["s"] as? JsonPrimitive)?.intOrNull,
                t = payload.text("t"),
            )
        }
    }

    private fun backoff(failures: Int): Duration {
        if (failures <= 0) return Random.nextLong(1000).milliseconds
        val seconds = (1L shl (failures - 1).coerceAtMost(6)).seconds
        return minOf(seconds, LONGEST_BACKOFF) + Random.nextLong(1000).milliseconds
    }

    private class Payload(val op: Int, val d: JsonElement?, val s: Int?, val t: String?)

    /** What resumes a session of the gateway, what Discord answered last. */
    private class Resumable {
        var sessionId: String? = null
        var sequence: Int? = null
        var url: String? = null

        fun forget() {
            sessionId = null
            sequence = null
            url = null
        }
    }

    /** What to do once a connection ended. */
    private enum class Ending {
        /** Connect again, resuming the session. */
        RESUME,

        /** Connect again with a new session. */
        IDENTIFY,

        /** The token was refused, see [DiscordStatus.REJECTED]. */
        REJECTED,

        /** Give up, see [DiscordStatus.REFUSED]. */
        REFUSED,
    }
}
