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

package me.knighthat.kreate3.notification

import android.content.res.Resources
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import me.knighthat.kreate3.R

/** How long a [Message] stays on screen. */
enum class MessageDuration { SHORT, LONG, INDEFINITE }

/**
 * What to tell the user, like "Added 3 songs to the queue".
 *
 * @param dismissable whether it has a button to close it
 * @param actionLabel the button next to [text], like "Retry", `null` for none; tapping it runs
 * [onAction] and closes the message
 * @param channel where it shows as a system notification while no screen shows messages, `null`
 * to drop it then, like for a reply to a tap
 */
data class Message(
    val text: String,
    val duration: MessageDuration = MessageDuration.SHORT,
    val dismissable: Boolean = false,
    val actionLabel: String? = null,
    val onAction: () -> Unit = {},
    val channel: AppChannel? = null,
)

/**
 * [text] with an Undo button that runs [undo], on the main thread: it hands any slow work to a
 * scope that outlives the screen, the message can outlive it. Shown long, so there's time to
 * reach the button, and only on screen, never as a system notification.
 */
fun undoMessage(resources: Resources, text: String, undo: () -> Unit): Message = Message(
    text = text,
    duration = MessageDuration.LONG,
    actionLabel = resources.getString(R.string.action_undo),
    onAction = undo,
)

/**
 * Where any part of the app, a screen or not, tells the user something. The app shows each
 * [Message] at the bottom of whatever screen is open, a new one replaces the one showing.
 *
 * While no screen shows messages, like when the app is in the background, a message with a
 * [Message.channel] goes to [system] instead, and one without is dropped.
 *
 * @param system `null` where there is nothing but screens, like in previews
 */
class Notifier(private val system: SystemNotifications? = null) {

    private val _messages = MutableSharedFlow<Message>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** Every [Message] posted from now on, for a screen to show. */
    val messages: SharedFlow<Message> = _messages.asSharedFlow()

    /**
     * Suspends until a screen shows messages, see ShowMessages. A message posted before that
     * without a [Message.channel] is dropped.
     */
    suspend fun awaitScreen() {
        _messages.subscriptionCount.first { it > 0 }
    }

    /** Shows [message]. Safe to call from any thread, it never waits. */
    fun post(message: Message) {
        // Screens collect only while the app is on screen, see ShowMessages
        if (_messages.subscriptionCount.value == 0 && message.channel != null)
            system?.post(message)
        else
            _messages.tryEmit(message)
    }

    /** Shows [text] for a short time, see [Message.channel] for [channel]. */
    fun post(text: String, channel: AppChannel? = null) = post(Message(text, channel = channel))
}
