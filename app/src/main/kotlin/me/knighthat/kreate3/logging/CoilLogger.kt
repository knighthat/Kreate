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

import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import coil3.util.Logger as CoilLogger

/**
 * Sends what Coil says about image requests to the app's log, under the tag `Coil`.
 *
 * @param detailed `true` (debug builds) logs from [CoilLogger.Level.Debug] with the whole stack
 * trace of an error; `false` (release) logs warnings and errors only, one line each: the message
 * and the kind of error, no stack trace.
 */
internal class KermitCoilLogger(private val detailed: Boolean) : CoilLogger {

    override var minLevel: CoilLogger.Level =
        if (detailed) CoilLogger.Level.Debug else CoilLogger.Level.Warn

    override fun log(
        tag: String,
        level: CoilLogger.Level,
        message: String?,
        throwable: Throwable?
    ) {
        if (level < minLevel) return

        val severity = when (level) {
            CoilLogger.Level.Verbose -> Severity.Verbose
            CoilLogger.Level.Debug -> Severity.Debug
            CoilLogger.Level.Info -> Severity.Info
            CoilLogger.Level.Warn -> Severity.Warn
            CoilLogger.Level.Error -> Severity.Error
        }
        val text = buildString {
            append(tag)
            if (message != null) append(": ").append(message)
            // Short: one line, the stack trace is for debug builds
            if (!detailed && throwable != null)
                append(" (").append(throwable::class.simpleName).append(": ")
                    .append(throwable.message).append(')')
        }
        Logger.log(severity, "Coil", if (detailed) throwable else null, text)
    }
}
