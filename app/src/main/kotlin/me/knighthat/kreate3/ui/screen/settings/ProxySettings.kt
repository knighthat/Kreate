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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import me.knighthat.kreate3.R
import me.knighthat.kreate3.constant.ProxyType
import me.knighthat.kreate3.preferences.Preference
import me.knighthat.kreate3.preferences.Preferences
import me.knighthat.kreate3.preferences.Secret
import me.knighthat.kreate3.preferences.collectAsStateWithLifecycle

private fun ProxyType.label(): String = when (this) {
    ProxyType.HTTP -> "HTTP"
    ProxyType.SOCKS -> "SOCKS5"
}

/**
 * A host name or an IP address, without a scheme, a port or a path. An empty one is accepted,
 * it clears the host.
 *
 * @return a check that gives the reason the host is not acceptable, `null` when it is
 */
@Composable
private fun hostValidator(): (String) -> String? {
    val scheme = stringResource(R.string.setting_description_host_scheme)
    val hostOnly = stringResource(R.string.setting_description_host_only)
    return { host ->
        when {
            "://" in host -> scheme
            host.any { it.isWhitespace() || it == '/' } -> hostOnly
            else -> null
        }
    }
}

@Composable
private fun portValidator(): (String) -> String? {
    val range = stringResource(R.string.setting_description_port_range)
    return { port -> if (port.isEmpty() || port.toIntOrNull() in 1..65535) null else range }
}

/**
 * The proxy every request of a source, or of the whole app, goes through: whether it is used,
 * its kind, where it is and how to sign in to it. Only the switch shows while it is off.
 *
 * @param source what the requests go to, like "YouTube", `null` for the app's proxy, which a
 * source's own proxy overrides
 */
@Composable
fun ProxySettings(
    source: String?,
    enabled: Preference<Boolean>,
    type: Preference<ProxyType>,
    host: Preference<String>,
    port: Preference<Int>,
    username: Secret,
    password: Secret,
) {
    val on by enabled.collectAsStateWithLifecycle()
    val kind by type.collectAsStateWithLifecycle()
    val address by host.collectAsStateWithLifecycle()
    val number by port.collectAsStateWithLifecycle()
    val user by username.collectAsStateWithLifecycle()
    val secret by password.collectAsStateWithLifecycle()
    val appProxy by Preferences.NETWORK_PROXY_ENABLED.collectAsStateWithLifecycle()

    val validateHost = hostValidator()
    val validatePort = portValidator()

    val incomplete = on && (address.isBlank() || number == 0)
    SettingsSection(stringResource(R.string.setting_header_proxy))
    SwitchSetting(
        title = stringResource(R.string.setting_title_use_a_proxy),
        summary = when {
            source == null && incomplete -> stringResource(R.string.setting_description_proxy_app_incomplete)

            source == null -> stringResource(R.string.setting_description_proxy_app)
            incomplete -> stringResource(
                R.string.setting_description_proxy_source_incomplete,
                source
            )

            !on && appProxy -> stringResource(R.string.setting_description_proxy_source_off, source)
            else -> stringResource(R.string.setting_description_proxy_source, source)
        },
        checked = on,
        onCheckedChange = enabled::update,
    )
    // Saved values stay when hidden, they are used again once the setting is back on
    if (!on) return

    ChoiceSetting(
        title = stringResource(R.string.setting_title_type),
        value = kind,
        options = ProxyType.entries,
        label = ProxyType::label,
        onSelect = type::update,
    )
    TextSetting(
        title = stringResource(R.string.setting_title_host),
        value = address,
        placeholder = "proxy.example.com",
        keyboardType = KeyboardType.Uri,
        validate = validateHost,
        onSave = { host.update(it.trim()) },
    )
    TextSetting(
        title = stringResource(R.string.setting_title_port),
        value = if (number == 0) "" else number.toString(),
        keyboardType = KeyboardType.Number,
        validate = { validatePort(it.trim()) },
        onSave = { port.update(it.trim().toIntOrNull() ?: 0) },
    )
    TextSetting(
        title = stringResource(R.string.setting_title_username),
        value = user,
        summary = user.ifEmpty { stringResource(R.string.setting_description_username_status_none) },
        onSave = { username.update(it.trim()) },
    )
    if (user.isNotEmpty())
        TextSetting(
            title = stringResource(R.string.setting_title_password),
            value = secret,
            summary = if (secret.isEmpty()) "" else "•".repeat(8),
            hidden = true,
            onSave = password::update,
        )
}
