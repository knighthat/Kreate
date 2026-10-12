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

package me.knighthat.kreate.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.DEFAULT_ARGS_KEY
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.SAVED_STATE_REGISTRY_OWNER_KEY
import androidx.lifecycle.VIEW_MODEL_STORE_OWNER_KEY
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.savedstate.compose.LocalSavedStateRegistryOwner
import org.koin.androidx.compose.koinViewModel

/**
 * Creation extras whose `SavedStateHandle` starts with the [Route.arguments] of [route].
 * Navigation 3 doesn't hand a route to its screen's ViewModel, this does.
 */
@Composable
fun routeExtras(route: Route): CreationExtras {
    val owner = checkNotNull(LocalViewModelStoreOwner.current) { "No ViewModelStoreOwner" }
    val savedStateOwner = LocalSavedStateRegistryOwner.current

    return remember(route, owner, savedStateOwner) {
        val base = (owner as? HasDefaultViewModelProviderFactory)?.defaultViewModelCreationExtras
            ?: CreationExtras.Empty
        MutableCreationExtras(base).apply {
            set(VIEW_MODEL_STORE_OWNER_KEY, owner)
            set(SAVED_STATE_REGISTRY_OWNER_KEY, savedStateOwner)
            set(DEFAULT_ARGS_KEY, route.arguments)
        }
    }
}

/**
 * The ViewModel of the screen that shows [route]. It reads the route's values from its
 * `SavedStateHandle`, so the screen never passes them in.
 */
@Composable
inline fun <reified VM : ViewModel> routeViewModel(route: Route): VM =
    koinViewModel(key = route.toString(), extras = routeExtras(route))
