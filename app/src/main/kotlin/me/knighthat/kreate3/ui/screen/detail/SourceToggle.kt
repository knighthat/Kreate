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

package me.knighthat.kreate3.ui.screen.detail

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.knighthat.kreate3.notification.Message
import me.knighthat.kreate3.notification.MessageDuration
import me.knighthat.kreate3.notification.Notifier
import me.knighthat.kreate3.provider.opensubsonic.ServerCheck
import me.knighthat.kreate3.provider.opensubsonic.ServerException
import me.knighthat.kreate3.provider.youtube.YouTubeException
import java.util.concurrent.atomic.AtomicInteger

private const val TAG = "SourceToggle"

/**
 * An on/off choice of the user that a source keeps, like an album bookmarked on the server. A
 * [toggle] flips it at once and sends it; when the source doesn't take it, it flips back, unless
 * another tap replaced it meanwhile, and [notifier] says why. It is sent on [scope], the app's, so
 * it lands even when the screen closes right away, and taps are sent in their order.
 *
 * @param send tells the source the new value, throws when it can't
 * @param failure what to tell the user when [send] threw, given the value it sent
 */
class SourceToggle(
    private val scope: CoroutineScope,
    private val notifier: Notifier,
    private val send: suspend (on: Boolean) -> Unit,
    private val failure: (e: Exception, on: Boolean) -> String,
) {

    private val _isOn = MutableStateFlow<Boolean?>(null)

    /** Keeps taps in their order on their way to the source. */
    private val sending = Mutex()

    /** Counts taps, a failed one only flips back when no tap came after it. */
    private val taps = AtomicInteger()

    /** `null` while it isn't known, or when it can't be changed, like while signed out. */
    val isOn: StateFlow<Boolean?> = _isOn.asStateFlow()

    /** Sets what the source says, once the item arrived; `null` hides the choice. */
    fun known(on: Boolean?) {
        _isOn.value = on
    }

    /** Flips the choice and sends it, nothing while it isn't known. */
    fun toggle() {
        val on = !(_isOn.value ?: return)
        _isOn.value = on
        val tap = taps.incrementAndGet()

        scope.launch {
            sending.withLock {
                try {
                    send(on)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "The source didn't take the change", e)
                    if (taps.get() == tap) _isOn.compareAndSet(on, !on)
                    notifier.post(Message(failure(e, on), MessageDuration.LONG))
                }
            }
        }
    }
}

/**
 * What to tell the user when the OpenSubsonic server didn't take a change, like "bookmark the
 * album" for [change].
 */
internal fun Exception.serverFailure(change: String): String =
    when ((this as? ServerException)?.check) {
        is ServerCheck.Rejected -> "The server refused to $change"
        is ServerCheck.Unreachable,
        is ServerCheck.UntrustedCertificate,
        is ServerCheck.CertificateChanged -> "Can't reach the server to $change"

        else -> "Couldn't $change on the server"
    }

/**
 * What to tell the user when YouTube didn't take a change, like "save the album" for [change].
 */
internal fun Exception.youTubeFailure(change: String): String {
    val e = this as? YouTubeException
    return when {
        e == null -> "Couldn't $change on YouTube"
        e.signedOut -> "Sign in to YouTube again to $change"
        e.status == null -> "Can't reach YouTube to $change"
        else -> "YouTube refused to $change"
    }
}
