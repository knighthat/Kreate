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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import org.koin.compose.koinInject
import java.util.UUID

/**
 * What a page opened for an answer, like an activity for a result, starts from and answers, by
 * the key of the request (see [rememberRouteResult]). Kept in memory only: the key is on the back
 * stack, so an answer still reaches the opener after the activity is recreated, but the input is
 * gone after the process is, and the page then keeps what it saved itself.
 */
@Stable
class RouteResults {
    private val inputs = mutableMapOf<String, Any>()

    // Read while composing, so the opener sees an answer as soon as it is back on screen
    private val answers = mutableStateMapOf<String, Any>()

    /** Starts the request [key] from [input], dropping an answer it got before. */
    fun open(key: String, input: Any) {
        inputs[key] = input
        answers.remove(key)
    }

    /** What the request [key] started from, `null` when it isn't known (anymore). */
    @Suppress("UNCHECKED_CAST")
    fun <I : Any> input(key: String): I? = inputs[key] as? I

    /** Answers the request [key], its opener gets [answer] once it is on screen again. */
    fun answer(key: String, answer: Any) {
        inputs.remove(key)
        answers[key] = answer
    }

    internal fun peek(key: String): Any? = answers[key]

    internal fun take(key: String): Any? = answers.remove(key)
}

/** Opens the page of a [rememberRouteResult] with the value it starts from. */
@Stable
class RouteLauncher<I : Any> internal constructor(
    private val key: String,
    private val results: RouteResults,
    private val navigator: Navigator?,
    private val route: (key: String) -> Route,
) {
    /** Opens the page with [input] to start from; nothing happens where there is no navigator. */
    fun launch(input: I) {
        navigator ?: return
        results.open(key, input)
        navigator.open(route(key))
    }
}

/**
 * A page that answers, opened like an activity for a result: [RouteLauncher.launch] puts the
 * [route] made for this request's key on top with the value it starts from, and [onResult] gets
 * what the page passes to [RouteResults.answer] once this screen is back. Going back without an
 * answer calls nothing. The key survives the activity being recreated.
 */
@Composable
fun <I : Any, O : Any> rememberRouteResult(
    route: (key: String) -> Route,
    onResult: (O) -> Unit,
): RouteLauncher<I> {
    val results = koinInject<RouteResults>()
    val navigator = LocalNavigator.current
    val key = rememberSaveable { UUID.randomUUID().toString() }
    val deliver by rememberUpdatedState(onResult)

    val answer = results.peek(key)
    LaunchedEffect(answer) {
        @Suppress("UNCHECKED_CAST")
        if (answer != null) (results.take(key) as? O)?.let(deliver)
    }

    return remember(key, results, navigator, route) {
        RouteLauncher(
            key,
            results,
            navigator,
            route
        )
    }
}
