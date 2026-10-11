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

package me.knighthat.kreate.preferences

import me.knighthat.kreate.constant.ContrastLevel
import me.knighthat.kreate.constant.ThemeMode
import me.knighthat.kreate.preferences.Preferences.awaitLoaded
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Every setting of the app. Values belong to the active profile.
 *
 * Settings are created on first use. Wait for [awaitLoaded] (for example behind
 * the splash screen) before showing UI, otherwise defaults are shown first.
 *
 * Keys are stored on disk, never rename one without a step in [PROFILE_STEPS].
 */
object Preferences : KoinComponent {

    private val engine: PreferenceEngine by inject()

    val THEME_MODE by lazy { enum("theme_mode", ThemeMode.AUTOMATIC) }
    val CONTRAST_LEVEL by lazy { enum("contrast_level", ContrastLevel.NORMAL) }

    /**
     * Minutes between two scans of the device's music. The system runs periodic work
     * at most every 15 minutes, so smaller values behave like 15.
     */
    val LOCAL_SCAN_INTERVAL by lazy { int("local_scan_interval", 30) }

    /**
     * SHA-256 checksum of the Terms of Service this profile has accepted,
     * empty until it accepts.
     *
     * It is kept per profile on purpose: when the terms change, every profile
     * has to accept the new version itself, one profile accepting does not
     * cover the others.
     *
     * It is read at every launch, so it is declared eagerly instead of `by lazy`.
     */
    val TOS_CHECKSUM = string("tos_checksum", "")

    /**
     * Suspends until the active profile's settings are loaded for the first time.
     */
    suspend fun awaitLoaded() = engine.awaitLoaded()

    /**
     * Saves pending changes now, call it when the app is about to close.
     */
    suspend fun flush() = engine.flush()

    private fun int(name: String, default: Int) =
        Preference(name, default, codec<Int, Int>({ it }, { it }), engine)

    private fun string(name: String, default: String) =
        Preference(name, default, codec<String, String>({ it }, { it }), engine)

    private inline fun <reified E : Enum<E>> enum(name: String, default: E) =
        Preference(name, default, enumCodec<E>(), engine)
}
