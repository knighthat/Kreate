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

package me.knighthat.kreate3.provider.opensubsonic

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.knighthat.kreate3.constant.OpenSubsonicAuth
import me.knighthat.kreate3.constant.ProxyType
import me.knighthat.kreate3.preferences.Preference
import me.knighthat.kreate3.preferences.Preferences
import me.knighthat.kreate3.preferences.Secret
import me.knighthat.kreate3.preferences.Secrets

/**
 * What the app knows about the OpenSubsonic server and the account it is signed in with, for
 * everything that needs to know without asking the server again: texts that name it ("Can't
 * reach Navidrome"), the Network settings, checks of whether anybody is signed in.
 *
 * Every answer of a server updates [answered], in memory only. Once [follow] is called, which
 * the app does as it starts, the server is also pinged and the sign in checked as soon as they
 * are set, and again whenever the settings change, the proxy too, or [refresh] is called. Then
 * [server], [credentials], [user] and [login] always say what the last check found.
 */
@OptIn(ExperimentalCoroutinesApi::class)
object OpenSubsonicServer {

    /** The address that answered last, without a trailing `/`, and what it said. */
    data class Answered(val url: String, val info: ServerInfo)

    /** Whether the app is signed in to the server. */
    enum class Login {
        /** Not checked yet, being checked, or the server can't be reached to tell. */
        UNKNOWN,
        LOGGED_IN,

        /** The server is off or not set up, or it refused the sign in. */
        LOGGED_OUT,
    }

    private val _answered = MutableStateFlow<Answered?>(null)
    private val attempt = MutableStateFlow(0)
    private val _server = MutableStateFlow<CheckState>(CheckState.Idle)
    private val _credentials = MutableStateFlow<CheckState>(CheckState.Idle)
    private val _user = MutableStateFlow<OpenSubsonicUser?>(null)
    private val _login = MutableStateFlow(Login.UNKNOWN)
    private var following = false

    /** The server that answered last, `null` until one did; it may not be the active profile's. */
    val answered: StateFlow<Answered?> = _answered.asStateFlow()

    /** Whether a server answers at the address, [CheckState.Idle] while it is off or has none. */
    val server: StateFlow<CheckState> = _server.asStateFlow()

    /** Whether the server accepts the credentials, [CheckState.Idle] until the ping succeeds. */
    val credentials: StateFlow<CheckState> = _credentials.asStateFlow()

    /** The account signed in with, `null` until the server accepted the credentials. */
    val user: StateFlow<OpenSubsonicUser?> = _user.asStateFlow()

    val login: StateFlow<Login> = _login.asStateFlow()

    val isLoggedIn: Boolean
        get() = _login.value == Login.LOGGED_IN

    /**
     * Whether nobody is signed in: the server is off or not set up, or it refused the sign in.
     * `false` while it isn't known, and when the server can't be reached.
     */
    val isLoggedOut: Boolean
        get() = _login.value == Login.LOGGED_OUT

    /** What the active profile's server said, `null` until it answered. */
    val info: ServerInfo?
        // The address is read only once a server answered, the settings may not be loaded yet
        get() = _answered.value?.let { infoAt(ProfileOpenSubsonicSettings.url) }

    /** Name of the active profile's server software, like `Navidrome`, see [ServerInfo.name]. */
    val name: String?
        get() = info?.name

    /** Version of the active profile's server software, `null` when it doesn't say. */
    val version: String?
        get() = info?.version

    /** What the server at [url] said, `null` while another address answered last or none did. */
    fun infoAt(url: String): ServerInfo? =
        _answered.value?.takeIf { it.url == url.normalized() }?.info

    /** Pings the server again, and checks the credentials again if it answers. */
    fun refresh() = attempt.update { it + 1 }

