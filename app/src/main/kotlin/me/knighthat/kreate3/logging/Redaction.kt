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

package me.knighthat.kreate3.logging

private val SECRET_PARAMETER = Regex(
    "([?&;](?:u|p|t|s|pass|password|passwd|token|access_token|refresh_token|id_token|apikey|api_key|" +
            "key|sig|signature|pot|po_token|auth|authorization|session|sid|cookie|secret|" +
            "visitordata|visitor_data|username|user)=)[^&#\\s\"'<>]*",
    RegexOption.IGNORE_CASE,
)

/** `user:password@` in front of a host. */
private val ADDRESS_USER_INFO = Regex("(://)[^/@\\s]+@")

/** Headers that carry who the user is, as printed in `name: value` lines. */
private val SECRET_HEADER = Regex(
    "(?im)^(\\W*(?:authorization|proxy-authorization|cookie|set-cookie|x-goog-[\\w-]+|" +
            "x-youtube-[\\w-]+|x-origin|x-emby-token|x-api-key)\\s*:).*$",
)

/**
 * Everything that reaches a log goes through this first, so a message written carelessly still
 * doesn't leak a credential.
 *
 * [text] with the values of credentials, tokens and cookies replaced by `<redacted>`: query
 * parameters named like one, the user info of an address and the headers that hold one.
 */
internal fun redact(text: String): String {
    if (text.isEmpty()) return text
    return text
        .replace(SECRET_PARAMETER, "$1<redacted>")
        .replace(ADDRESS_USER_INFO, "$1<redacted>@")
        .replace(SECRET_HEADER, "$1 <redacted>")
}

/** Header names whose values are never printed by the network logging. */
internal fun isSecretHeader(name: String): Boolean {
    val lower = name.lowercase()
    return lower in SECRET_HEADERS || lower.startsWith("x-goog-") || lower.startsWith("x-youtube-")
}

private val SECRET_HEADERS = setOf(
    "authorization", "proxy-authorization", "cookie", "set-cookie", "x-origin", "x-emby-token",
    "x-api-key",
)
