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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import me.knighthat.kreate3.R
import me.knighthat.kreate3.constant.ProxyType
import me.knighthat.kreate3.ui.component.PreviewTheme
import me.knighthat.kreate3.ui.component.ScreenPreviews
import me.knighthat.kreate3.ui.navigation.RouteResults
import me.knighthat.kreate3.ui.screen.detail.DetailScaffold
import org.koin.compose.koinInject

/** Width of the port field, room for 5 digits beside the host. */
private val PORT_WIDTH = 112.dp

/**
 * Where a proxy is and how to sign in to it, what [ProxyEditorScreen] starts from and answers.
 *
 * @param port `0` while there is none
 * @param password empty whenever [username] is
 */
data class ProxyConfig(
    val type: ProxyType = ProxyType.HTTP,
    val host: String = "",
    val port: Int = 0,
    val username: String = "",
    val password: String = "",
)

internal fun ProxyType.label(): String = when (this) {
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
 * The page of a proxy opened for an answer (see [me.knighthat.kreate3.ui.navigation.rememberRouteResult]):
 * it starts from what the opener passed under [key] and answers it a [ProxyConfig] on Save, an
 * empty one on Clear. Going back answers nothing.
 */
@Composable
fun ProxyEditorScreen(key: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val results = koinInject<RouteResults>()
    val start = remember(key) { results.input<ProxyConfig>(key) ?: ProxyConfig() }

    ProxyEditorContent(
        start = start,
        onBack = onBack,
        onSave = {
            results.answer(key, it)
            onBack()
        },
        modifier = modifier,
    )
}

/**
 * The protocol as two radio buttons side by side, the host and port on one row, then the
 * username and the password, which is optional and can only be typed once there is a username.
 * Save (while every field is acceptable) and Clear (saves an empty proxy, whatever the fields
 * hold) are in the top bar, both close the page.
 *
 * @param start what the fields show at first
 */
@Composable
private fun ProxyEditorContent(
    start: ProxyConfig,
    onBack: () -> Unit,
    onSave: (ProxyConfig) -> Unit,
    modifier: Modifier = Modifier,
) {
    var type by rememberSaveable { mutableStateOf(start.type) }
    var host by rememberSaveable { mutableStateOf(start.host) }
    var port by rememberSaveable { mutableStateOf(if (start.port == 0) "" else start.port.toString()) }
    var username by rememberSaveable { mutableStateOf(start.username) }
    var password by rememberSaveable { mutableStateOf(start.password) }

    val hostError = hostValidator()(host.trim())
    val portError = portValidator()(port.trim())
    val empty = type == ProxyType.HTTP && host.isEmpty() && port.isEmpty() && username.isEmpty() &&
            password.isEmpty()

    DetailScaffold(
        title = stringResource(R.string.screen_title_proxy),
        onBack = onBack,
        modifier = modifier,
        actions = {
            TextButton(
                onClick = { onSave(ProxyConfig()) },
                enabled = !empty || start != ProxyConfig(),
            ) { Text(stringResource(R.string.action_clear)) }
            TextButton(
                onClick = {
                    val user = username.trim()
                    onSave(
                        ProxyConfig(
                            type = type,
                            host = host.trim(),
                            port = port.trim().toIntOrNull() ?: 0,
                            username = user,
                            // No username, no sign in
                            password = if (user.isEmpty()) "" else password,
                        )
                    )
                },
                enabled = hostError == null && portError == null,
            ) { Text(stringResource(R.string.action_save)) }
        },
    ) { padding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier.selectableGroup(),
            ) {
                ProxyType.entries.forEach { option ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.selectable(
                            selected = type == option,
                            onClick = { type = option },
                            role = Role.RadioButton,
                        ),
                    ) {
                        RadioButton(selected = type == option, onClick = null)
                        Text(option.label(), Modifier.padding(start = 8.dp, end = 4.dp))
                    }
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text(stringResource(R.string.setting_proxy_title_host)) },
                    placeholder = { Text("proxy.example.com") },
                    singleLine = true,
                    isError = hostError != null,
                    supportingText = hostError?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        autoCorrectEnabled = false,
                        imeAction = ImeAction.Next,
                    ),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it },
                    label = { Text(stringResource(R.string.setting_proxy_title_port)) },
                    singleLine = true,
                    isError = portError != null,
                    supportingText = portError?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Next,
                    ),
                    modifier = Modifier.width(PORT_WIDTH),
                )
            }
            CredentialField(
                label = stringResource(R.string.setting_title_username),
                value = username,
                onValueChange = { username = it },
                hidden = false,
                imeAction = ImeAction.Next,
            )
            CredentialField(
                label = stringResource(R.string.setting_title_password),
                value = password,
                onValueChange = { password = it },
                hidden = true,
                imeAction = ImeAction.Done,
                placeholder = stringResource(R.string.optional),
                enabled = username.isNotBlank(),
            )
        }
    }
}

@ScreenPreviews
@Composable
private fun ProxyEditorPreview() = PreviewTheme {
    ProxyEditorContent(
        start = ProxyConfig(
            type = ProxyType.SOCKS,
            host = "proxy.example.com",
            port = 1080,
            username = "neon",
        ),
        onBack = {},
        onSave = {},
    )
}
