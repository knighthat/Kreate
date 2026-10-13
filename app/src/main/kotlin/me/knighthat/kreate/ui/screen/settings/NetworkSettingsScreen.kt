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

package me.knighthat.kreate.ui.screen.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import me.knighthat.kreate.constant.OpenSubsonicAuth
import me.knighthat.kreate.preferences.Preferences
import me.knighthat.kreate.preferences.Secrets
import me.knighthat.kreate.preferences.collectAsStateWithLifecycle

private const val NEXT_LAUNCH = "Applies from the next launch"

/**
 * Only an address starting with `http://` or `https://` and naming a host is accepted.
 * An empty one is, it clears the server.
 *
 * @return the reason the address is not acceptable, `null` when it is
 */
private fun validateServerUrl(url: String): String? = when {
    url.isEmpty() -> null
    !url.startsWith("https://") && !url.startsWith("http://") -> "Start with https:// or http://"
    url.substringAfter("://").isBlank() -> "Add the host of the server"
    else -> null
}

/**
 * How requests are made, and the OpenSubsonic server (like Navidrome) to play music from.
 */
@Composable
fun NetworkSettingsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val connectTimeout by Preferences.NETWORK_CONNECT_TIMEOUT.collectAsStateWithLifecycle()
    val readTimeout by Preferences.NETWORK_READ_TIMEOUT.collectAsStateWithLifecycle()
    val maxRetries by Preferences.NETWORK_MAX_RETRIES.collectAsStateWithLifecycle()
    val enabled by Preferences.OPEN_SUBSONIC_ENABLED.collectAsStateWithLifecycle()
    val auth by Preferences.OPEN_SUBSONIC_AUTH.collectAsStateWithLifecycle()
    val url by Preferences.OPEN_SUBSONIC_URL.collectAsStateWithLifecycle()
    val username by Preferences.OPEN_SUBSONIC_USERNAME.collectAsStateWithLifecycle()
    val password by Secrets.OPEN_SUBSONIC_PASSWORD.collectAsStateWithLifecycle()
    val apiKey by Secrets.OPEN_SUBSONIC_API_KEY.collectAsStateWithLifecycle()

    SettingsPage("Network", onBack, modifier) {
        SettingsSection("Requests")
        NumberSetting(
            title = "Connect timeout",
            value = connectTimeout.toLong(),
            unit = "seconds",
            min = 1,
            max = 120,
            onSave = { Preferences.NETWORK_CONNECT_TIMEOUT.update(it.toInt()) },
            note = NEXT_LAUNCH,
        )
        NumberSetting(
            title = "Read timeout",
            value = readTimeout.toLong(),
            unit = "seconds",
            min = 1,
            max = 300,
            onSave = { Preferences.NETWORK_READ_TIMEOUT.update(it.toInt()) },
            note = NEXT_LAUNCH,
        )
        NumberSetting(
            title = "Retries",
            value = maxRetries.toLong(),
            unit = "times",
            min = 0,
            max = 10,
            onSave = { Preferences.NETWORK_MAX_RETRIES.update(it.toInt()) },
            note = "Requests that got a server error are sent again. $NEXT_LAUNCH",
        )

        SettingsSection("OpenSubsonic")
        SwitchSetting(
            title = "Use OpenSubsonic",
            summary = "The server is not contacted while this is off",
            checked = enabled,
            onCheckedChange = Preferences.OPEN_SUBSONIC_ENABLED::update,
        )

        // Saved values stay when hidden, they are used again once the setting is back on
        if (!enabled) return@SettingsPage

        TextSetting(
            title = "Server address",
            value = url,
            placeholder = "https://music.example.com",
            keyboardType = KeyboardType.Uri,
            validate = ::validateServerUrl,
            onSave = { Preferences.OPEN_SUBSONIC_URL.update(it.trim().trimEnd('/')) },
        )

        SettingsSection("Sign in with")
        RadioSetting(
            value = auth,
            options = OpenSubsonicAuth.entries,
            label = { if (it == OpenSubsonicAuth.PASSWORD) "Username and password" else "API key" },
            onSelect = Preferences.OPEN_SUBSONIC_AUTH::update,
        )
        when (auth) {
            OpenSubsonicAuth.PASSWORD -> {
                TextSetting(
                    title = "Username",
                    value = username,
                    onSave = { Preferences.OPEN_SUBSONIC_USERNAME.update(it.trim()) },
                )
                TextSetting(
                    title = "Password",
                    value = password,
                    summary = if (password.isEmpty()) "" else "•".repeat(8),
                    hidden = true,
                    onSave = Secrets.OPEN_SUBSONIC_PASSWORD::update,
                )
            }

            OpenSubsonicAuth.API_KEY -> TextSetting(
                title = "API key",
                value = apiKey,
                summary = if (apiKey.isEmpty()) "" else "•".repeat(8),
                hidden = true,
                onSave = Secrets.OPEN_SUBSONIC_API_KEY::update,
            )
        }
    }
}
