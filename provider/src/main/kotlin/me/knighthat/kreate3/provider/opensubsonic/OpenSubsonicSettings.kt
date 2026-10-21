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

import me.knighthat.kreate3.constant.OpenSubsonicAuth
import me.knighthat.kreate3.preferences.Preferences
import me.knighthat.kreate3.preferences.Secrets
import me.knighthat.kreate3.provider.SourceProxy
import me.knighthat.kreate3.provider.sourceProxy

/**
 * What the user set up for the server, read at the time of each request so a change applies at once.
 */
interface OpenSubsonicSettings {

    /** Nothing may contact the server while this is `false`, whatever else is set. */
    val enabled: Boolean

    /** Address without `/rest`, empty when not set. */
    val url: String

    val auth: OpenSubsonicAuth

    /** Waits for the saved username to be read, see [Secrets]. */
    suspend fun username(): String

    /** Waits for the saved password to be read, see [Secrets]. */
    suspend fun password(): String

    /** Waits for the saved API key to be read, see [Secrets]. */
    suspend fun apiKey(): String

    /** Whether a server with a self-signed certificate may be trusted, see [CertificatePin]. */
    val allowSelfSigned: Boolean

    /** The self-signed certificate trusted for the server, `null` when none is. */
    val pinnedCertificate: CertificatePin?

    /** Trusts [pin] from now on, `null` forgets the trusted certificate. */
    fun pin(pin: CertificatePin?)

    /** The proxy every request to the server goes through, waits for its sign in to be read. */
    suspend fun proxy(): SourceProxy = SourceProxy.Off
}

/**
 * The settings of the active profile.
 */
internal object ProfileOpenSubsonicSettings : OpenSubsonicSettings {

    override val enabled get() = Preferences.OPEN_SUBSONIC_ENABLED.value
    override val url get() = Preferences.OPEN_SUBSONIC_URL.value
    override val auth get() = Preferences.OPEN_SUBSONIC_AUTH.value
    override suspend fun username() = Secrets.OPEN_SUBSONIC_USERNAME.current()
    override suspend fun password() = Secrets.OPEN_SUBSONIC_PASSWORD.current()
    override suspend fun apiKey() = Secrets.OPEN_SUBSONIC_API_KEY.current()
    override val allowSelfSigned get() = Preferences.OPEN_SUBSONIC_ALLOW_SELF_SIGNED.value

    override val pinnedCertificate: CertificatePin?
        get() {
            val host = Preferences.OPEN_SUBSONIC_CERTIFICATE_HOST.value
            val fingerprint = Preferences.OPEN_SUBSONIC_CERTIFICATE_SHA256.value
            return if (host.isEmpty() || fingerprint.isEmpty()) null else CertificatePin(
                host,
                fingerprint
            )
        }

    override fun pin(pin: CertificatePin?) {
        Preferences.OPEN_SUBSONIC_CERTIFICATE_HOST.update(pin?.host.orEmpty())
        Preferences.OPEN_SUBSONIC_CERTIFICATE_SHA256.update(pin?.fingerprint.orEmpty())
    }

    override suspend fun proxy() = sourceProxy(
        Preferences.OPEN_SUBSONIC_PROXY_ENABLED,
        Preferences.OPEN_SUBSONIC_PROXY_TYPE,
        Preferences.OPEN_SUBSONIC_PROXY_HOST,
        Preferences.OPEN_SUBSONIC_PROXY_PORT,
        Secrets.OPEN_SUBSONIC_PROXY_USERNAME,
        Secrets.OPEN_SUBSONIC_PROXY_PASSWORD,
    )
}
