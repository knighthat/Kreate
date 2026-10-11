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

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Everything stored for the active profile. Compared by identity, a new instance
 * means the profile (or its content) changed.
 */
internal class Snapshot(val values: Map<String, Any>)

/**
 * Connects [Preference]s with the [PreferenceStorage].
 *
 * Writes and profile switches go through a single queue, so a change made before
 * a switch is always saved to the profile it was made in, and a profile is never
 * loaded before its pending writes are saved. Writes are batched, rapid changes of
 * one setting end up as a single write.
 */
internal class PreferenceEngine(
    private val profiles: ProfileManager,
    internal val scope: CoroutineScope
) {

    private sealed interface Command {
        class Dirty(val preference: Preference<*>) : Command
        class Switch(val profileId: String) : Command
        class Flush(val done: CompletableDeferred<Unit>) : Command
    }

    private val commands = Channel<Command>(Channel.UNLIMITED)
    private val loaded = CompletableDeferred<Unit>()
    private val mutableSnapshot = MutableStateFlow(Snapshot(emptyMap()))

    /**
     * Values of the active profile, empty until [awaitLoaded] returns.
     */
    val snapshot: StateFlow<Snapshot> = mutableSnapshot.asStateFlow()

    init {
        scope.launch {
            profiles.activeId
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

    fun markDirty(preference: Preference<*>) {
        commands.trySend(Command.Dirty(preference))
    }

    /**
     * Saves pending changes now, call it when the app is about to close.
     */
    suspend fun flush() {
        val done = CompletableDeferred<Unit>()
        commands.send(Command.Flush(done))
        done.await()
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
                is Command.Flush -> {
                    write(profileId, dirty)
                    command.done.complete(Unit)
                }

                is Command.Switch -> {
                    write(profileId, dirty)
                    profileId = command.profileId
                    load(command.profileId)
                }
            }
        }
    }

    private suspend fun write(profileId: String?, dirty: MutableSet<Preference<*>>) {
        if (profileId == null || dirty.isEmpty()) return

        val changes = dirty.associate { it.name to it.encoded() }
        dirty.clear()

        try {
            profiles.storage.write(profileId, changes)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // TODO(knighthat): report failed writes
        }
    }

    private suspend fun load(profileId: String) {
        try {
            mutableSnapshot.value = Snapshot(profiles.storage.read(profileId))
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // TODO(knighthat): handle unreadable files, defaults are used meanwhile
            mutableSnapshot.value = Snapshot(emptyMap())
        } finally {
            loaded.complete(Unit)
        }
    }

    private companion object {

        const val DEBOUNCE_MILLIS = 200L
    }
}
