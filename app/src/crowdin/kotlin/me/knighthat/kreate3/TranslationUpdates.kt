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

package me.knighthat.kreate3

import android.content.Context
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import java.util.Locale

/**
 * The foss and github builds pull no texts from Crowdin, they show the translations merged into
 * the APK. Patreon delivers texts translated after the build, see its `TranslationUpdates`.
 */
@Suppress("UNUSED_PARAMETER")
object TranslationUpdates {

    /** None besides the APK's, see `AppLanguage.available`. */
    val languages: StateFlow<List<Locale>?> = MutableStateFlow(emptyList())

    /** Never goes up, nothing is downloaded. */
    val delivered: StateFlow<Int> = MutableStateFlow(0)

    fun init(context: Context, scope: CoroutineScope, client: HttpClient, json: Json) = Unit

    fun wrap(base: Context): Context = base

    suspend fun refresh() = Unit

    fun check(context: Context) = Unit
}
