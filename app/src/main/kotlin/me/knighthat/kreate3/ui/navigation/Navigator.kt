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

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf

/**
 * Opens screens from places that don't own the back stack, like the menu of a song in a list.
 */
@Immutable
class Navigator(
    /** The screen on top of the back stack, `null` when it isn't a [Route]. */
    val current: Route?,
    /** Puts [Route] on top of the back stack. */
    val open: (Route) -> Unit,
)

/** The app's [Navigator], `null` where there is none, like in previews. */
val LocalNavigator = compositionLocalOf<Navigator?> { null }
