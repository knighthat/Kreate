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

package me.knighthat.kreate3.provider

import io.ktor.client.HttpClient
import okhttp3.OkHttpClient

/**
 * Makes a Ktor client set up like the app's one, on top of [OkHttpClient]s a source derived from
 * the app's, like one going through a proxy.
 */
fun interface HttpClientFactory {

    fun create(okHttp: OkHttpClient): HttpClient
}
