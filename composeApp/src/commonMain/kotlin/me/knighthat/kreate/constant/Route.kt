package me.knighthat.kreate.constant

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.currentBackStackEntryAsState
import kotlinx.serialization.Serializable
import me.knighthat.kreate.util.LocalNavController
import org.jetbrains.annotations.Contract
import kotlin.reflect.KClass


sealed class Route {

    companion object {

        /**
         * Verifies whether [clazz] is currently at the top of [navBackStackEntry].
         *
         * This method only verifies the top of the stack (current navigation) and
         * doesn't not verify whether the [clazz] exists in the stack.
         *
         * If [navBackStackEntry] is `null` then the result defaults to `false`
         */
        @Contract("_,null->false")
        fun <T: Route> isHere( clazz: KClass<T>, navBackStackEntry: NavBackStackEntry? ): Boolean =
            navBackStackEntry?.destination?.hierarchy?.firstOrNull()?.hasRoute( clazz ) == true

        /**
         * Verifies whether [T] is currently at the top of [navController]'s [NavBackStackEntry].
         *
         * This method only verifies the top of the stack (current navigation) and
         * doesn't not verify whether the [T] exists in the stack.
         */
        inline fun <reified T: Route> isHere( navController: NavController ): Boolean =
            isHere( T::class, navController.currentBackStackEntry )
    }

    val isHere: Boolean
        @Composable
        get() {
            val navBackStackEntry by LocalNavController.current.currentBackStackEntryAsState()
            return remember( navBackStackEntry ) {
                isHere( this::class, navBackStackEntry )
            }
        }
    val isNotHere: Boolean
        @Composable
        get() = !isHere

    fun isHere( navController: NavController ): Boolean =
        isHere( this::class, navController.currentBackStackEntry )

    @Serializable
    object Home: Route()

    @Serializable
    object Library: Route()

    @Serializable
    object Search: Route() {

        @Serializable
        data class Results( val input: String ): Route()
    }
}