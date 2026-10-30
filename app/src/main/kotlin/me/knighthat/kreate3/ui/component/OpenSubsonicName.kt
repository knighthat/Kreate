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

package me.knighthat.kreate3.ui.component

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.knighthat.kreate3.R
import me.knighthat.kreate3.preferences.Preferences
import me.knighthat.kreate3.provider.opensubsonic.OpenSubsonicServer
import me.knighthat.kreate3.preferences.collectAsStateWithLifecycle as collectPreference

/**
 * What texts call the OpenSubsonic server: the name of its software, like "Navidrome", or the
 * source's name until the active profile's server said it.
 */
internal fun Resources.openSubsonicName(): String =
    OpenSubsonicServer.name ?: getString(R.string.source_opensubsonicapi)

/**
 * [openSubsonicName] that follows the server's answers and the profile's address. It reads
 * [Preferences], so a screen calls it and gives the result to its content.
 */
@Composable
internal fun openSubsonicName(): String {
    val answered by OpenSubsonicServer.answered.collectAsStateWithLifecycle()
    val url by Preferences.OPEN_SUBSONIC_URL.collectPreference()
    return answered?.let { OpenSubsonicServer.infoAt(url)?.name }
        ?: stringResource(R.string.source_opensubsonicapi)
}
