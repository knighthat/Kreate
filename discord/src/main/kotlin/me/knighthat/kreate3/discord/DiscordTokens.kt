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

package me.knighthat.kreate3.discord

/**
 * What signs the app in to Discord as a user, from [DiscordAuth].
 *
 * @property access sent as `Bearer` with every request and to the gateway
 * @property refresh trades for new tokens once [access] runs out
 * @property expiresAt when [access] runs out, in seconds since the epoch
 */
data class DiscordTokens(
    val access: String,
    val refresh: String,
    val expiresAt: Long,
) {

    // Never the tokens themselves, they sign in as the user
    override fun toString(): String = "DiscordTokens(expiresAt=$expiresAt)"
}
