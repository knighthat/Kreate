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

import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.LoggingConfig
import co.touchlab.kermit.Logger as AppLogger

/**
 * Logs the requests of the app's HTTP clients under the tag `Network`.
 *
 * - Debug builds log the address, the headers and the status of each request and answer, at the
 *   debug level. Bodies are never logged: sign-ins carry passwords in them.
 * - Release builds log one line per request and per answer, the address and the status, at the
 *   info level.
 *
 * Headers that identify the user ([isSecretHeader]) are never printed, and every line goes through
 * [redact] like the rest of the log.
 */
internal fun LoggingConfig.appLogging(debug: Boolean) {
    logger = object : Logger {
        override fun log(message: String) {
            if (debug)
                AppLogger.d(tag = "Network") { message }
            else
                AppLogger.i(tag = "Network") { message }
        }
    }
    level = if (debug) LogLevel.HEADERS else LogLevel.INFO
    sanitizeHeader { isSecretHeader(it) }
}
