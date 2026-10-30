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

package me.knighthat.kreate3.provider.opensubsonic

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What the OpenSubsonic server last said about itself, for texts that name it ("Can't reach
 * Navidrome"). Every answer of a server updates it, in memory only: nothing is known until a
 * server answered since the app started.
 */
object OpenSubsonicServer {

    /** The address that answered last, without a trailing `/`, and what it said. */
    data class Answered(val url: String, val info: ServerInfo)

    private val _answered = MutableStateFlow<Answered?>(null)

    /** The server that answered last, `null` until one did; it may not be the active profile's. */
    val answered: StateFlow<Answered?> = _answered.asStateFlow()

    /** What the active profile's server said, `null` until it answered. */
    val info: ServerInfo?
        // The address is read only once a server answered, the settings may not be loaded yet
        get() = _answered.value?.let { infoAt(ProfileOpenSubsonicSettings.url) }

    /** Name of the active profile's server software, like `Navidrome`, see [ServerInfo.name]. */
    val name: String?
        get() = info?.name

    /** Version of the active profile's server software, `null` when it doesn't say. */
    val version: String?
        get() = info?.serverVersion

    /** What the server at [url] said, `null` while another address answered last or none did. */
    fun infoAt(url: String): ServerInfo? =
        _answered.value?.takeIf { it.url == url.normalized() }?.info

    internal fun record(url: String, info: ServerInfo) {
        _answered.value = Answered(url.normalized(), info)
    }

    private fun String.normalized() = trim().trimEnd('/')
}
