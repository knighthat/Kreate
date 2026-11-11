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

package me.knighthat.kreate3.ui.screen.settings.network.opensubsonic

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import me.knighthat.kreate3.R
import me.knighthat.kreate3.constant.OpenSubsonicAuth
import me.knighthat.kreate3.preferences.Preferences
import me.knighthat.kreate3.preferences.Secrets
import me.knighthat.kreate3.preferences.collectAsStateWithLifecycle
import me.knighthat.kreate3.ui.component.PreviewTheme
import me.knighthat.kreate3.ui.component.ScreenPreviews
import me.knighthat.kreate3.ui.component.labelRes
import me.knighthat.kreate3.ui.component.openSubsonicName
import me.knighthat.kreate3.ui.screen.detail.DetailScaffold
import me.knighthat.kreate3.ui.screen.settings.CredentialField

/**
 * The address and credentials of the OpenSubsonic server, a segment per way to sign in. Nothing is
 * kept until Sign in (Update once signed in), which also makes the chosen way the one used; the
 * other way's credentials stay, unused. Sign out forgets the credentials once confirmed, the
 * address stays.
 */
@Composable
fun OpenSubsonicSignInScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val url by Preferences.OPEN_SUBSONIC_URL.collectAsStateWithLifecycle()
    val auth by Preferences.OPEN_SUBSONIC_AUTH.collectAsStateWithLifecycle()
    val username by Secrets.OPEN_SUBSONIC_USERNAME.collectAsStateWithLifecycle()
    val password by Secrets.OPEN_SUBSONIC_PASSWORD.collectAsStateWithLifecycle()
    val apiKey by Secrets.OPEN_SUBSONIC_API_KEY.collectAsStateWithLifecycle()

    OpenSubsonicSignInContent(
        server = openSubsonicName(),
        url = url,
        auth = auth,
        username = username,
        password = password,
        apiKey = apiKey,
        onSave = { address, method, user, secret, key ->
            Preferences.OPEN_SUBSONIC_URL.update(address)
            Preferences.OPEN_SUBSONIC_AUTH.update(method)
            when (method) {
                OpenSubsonicAuth.PASSWORD -> {
                    Secrets.OPEN_SUBSONIC_USERNAME.update(user)
                    Secrets.OPEN_SUBSONIC_PASSWORD.update(secret)
                }

                OpenSubsonicAuth.API_KEY -> Secrets.OPEN_SUBSONIC_API_KEY.update(key)
            }
            onBack()
        },
        onSignOut = {
            Secrets.OPEN_SUBSONIC_USERNAME.clear()
            Secrets.OPEN_SUBSONIC_PASSWORD.clear()
            Secrets.OPEN_SUBSONIC_API_KEY.clear()
            onBack()
        },
        onBack = onBack,
        modifier = modifier,
    )
}

/**
 * @param server what the server is called, see [openSubsonicName]
 * @param url the saved address of the server
 * @param auth the way to sign in used now, chosen when the screen opens
 * @param onSave the address (trimmed, no trailing `/`), the chosen way with the username
 * (trimmed), password and API key (trimmed)
 */
