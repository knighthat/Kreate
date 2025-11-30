package me.knighthat.kreate.constant

import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import kotlinx.serialization.Serializable


sealed class Route {

    fun isHere( navController: NavController ): Boolean {
        val hierarchy = navController.currentBackStackEntry?.destination?.hierarchy
        return hierarchy?.any { it.hasRoute( this::class ) } == true
    }

    @Serializable
    object Home: Route()

    @Serializable
    object Library: Route()
}