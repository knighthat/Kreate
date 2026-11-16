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

package me.knighthat.kreate3.ui.screen.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.knighthat.kreate3.AppLanguage
import me.knighthat.kreate3.R
import me.knighthat.kreate3.ServiceSignOut
import me.knighthat.kreate3.font.FontFiles
import me.knighthat.kreate3.font.FontSlot
import me.knighthat.kreate3.notification.Notifier
import me.knighthat.kreate3.preferences.Preferences
import me.knighthat.kreate3.preferences.ProfileManager
import me.knighthat.kreate3.preferences.Secrets
import org.koin.core.component.KoinComponent
import kotlin.coroutines.cancellation.CancellationException

/**
 * Puts the active profile's settings back to their defaults, see [Preferences.reset], and signs
 * it out of everything ([Secrets.clearAll]).
 *
 * @param scope the app's global scope, a reset goes on when the page closes
 */
class SettingsViewModel(
    private val context: Context,
    private val fonts: FontFiles,
    private val profiles: ProfileManager,
    private val notifier: Notifier,
    private val scope: CoroutineScope,
) : ViewModel(), KoinComponent {

    /**
     * Signs out of every service (those to tell first through [ServiceSignOut]) and deletes the
     * profile's secrets, resets the settings, deletes the fonts the profile picked and shows the
     * device's language when the profile had one of its own. A failure is reported like any
     * failed save and stops there.
     */
    fun reset() = scope.launch {
        val profile = checkNotNull(profiles.activeId.first { it != null })
        val language = Preferences.LANGUAGE.value

        getKoin().getAll<ServiceSignOut>().forEach {
            try {
                it.signOut()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.w(e, TAG) { "Couldn't sign out before a reset" }
            }
        }
        if (!Secrets.clearAll()) return@launch
        if (!Preferences.reset()) return@launch

        try {
            withContext(Dispatchers.IO) { FontSlot.entries.forEach { fonts.forget(profile, it) } }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.w(e, TAG) { "Couldn't delete the fonts after a reset" }
        }
        notifier.post(context.getString(R.string.snackbar_settings_reset))

        // Older Android versions restart the app for it
        if (language.isNotEmpty())
            withContext(Dispatchers.Main) { AppLanguage.select(context, scope, null) }
    }

    private companion object {
        const val TAG = "SettingsReset"
    }
}
