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

package me.knighthat.kreate.provider.opensubsonic

/**
 * What a request to the server found out, see [OpenSubsonicProvider.ping] and
 * [OpenSubsonicProvider.checkCredentials].
 */
sealed interface ServerCheck {

    /** The server answered as it should. */
    data class Success(val server: ServerInfo) : ServerCheck

    /** The setting is off, so the server was not contacted. */
    data object Disabled : ServerCheck

    /** The address, or the credentials of the chosen sign in method, are missing. */
    data object NotConfigured : ServerCheck

    /** Nothing answered: the address is wrong, the server is down or there is no network. */
    data class Unreachable(val reason: String) : ServerCheck

    /**
     * The server's certificate is not trusted. Turning on self-signed certificates helps when
     * the server uses its own.
     */
    data class UntrustedCertificate(val reason: String) : ServerCheck

    /**
     * The server presented a self-signed certificate other than the one trusted for it, and was
     * refused. [OpenSubsonicProvider.trust] trusts the new one.
     *
     * @property fingerprint SHA-256 of the new certificate
     * @property pinnedFingerprint SHA-256 of the certificate trusted until now
     */
    data class CertificateChanged(
        val host: String,
        val fingerprint: String,
        val pinnedFingerprint: String,
    ) : ServerCheck

    /** Something answered but it is not a Subsonic server, like a wrong address or a login page. */
    data object NotSubsonic : ServerCheck

    /**
     * The server refused the sign in.
     *
     * @property code error code of the Subsonic API, `40` is a wrong username or password
     * and `44` an invalid API key
     */
    data class Rejected(val code: Int, val message: String) : ServerCheck
}

/**
 * What the server said about itself.
 *
 * @property apiVersion version of the Subsonic API it speaks
 * @property type name of the server software, like `navidrome`, `null` when it doesn't say
 * @property serverVersion version of the server software, `null` when it doesn't say
 * @property isOpenSubsonic whether it supports the OpenSubsonic extensions
 */
data class ServerInfo(
    val apiVersion: String,
    val type: String?,
    val serverVersion: String?,
    val isOpenSubsonic: Boolean,
)
