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
import me.knighthat.kreate3.R
import me.knighthat.kreate3.constant.ProxyType
import me.knighthat.kreate3.preferences.Preference
import me.knighthat.kreate3.preferences.Preferences
import me.knighthat.kreate3.preferences.Secret
import me.knighthat.kreate3.preferences.collectAsStateWithLifecycle
import me.knighthat.kreate3.ui.navigation.ProxyEditor
import me.knighthat.kreate3.ui.navigation.rememberRouteResult

/**
 * The proxy every request of a source, or of the whole app, goes through, as one row: its switch
 * says whether it is used, and a tap on its texts (while on) opens [ProxyEditorScreen] with where
 * it is and how to sign in to it, saved once the page answers.
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

    val editor = rememberRouteResult<ProxyConfig, ProxyConfig>(::ProxyEditor) {
        type.update(it.type)
        host.update(it.host)
        port.update(it.port)
        username.update(it.username)
        password.update(it.password)
    }

    ProxySettings(
        source = source,
        on = on,
        proxy = ProxyConfig(kind, address, number, user, secret),
        appProxy = appProxy,
        onEnabledChange = { enabled.update(it) },
        onEdit = { editor.launch(it) },
    )
}

/**
 * [ProxySettings] with the values given.
 *
 * @param appProxy whether the app's proxy is on, which a source's own proxy overrides
 * @param onEdit called with the saved [proxy] to open its page
 */
@Composable
fun ProxySettings(
    source: String?,
    on: Boolean,
    proxy: ProxyConfig,
    appProxy: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onEdit: (ProxyConfig) -> Unit,
) {
    SettingsSection(stringResource(R.string.setting_header_proxy))
    // Saved values stay while it is off, they are used again once it is back on
    SwitchPageSetting(
        title = stringResource(R.string.setting_title_use_a_proxy),
        summary = when {
            !on && source != null && appProxy -> stringResource(
                R.string.setting_description_proxy_source_off,
                source
            )

            !on -> stringResource(R.string.setting_description_proxy_status_off)
            proxy.host.isBlank() || proxy.port == 0 ->
                stringResource(R.string.setting_description_proxy_status_incomplete)

            else -> stringResource(
                R.string.setting_description_proxy_status_on,
                // Protocol names and addresses are never translated
                "${proxy.type.label()} · ${proxy.host}:${proxy.port}"
            )
        },
        checked = on,
        onCheckedChange = onEnabledChange,
        onClick = { onEdit(proxy) },
    )
}

/** [ProxySettings] of a proxy that is off, for previews. */
@Composable
internal fun PreviewProxySettings(source: String?) = ProxySettings(
    source = source,
    on = false,
    proxy = ProxyConfig(),
    appProxy = false,
    onEnabledChange = {},
    onEdit = {},
)
