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

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** How long a [Message] stays on screen. */
enum class MessageDuration { SHORT, LONG, INDEFINITE }

/**
 * What to tell the user, like "Added 3 songs to the queue".
 *
 * @param dismissable whether it has a button to close it
 * @param actionLabel the button next to [text], like "Retry", `null` for none; tapping it runs
 * [onAction] and closes the message
 */
data class Message(
    val text: String,
    val duration: MessageDuration = MessageDuration.SHORT,
    val dismissable: Boolean = false,
    val actionLabel: String? = null,
    val onAction: () -> Unit = {},
)

/**
 * Where any part of the app, a screen or not, tells the user something. The app shows each
 * [Message] at the bottom of whatever screen is open, a new one replaces the one showing.
 *
 * A message posted while the app isn't on screen is dropped.
 */
class Notifier {

    private val _messages = MutableSharedFlow<Message>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** Every [Message] posted from now on. */
    val messages: SharedFlow<Message> = _messages.asSharedFlow()

    /** Shows [message]. Safe to call from any thread, it never waits. */
    fun post(message: Message) {
        _messages.tryEmit(message)
    }

    /** Shows [text] for a short time. */
    fun post(text: String) = post(Message(text))
}