    /**
     * Starts keeping [server], [credentials], [user] and [login] up to date through [provider],
     * for as long as [scope] lives. Does nothing when called again.
     *
     * @param scope the app's long lived one
     */
    internal fun follow(scope: CoroutineScope, provider: OpenSubsonicProvider) {
        if (following) return
        following = true

        scope.launch {
            // Empty until the profile's files are read, which would look like not signed in
            Secrets.OPEN_SUBSONIC_USERNAME.current()
            Secrets.OPEN_SUBSONIC_PASSWORD.current()
            Secrets.OPEN_SUBSONIC_API_KEY.current()

            combine(
                Preferences.OPEN_SUBSONIC_ENABLED.state,
                Preferences.OPEN_SUBSONIC_URL.state,
                Preferences.OPEN_SUBSONIC_ALLOW_SELF_SIGNED.state,
                proxy(),
                attempt,
            ) { enabled, url, _, _, _ -> enabled && url.isNotBlank() }
                .transformLatest { ready ->
                    if (!ready) {
                        emit(CheckState.Idle)
                    } else {
                        emit(CheckState.Checking)
                        emit(CheckState.Done(provider.ping()))
                    }
                }
                .onEach { _server.value = it }
                .launchIn(this)

            combine(_server, credentialsSet()) { server, set ->
                // A new ping goes through Checking first, so every successful one checks again
                ((server as? CheckState.Done)?.result is ServerCheck.Success) to set
            }
                .distinctUntilChanged()
                .transformLatest { (reachable, set) ->
                    if (!reachable || set == null) {
                        emit(CheckState.Idle)
                    } else {
                        emit(CheckState.Checking)
                        emit(CheckState.Done(provider.checkCredentials()))
                    }
                }
                .onEach { _credentials.value = it }
                .launchIn(this)

            _credentials
                .transformLatest { check ->
                    emit(
                        if ((check as? CheckState.Done)?.result is ServerCheck.Success)
                            try {
                                provider.user()
                            } catch (_: ServerException) {
                                null
                            }
                        else
                            null
                    )
                }
                .onEach { _user.value = it }
                .launchIn(this)

            combine(
                Preferences.OPEN_SUBSONIC_ENABLED.state,
                Preferences.OPEN_SUBSONIC_URL.state,
                credentialsSet(),
                _credentials,
            ) { enabled, url, set, check ->
                val result = (check as? CheckState.Done)?.result
                when {
                    !enabled || url.isBlank() || set == null -> Login.LOGGED_OUT
                    result is ServerCheck.Success -> Login.LOGGED_IN
                    result is ServerCheck.Rejected -> Login.LOGGED_OUT
                    else -> Login.UNKNOWN
                }
            }
                .onEach { _login.value = it }
                .launchIn(this)
        }
    }

    internal fun record(url: String, info: ServerInfo) {
        _answered.value = Answered(url.normalized(), info)
    }

    private fun String.normalized() = trim().trimEnd('/')

    /**
     * Everything of the server's proxy and of the app's, which it falls back to. A change of any
     * is a new value, which starts a new check.
     */
    private fun proxy(): Flow<List<Any>> =
        combine(
            proxySettings(
                Preferences.OPEN_SUBSONIC_PROXY_ENABLED,
                Preferences.OPEN_SUBSONIC_PROXY_TYPE,
                Preferences.OPEN_SUBSONIC_PROXY_HOST,
                Preferences.OPEN_SUBSONIC_PROXY_PORT,
                Secrets.OPEN_SUBSONIC_PROXY_USERNAME,
                Secrets.OPEN_SUBSONIC_PROXY_PASSWORD,
            ),
            proxySettings(
                Preferences.NETWORK_PROXY_ENABLED,
                Preferences.NETWORK_PROXY_TYPE,
                Preferences.NETWORK_PROXY_HOST,
                Preferences.NETWORK_PROXY_PORT,
                Secrets.NETWORK_PROXY_USERNAME,
                Secrets.NETWORK_PROXY_PASSWORD,
            ),
        ) { own, app -> own + app }

    private fun proxySettings(
        enabled: Preference<Boolean>,
        type: Preference<ProxyType>,
        host: Preference<String>,
        port: Preference<Int>,
        username: Secret,
        password: Secret,
    ): Flow<List<Any>> =
        combine(
            combine(
                enabled.state,
                type.state,
                host.state,
                port.state
            ) { on, kind, address, number ->
                listOf(on, kind, address, number)
            },
            username.state,
            password.state,
        ) { settings, user, secret -> settings + user + secret }

    /**
     * The credentials of the chosen method, `null` while any of them is missing. A change of
     * them is a new value, which starts a new check.
     */
    private fun credentialsSet(): Flow<List<String>?> =
        combine(
            Preferences.OPEN_SUBSONIC_AUTH.state,
            Secrets.OPEN_SUBSONIC_USERNAME.state,
            Secrets.OPEN_SUBSONIC_PASSWORD.state,
            Secrets.OPEN_SUBSONIC_API_KEY.state,
        ) { auth, username, password, apiKey ->
            when (auth) {
                OpenSubsonicAuth.PASSWORD -> listOf(username, password)
                OpenSubsonicAuth.API_KEY -> listOf(apiKey)
            }.takeIf { values -> values.none(String::isEmpty) }
        }
}
