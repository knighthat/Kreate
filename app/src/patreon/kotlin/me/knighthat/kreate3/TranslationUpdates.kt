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
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.core.content.edit
import com.crowdin.platform.Crowdin
import com.crowdin.platform.CrowdinConfig
import me.knighthat.kreate3.TranslationUpdates.FILE
import me.knighthat.kreate3.TranslationUpdates.HASH
import me.knighthat.kreate3.TranslationUpdates.INTERVAL
import kotlin.time.Duration.Companion.hours

/**
 * Texts translated on Crowdin after the build, delivered by the Crowdin SDK from the distribution
 * [HASH] over the translations merged into the APK. A text the distribution doesn't have (it
 * skips untranslated ones) shows the APK's.
 *
 * The SDK's own check at start is off. [check] asks for new texts of the shown language at most
 * every [INTERVAL], and only while it isn't English, the source the APK has. Each language keeps
 * its own time in [FILE], so switching back and forth doesn't ask again; a language not checked
 * before, or not since the app was updated, is checked at once, so texts kept from before don't
 * stay over newer ones of the APK for long. New texts show from the next screen made: activities
 * [wrap]ped get them, the application and services keep the APK's texts, since their resources
 * would otherwise miss later configuration changes.
 *
 * Only in the patreon build, foss and github have a stand-in (src/crowdin) that keeps the APK's
 * texts.
 */
object TranslationUpdates {

    // Each language's tag to when its texts were last asked for, and the app version they are for
    private const val FILE = "translation_checks"
    private const val KEY_VERSION = "version_code"

    // Crowdin's distribution of the "Android values" bundle, every values-*/ text in one file
    private const val HASH = "02072db4999a42ff831759a4v12"

    private val INTERVAL = 48.hours

    /** Starts the SDK with the texts it kept, from `onCreate` of the application. */
    fun init(context: Context) {
        Crowdin.init(
            context,
            CrowdinConfig.Builder()
                .withDistributionHash(HASH)
                // check() decides when
                .withInitSyncDisabled()
                .build()
        )
        check(context)
    }

    /** [base] showing the texts delivered, for `attachBaseContext` of activities. */
    fun wrap(base: Context): Context = Crowdin.wrapContext(base)

    /**
     * Asks for new texts in the language the app shows when it isn't English and [INTERVAL]
     * passed since that language was last checked, or it never was in this version of the app.
     * Called at start and on configuration changes, a switch of language. Without a connection,
     * the next call tries again.
     */
    fun check(context: Context) {
        val locale = context.resources.configuration.locales[0]
        if (locale.language == "en") return

        val preferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        // An update brings newer merged texts, every language is checked again
        if (preferences.getInt(KEY_VERSION, 0) != BuildConfig.VERSION_CODE) {
            preferences.edit(commit = true) {
                clear()
                putInt(KEY_VERSION, BuildConfig.VERSION_CODE)
            }
        }

        val tag = locale.toLanguageTag()
        val checkedAt = preferences.getLong(tag, 0)
        val now = System.currentTimeMillis()
        // A clock set back counts as passed
        val due = checkedAt == 0L || now - checkedAt !in 0..<INTERVAL.inWholeMilliseconds
        if (!due || !isOnline(context)) return

        // Before the request, a configuration change meanwhile doesn't start another
        preferences.edit { putLong(tag, now) }
        // The SDK downloads on its own threads, in the language of the context's configuration
        Crowdin.forceUpdate(context)
    }

    private fun isOnline(context: Context): Boolean {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val network = connectivity?.activeNetwork ?: return false
        return connectivity.getNetworkCapabilities(network)
            ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }
}
