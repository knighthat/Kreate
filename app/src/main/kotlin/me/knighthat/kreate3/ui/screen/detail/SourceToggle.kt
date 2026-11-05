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

import android.content.res.Resources
import androidx.annotation.StringRes
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.knighthat.kreate3.R
import me.knighthat.kreate3.notification.Message
import me.knighthat.kreate3.notification.MessageDuration
import me.knighthat.kreate3.notification.Notifier
import me.knighthat.kreate3.provider.opensubsonic.ServerCheck
import me.knighthat.kreate3.provider.opensubsonic.ServerException
import me.knighthat.kreate3.provider.youtube.YouTubeException
import me.knighthat.kreate3.ui.component.openSubsonicName
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
                    Logger.w(e, TAG) { "The source didn't take the change" }
                    if (taps.get() == tap) _isOn.compareAndSet(on, !on)
                    notifier.post(Message(failure(e, on), MessageDuration.LONG))
                }
            }
        }
    }
}

/** [name] in quotes, or [fallback], like "this album", while the name isn't known. */
internal fun Resources.itemName(name: String?, @StringRes fallback: Int): String =
    name?.let { getString(R.string.quoted, it) } ?: getString(fallback)

/**
 * [withName] filled with [args] and the quoted [name], or [without] filled with [args] alone while
 * the name isn't known.
 */
internal fun Resources.named(
    name: String?,
    @StringRes withName: Int,
    @StringRes without: Int,
    vararg args: Any,
): String {
    val known = name?.takeIf { it.isNotBlank() } ?: return getString(without, *args)
    return getString(withName, *args, getString(R.string.quoted, known))
}

/**
 * What to tell the user when the OpenSubsonic server didn't take a change to [item], see
 * [itemName]. The change is already reverted.
 */
internal fun Exception.serverFailure(resources: Resources, item: String): String {
    val server = resources.openSubsonicName()
    val reason = when ((this as? ServerException)?.check) {
        is ServerCheck.Rejected -> resources.getString(R.string.error_reason_refused, server)
        is ServerCheck.Unreachable -> resources.getString(R.string.error_reason_unreachable, server)
        is ServerCheck.UntrustedCertificate ->
            resources.getString(R.string.error_server_certificate_untrusted, server)

        is ServerCheck.CertificateChanged ->
            resources.getString(R.string.error_reason_certificate_changed, server)

        else -> resources.getString(R.string.error_reason_unknown)
    }
    return resources.getString(R.string.error_sync_failed, item, reason)
}

/**
 * What to tell the user when YouTube didn't take a change to [item], see [itemName]. The change
 * is already reverted.
 */
internal fun Exception.youTubeFailure(resources: Resources, item: String): String {
    val e = this as? YouTubeException
    val youTube = resources.getString(R.string.source_ytm)
    val reason = when {
        e == null -> resources.getString(R.string.error_reason_unknown)
        e.signedOut -> resources.getString(R.string.error_reason_signed_out, youTube)
        e.status == null -> resources.getString(R.string.error_reason_unreachable, youTube)
        else -> resources.getString(R.string.error_reason_refused, youTube)
    }
    return resources.getString(R.string.error_sync_failed, item, reason)
}
