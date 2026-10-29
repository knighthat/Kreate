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

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.annotation.RequiresApi
import androidx.core.content.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import me.knighthat.kreate3.AppLanguage.FILE
import me.knighthat.kreate3.AppLanguage.displayName
import me.knighthat.kreate3.preferences.Preferences
import me.knighthat.kreate3.preferences.ProfileManager
import java.text.Collator
import java.util.Locale

/**
 * The language of the app's texts. Each profile picks its own in [Preferences.LANGUAGE] (empty
 * follows the device), which goes with the profile into backups.
 *
 * What the app shows is kept apart from it, since it's needed before the settings load. Android
 * 13+ keeps it itself (its app settings can change it too, from the locale config the build
 * generates). Older versions read it from [FILE] in [wrap], when every context is made. [FILE]
 * also holds the language last shown, so [follow] can tell a change made in the system's
 * settings from a profile's own language.
 */
object AppLanguage {

    private const val FILE = "app_language"
    private const val KEY_TAG = "tag"

    /**
     * Languages the APK has texts in: English and every translation Crowdin merged into `res/`
     * before the build, see `crowdin.yml` and `app/build.gradle.kts`. Sorted by [displayName].
     */
    val available: List<Locale> by lazy {
        val locales = BuildConfig.LANGUAGES.split(',').map(Locale::forLanguageTag)
        val collator = Collator.getInstance(Locale.ROOT)
        locales.sortedWith(compareBy(collator) { displayName(it, locales) })
    }

    /**
     * One of [available] for a language [tag], `null` for an empty one (the device's) or a
     * language this build has no texts in. `vi-VN` is `vi` when only that one is there.
     */
    fun of(tag: String): Locale? {
        if (tag.isEmpty()) return null
        val locale = Locale.forLanguageTag(tag)
        return available.firstOrNull { it == locale }
            ?: available.firstOrNull { it.language == locale.language }
    }

    /**
     * Picks [locale] for the active profile, `null` for the device's language, and shows it.
     * Older versions than Android 13 restart the app once playback stopped, the pick is saved
     * first.
     */
    fun select(context: Context, scope: CoroutineScope, locale: Locale?) {
        val tag = locale?.toLanguageTag().orEmpty()
        Preferences.LANGUAGE.update(tag)
        show(context, scope, tag)
    }

    /**
     * Shows the language of each profile once its settings load: at start (a restored backup
     * too) and after a switch. A language picked in the system's settings since the last time
     * goes to the profile instead.
     */
    fun follow(context: Context, scope: CoroutineScope, profiles: ProfileManager) =
        scope.launch {
            profiles.activeId.filterNotNull().collectLatest { profileId ->
                Preferences.awaitProfile(profileId)

                val shown = shown(context)
                if (Build.VERSION.SDK_INT >= 33 && system(context) != shown) {
                    takeFromSystem(context)
                    return@collectLatest
                }
                val picked = of(Preferences.LANGUAGE.value)?.toLanguageTag().orEmpty()
                if (picked != shown) show(context, scope, picked)
            }
        }

    /**
     * Gives the active profile the language picked in the system's settings, called when the
     * configuration changes. Android 13+ only, older versions have no such setting.
     */
    fun takeFromSystem(context: Context) {
        if (Build.VERSION.SDK_INT < 33) return
        val tag = system(context)
        if (tag == shown(context)) return

        remember(context, tag)
        Preferences.LANGUAGE.update(tag)
    }

    /**
     * [base] in the shown language, for `attachBaseContext` of the application, activities and
     * services. Android 13+ does this itself, [base] is returned as is there.
     */
    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= 33) return base
        val tag = shown(base).ifEmpty { return base }
        val locale = Locale.forLanguageTag(tag)

        // Dates and numbers formatted without a context follow it too
        Locale.setDefault(locale)
        val configuration = Configuration(base.resources.configuration)
        configuration.setLocale(locale)
        return base.createConfigurationContext(configuration)
    }

    /**
     * The name of [locale] in its own language ("Tiếng Việt"). The country or script is added
     * only when another of [available] shares the language (Português (Brasil) and (Portugal)).
     */
    fun displayName(locale: Locale): String = displayName(locale, available)

    private fun displayName(locale: Locale, locales: List<Locale>): String {
        val shared = locales.count { it.language == locale.language } > 1
        val name =
            if (shared) locale.getDisplayName(locale) else locale.getDisplayLanguage(locale)
        return name.replaceFirstChar { it.titlecase(locale) }
    }

    private fun show(context: Context, scope: CoroutineScope, tag: String) {
        remember(context, tag)
        if (Build.VERSION.SDK_INT >= 33) {
            context.getSystemService(LocaleManager::class.java).applicationLocales =
                if (tag.isEmpty()) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(
                    tag
                )
            return
        }

        // Every context took its language when it was made
        scope.launch {
            Preferences.flush()
            (context.applicationContext as KreateApplication).restartAfterPlayback()
        }
    }

    /** The tag of the language shown, empty for the device's. */
    private fun shown(context: Context): String =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY_TAG, null).orEmpty()

    // Written at once, a restart can end the process right after
    private fun remember(context: Context, tag: String) =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit(commit = true) {
            if (tag.isEmpty()) remove(KEY_TAG) else putString(KEY_TAG, tag)
        }

    /** The system's language for the app as one of [available], empty for the device's. */
    @RequiresApi(33)
    private fun system(context: Context): String {
        val locales = context.getSystemService(LocaleManager::class.java).applicationLocales
        return if (locales.isEmpty) "" else of(locales[0].toLanguageTag())?.toLanguageTag()
            .orEmpty()
    }
}