@Composable
internal fun OpenSubsonicSignInContent(
    server: String,
    url: String,
    auth: OpenSubsonicAuth,
    username: String,
    password: String,
    apiKey: String,
    onSave: (String, OpenSubsonicAuth, String, String, String) -> Unit,
    onSignOut: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // `null` while untouched, the saved value shows: secrets are empty until their file is read
    var typedUrl by rememberSaveable { mutableStateOf<String?>(null) }
    var picked by rememberSaveable { mutableStateOf<OpenSubsonicAuth?>(null) }
    var typedUsername by rememberSaveable { mutableStateOf<String?>(null) }
    var typedPassword by rememberSaveable { mutableStateOf<String?>(null) }
    var typedApiKey by rememberSaveable { mutableStateOf<String?>(null) }
    var askSignOut by remember { mutableStateOf(false) }

    val shownUrl = typedUrl ?: url
    val cleanUrl = shownUrl.trim().trimEnd('/')
    val urlError = serverUrlValidator()(cleanUrl)
    val urlChanged = cleanUrl != url
    val shownMethod = picked ?: auth
    val shownUsername = typedUsername ?: username
    val shownPassword = typedPassword ?: password
    val shownApiKey = typedApiKey ?: apiKey
    val canSave = cleanUrl.isNotEmpty() && urlError == null && when (shownMethod) {
        OpenSubsonicAuth.PASSWORD ->
            shownUsername.isNotBlank() && shownPassword.isNotEmpty() &&
                    (urlChanged || shownMethod != auth || shownUsername.trim() != username || shownPassword != password)

        OpenSubsonicAuth.API_KEY ->
            shownApiKey.isNotBlank() &&
                    (urlChanged || shownMethod != auth || shownApiKey.trim() != apiKey)
    }
    val anySaved = username.isNotEmpty() || password.isNotEmpty() || apiKey.isNotEmpty()
    // Like the "Sign in" row's "Not signed in": the way in use has every credential saved
    val signedIn = when (auth) {
        OpenSubsonicAuth.PASSWORD -> username.isNotEmpty() && password.isNotEmpty()
        OpenSubsonicAuth.API_KEY -> apiKey.isNotEmpty()
    }

    if (askSignOut)
        AlertDialog(
            onDismissRequest = { askSignOut = false },
            title = {
                Text(
                    stringResource(
                        R.string.dialog_title_sign_out,
                        server
                    )
                )
            },
            text = { Text(stringResource(R.string.dialog_body_sign_out_of_opensubsonic)) },
            confirmButton = {
                TextButton(onClick = {
                    askSignOut = false
                    onSignOut()
                }) { Text(stringResource(R.string.action_sign_out)) }
            },
            dismissButton = {
                TextButton(onClick = { askSignOut = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )

    DetailScaffold(
        title = stringResource(R.string.screen_title_sign_in),
        onBack = onBack,
        modifier = modifier,
        actions = {
            TextButton(onClick = { askSignOut = true }, enabled = anySaved) {
                Text(stringResource(R.string.action_sign_out))
            }
            TextButton(
                onClick = {
                    onSave(
                        cleanUrl,
                        shownMethod,
                        shownUsername.trim(),
                        shownPassword,
                        shownApiKey.trim()
                    )
                },
                enabled = canSave,
            ) {
                Text(
                    stringResource(if (signedIn) R.string.action_update else R.string.action_sign_in)
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(16.dp),
            ) {
                CredentialField(
                    label = stringResource(R.string.setting_title_server_address),
                    value = shownUrl,
                    onValueChange = { typedUrl = it },
                    hidden = false,
                    imeAction = ImeAction.Next,
                    placeholder = "https://music.example.com",
                    keyboardType = KeyboardType.Uri,
                    error = urlError,
                )
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 8.dp)
                ) {
                    OpenSubsonicAuth.entries.forEach { method ->
                        SegmentedButton(
                            selected = shownMethod == method,
                            onClick = { picked = method },
                            shape = SegmentedButtonDefaults.itemShape(
                                method.ordinal,
                                OpenSubsonicAuth.entries.size
                            ),
                        ) { Text(stringResource(method.labelRes)) }
                    }
                }
                when (shownMethod) {
                    OpenSubsonicAuth.PASSWORD -> {
                        CredentialField(
                            label = stringResource(R.string.setting_title_username),
                            value = shownUsername,
                            onValueChange = { typedUsername = it },
                            hidden = false,
                            imeAction = ImeAction.Next,
                        )
                        CredentialField(
                            label = stringResource(R.string.setting_title_password),
                            value = shownPassword,
                            onValueChange = { typedPassword = it },
                            hidden = true,
                            imeAction = ImeAction.Done,
                        )
                    }

                    OpenSubsonicAuth.API_KEY -> CredentialField(
                        label = stringResource(R.string.setting_title_api_key),
                        value = shownApiKey,
                        onValueChange = { typedApiKey = it },
                        hidden = true,
                        imeAction = ImeAction.Done,
                    )
                }
            }
        }
    }
}

@ScreenPreviews
@Composable
private fun OpenSubsonicSignInPreview() = PreviewTheme {
    OpenSubsonicSignInContent(
        server = "Navidrome",
        url = "https://music.example.com",
        auth = OpenSubsonicAuth.PASSWORD,
        username = "neon",
        password = "secret",
        apiKey = "",
        onSave = { _, _, _, _, _ -> },
        onSignOut = {},
        onBack = {},
    )
}

/** Nothing saved yet, the button signs in. */
@ScreenPreviews
@Composable
private fun OpenSubsonicSignInSignedOutPreview() = PreviewTheme {
    OpenSubsonicSignInContent(
        server = "Navidrome",
        url = "",
        auth = OpenSubsonicAuth.PASSWORD,
        username = "",
        password = "",
        apiKey = "",
        onSave = { _, _, _, _, _ -> },
        onSignOut = {},
        onBack = {},
    )
}

/**
 * Only an address starting with `http://` or `https://` and naming a host is accepted.
 * An empty one is, signing in needs one anyway.
 *
 * @return a check that gives the reason the address is not acceptable, `null` when it is
 */
@Composable
private fun serverUrlValidator(): (String) -> String? {
    val scheme = stringResource(R.string.setting_description_server_url_scheme)
    val host = stringResource(R.string.setting_description_server_url_host)
    return { url ->
        when {
            url.isEmpty() -> null
            !url.startsWith("https://") && !url.startsWith("http://") -> scheme
            url.substringAfter("://").isBlank() -> host
            else -> null
        }
    }
}
