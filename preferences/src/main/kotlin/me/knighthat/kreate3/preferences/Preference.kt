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

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update

/**
 * A single setting of the active profile, or of the whole app ([GlobalPreferences]).
 *
 * Its value follows the active profile, switching profile replaces the value
 * with the one stored for the new profile. Changes are visible immediately and
 * saved in the background.
 *
 * Use the instances declared in [Preferences] and [GlobalPreferences], they must not be created
 * elsewhere.
 */
class Preference<T> internal constructor(
    internal val name: String,
    val default: T,
    private val codec: Codec<T>,
    private val engine: PreferenceEngine,
) {

    // The snapshot the value comes from, a newer one replaces the value
    private var applied = engine.snapshot.value

    private val _state = MutableStateFlow(decode(applied))

    /**
     * Current value, emits every time it changes.
     */
    val state: StateFlow<T> = _state.asStateFlow()

    val value: T get() = _state.value

    init {
        engine.register(this)
        // One published while registering would be missed
        load(engine.snapshot.value)
    }

    fun update(value: T) {
        if (_state.getAndUpdate { value } != value)
            engine.markDirty(this)
    }

    fun update(transform: (T) -> T) {
        var changed = false
        _state.update { old -> transform(old).also { changed = it != old } }

        if (changed) engine.markDirty(this)
    }

    fun reset() = update(default)

    /**
     * What goes to the storage. Defaults are not stored (`null` removes the entry),
     * so that changing a default later reaches users that never touched the setting.
     */
    internal fun encoded(): Any? = encode(value)

    /**
     * Goes back to [stored] (`null` for the default) without saving, unless the value changed
     * since [failed] couldn't be saved; that change is saved on its own.
     */
    internal fun revert(failed: Any?, stored: Any?) {
        val current = _state.value
        if (encode(current) == failed)
            _state.compareAndSet(current, decode(stored))
    }

    /**
     * Takes the value in [snapshot], the values of a profile just loaded.
     */
    internal fun load(snapshot: Snapshot) = synchronized(this) {
        if (snapshot !== applied) {
            applied = snapshot
            _state.value = decode(snapshot)
        }
    }

    private fun encode(value: T): Any? = if (value == default) null else codec.encode(value)

    private fun decode(stored: Any?): T = stored?.let(codec.decode) ?: default

    private fun decode(snapshot: Snapshot): T = decode(snapshot.values[name])
}
