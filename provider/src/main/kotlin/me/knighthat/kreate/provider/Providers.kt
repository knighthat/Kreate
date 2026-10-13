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

package me.knighthat.kreate.provider

import me.knighthat.kreate.database.model.Origin

/**
 * Finds the [Provider] of an [Origin].
 *
 * @throws IllegalArgumentException when two providers serve the same origin
 */
class Providers(providers: List<Provider>) {

    private val byOrigin: Map<Origin, Provider> = providers.associateBy { it.origin }.also {
        require(it.size == providers.size) { "Two providers serve the same origin" }
    }

    /**
     * @throws NoSuchElementException when no provider serves [origin], which means a provider is missing from [providerModule]
     */
    operator fun get(origin: Origin): Provider = byOrigin.getValue(origin)
}
