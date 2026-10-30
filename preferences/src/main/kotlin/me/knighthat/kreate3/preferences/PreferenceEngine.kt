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

package me.knighthat.kreate3.preferences

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Everything stored for the active profile. Compared by identity, a new instance
 * means the profile (or its content) changed.
 *
 * @param profileId the profile the values belong to, `null` before one is loaded
 */
internal class Snapshot(val values: Map<String, Any>, val profileId: String? = null)

/**
 * Connects [Preference]s with the [PreferenceStorage].
 *
 * Writes and profile switches go through a single queue, so a change made before
 * a switch is always saved to the profile it was made in, and a profile is never
 * loaded before its pending writes are saved. Writes are batched, rapid changes of
 * one setting end up as a single write. A write that keeps failing puts its settings back to
 * what is stored.
 *
 * @param profileIds the profile whose values are served, each new one is loaded
 * @param store what failures are reported as
 */
internal class PreferenceEngine(
    private val storage: PreferenceStorage,
    profileIds: Flow<String?>,
    private val reports: FailureReports,
    private val store: PreferenceFailure.Store,
    private val scope: CoroutineScope,
) {

    /** Serves the [Preferences] of the active profile */
    constructor(profiles: ProfileManager, scope: CoroutineScope) : this(
        profiles.storage,
        profiles.activeId,
        profiles.reports,
        PreferenceFailure.Store.SETTINGS,
        scope,
    )

    private sealed interface Command {
        class Dirty(val preference: Preference<*>) : Command
        class Switch(val profileId: String) : Command
        class Flush(val done: CompletableDeferred<Boolean>) : Command
    }

    private val commands = Channel<Command>(Channel.UNLIMITED)
    private val loaded = CompletableDeferred<Unit>()
    private val mutableSnapshot = MutableStateFlow(Snapshot(emptyMap()))
    private val preferences = CopyOnWriteArraySet<Preference<*>>()

    /** What is in the file of the active profile, only [process] touches it */
    private val saved = mutableMapOf<String, Any>()

    /**
     * Values of the active profile, empty until [awaitLoaded] returns.
     */
    val snapshot: StateFlow<Snapshot> = mutableSnapshot.asStateFlow()

    /**
     * See [FailureReports.failures], those of the secrets and the profile list included.
     */
    val failures: Flow<PreferenceFailure> = reports.failures

    /** Whether [awaitLoaded] returns at once */
    val isLoaded: Boolean get() = loaded.isCompleted

    init {
        scope.launch {
            profileIds
                .filterNotNull()
                .distinctUntilChanged()
                .collect { commands.send(Command.Switch(it)) }
        }
        scope.launch { process() }
    }

    /**
     * Suspends until the active profile is loaded for the first time.
     */
    suspend fun awaitLoaded() = loaded.await()

    /**
     * Suspends until the settings of [profileId] are loaded, like after switching to it.
     */
    suspend fun awaitProfile(profileId: String) {
        snapshot.first { it.profileId == profileId }
    }

    /**
     * Keeps [preference] in step with [snapshot]: it gets each new one before anyone waiting for
     * it (like [awaitLoaded]) goes on.
     */
    fun register(preference: Preference<*>) {
        preferences += preference
    }

    fun markDirty(preference: Preference<*>) {
        commands.trySend(Command.Dirty(preference))
    }

    /**
     * Saves pending changes now, call it when the app is about to close.
     *
     * @return `false` when they couldn't be saved, which is reported and undone
     */
    suspend fun flush(): Boolean {
        val done = CompletableDeferred<Boolean>()
        commands.send(Command.Flush(done))
        return done.await()
    }

    private suspend fun process() {
        val dirty = mutableSetOf<Preference<*>>()
        var profileId: String? = null

        while (true) {
            // Wait for a quiet moment before saving, `null` means it's reached
            val command = if (dirty.isEmpty())
                commands.receive()
            else
                withTimeoutOrNull(DEBOUNCE_MILLIS) { commands.receive() }

            when (command) {
                null -> write(profileId, dirty)
                is Command.Dirty -> dirty += command.preference
                is Command.Flush -> command.done.complete(write(profileId, dirty))

                is Command.Switch -> {
                    write(profileId, dirty)
                    profileId = command.profileId
                    load(command.profileId)
                }
            }
        }
    }

    /** @return `false` when it couldn't be saved */
    private suspend fun write(profileId: String?, dirty: MutableSet<Preference<*>>): Boolean {
        if (profileId == null || dirty.isEmpty()) return true

        val batch = dirty.toList()
        dirty.clear()
        val changes = batch.associate { it.name to it.encoded() }

        val error = retrying { storage.write(profileId, changes) }
        if (error == null) {
            changes.forEach { (name, value) ->
                if (value == null) saved -= name else saved[name] = value
            }
            return true
        }

        // A setting changed again meanwhile is in the queue, it keeps that change
        batch.forEach { it.revert(failed = changes[it.name], stored = saved[it.name]) }
        report(saving = true, error)
        return false
    }

    private suspend fun load(profileId: String) {
        saved.clear()
        try {
            val values = storage.read(profileId)
            saved += values
            publish(Snapshot(values, profileId))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Defaults are used meanwhile
            publish(Snapshot(emptyMap(), profileId))
            report(saving = false, e)
        } finally {
            loaded.complete(Unit)
        }
    }

    private fun publish(snapshot: Snapshot) {
        mutableSnapshot.value = snapshot
        preferences.forEach { it.load(snapshot) }
    }

    private fun report(saving: Boolean, error: Exception) =
        reports.report(store, saving, storage.reasonOf(error))

    private companion object {

        const val DEBOUNCE_MILLIS = 200L
    }
}
