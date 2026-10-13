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

package me.knighthat.kreate.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Keeps the [Secret]s of each profile in `<root>/profiles/<id>/secrets.preferences_pb`, a file of
 * its own beside the profile's preferences. Deleting the profile deletes its secrets, and they
 * follow the active profile like preferences do.
 *
 * This class is the only owner of the secret files, a file must never be opened elsewhere.
 *
 * Reads and writes run one after another in the order they were asked, so a change made before
 * a profile switch is saved to the profile it was made in.
 *
 * Create a single instance per process.
 */
internal class SecretStore(
    private val scope: CoroutineScope,
    private val profiles: ProfileManager,
    private val rootDirectory: File,
) {

    constructor(scope: CoroutineScope, profiles: ProfileManager, context: Context) :
            this(scope, profiles, context.filesDir)

    private class OpenStore(val scope: CoroutineScope, val dataStore: DataStore<Preferences>)

    private val states = ConcurrentHashMap<String, MutableStateFlow<String>>()
    private val stores = mutableMapOf<String, OpenStore>()
    private val tasks = Channel<suspend () -> Unit>(Channel.UNLIMITED)

    init {
        scope.launch(Dispatchers.IO) {
            for (task in tasks) {
                try {
                    task()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // TODO(knighthat): report failed reads and writes
                }
            }
        }
        scope.launch {
            profiles.activeId.filterNotNull().distinctUntilChanged().collect { profileId ->
                states.forEach { (name, state) -> load(profileId, name, state) }
            }
        }
        scope.launch {
            // A deleted profile's file is gone, release the store that had it open
            profiles.ids.collect { ids ->
                tasks.trySend { stores.keys.filter { it !in ids }.forEach(::close) }
            }
        }
    }

    fun secret(name: String): Secret {
        require(NAME.matches(name)) { "\"$name\" must be lowercase letters, digits and _" }
        return Secret(name, this)
    }

    fun stateOf(name: String): StateFlow<String> = states.getOrPut(name) {
        MutableStateFlow("").also { state ->
            // Without an active profile the first switch loads it
            profiles.activeId.value?.let { load(it, name, state) }
        }
    }

    /**
     * The value of [name] after every read and write asked before, the load of the active
     * profile's file included.
     */
    suspend fun current(name: String): String {
        val state = stateOf(name)
        val done = CompletableDeferred<Unit>()
        tasks.send { done.complete(Unit) }
        done.await()
        return state.value
    }

    fun write(name: String, value: String) {
        val state = states.getOrPut(name) { MutableStateFlow("") }
        val profileId = profiles.activeId.value ?: return

        tasks.trySend {
            storeOf(profileId).edit {
                val key = stringPreferencesKey(name)
                if (value.isEmpty()) it.remove(key) else it[key] = value
            }
            if (profiles.activeId.value == profileId) state.value = value
        }
    }

    private fun load(profileId: String, name: String, state: MutableStateFlow<String>) {
        tasks.trySend {
            // A profile switched meanwhile has nothing to show
            if (profiles.activeId.value == profileId)
                state.value = storeOf(profileId).data.first()[stringPreferencesKey(name)].orEmpty()
        }
    }

    // Only called from the task queue, so the map needs no lock
    private fun storeOf(profileId: String): DataStore<Preferences> =
        stores.getOrPut(profileId) {
            // Child of the scope, so stopping the store releases its files
            val storeScope =
                CoroutineScope(scope.coroutineContext + Dispatchers.IO + SupervisorJob(scope.coroutineContext.job))
            val dataStore = createPreferencesDataStore(storeScope, SECRET_STEPS) {
                File(Profile.directory(rootDirectory, profileId), FILE_NAME)
            }
            OpenStore(storeScope, dataStore)
        }.dataStore

    private fun close(profileId: String) {
        stores.remove(profileId)?.scope?.cancel()
    }

    private companion object {

        const val FILE_NAME = "secrets.preferences_pb"

        val NAME = Regex("[a-z0-9_]+")
    }
}
