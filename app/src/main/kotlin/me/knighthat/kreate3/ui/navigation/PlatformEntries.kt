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

package me.knighthat.kreate3.ui.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey

/**
 * Screens only one platform has (the patreon build's Patreon page), added to the app's entries by
 * `KreateApp`. A platform's `platformModule` declares one with Koin; the others declare none, so
 * `main` never names those screens.
 */
fun interface PlatformEntries {

    /** Adds this platform's entries; [onBack] leaves the screen. */
    fun EntryProviderScope<NavKey>.add(onBack: () -> Unit)
}
