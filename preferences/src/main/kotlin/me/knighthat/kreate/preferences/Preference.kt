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

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * A single setting of the active profile.
 *
 * Its value follows the active profile, switching profile replaces the value
 * with the one stored for the new profile. Changes are visible immediately and
 * saved in the background.
 *
 * Use the instances declared in [Preferences], they must not be created elsewhere.
 */
class Preference<T> internal constructor(
    internal val name: String,
    val default: T,
    private val codec: Codec<T>,
    private val engine: PreferenceEngine,
) {

    private val _state = MutableStateFlow(decode(engine.snapshot.value))

    /**
     * Current value, emits every time it changes.
     */
    val state: StateFlow<T> = _state.asStateFlow()

    val value: T get() = _state.value

    init {
        engine.scope.launch {
            var applied = engine.snapshot.value
            engine.snapshot.collect {
                if (it !== applied) {
                    applied = it
                    _state.value = decode(it)
                }
            }
        }
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
    internal fun encoded(): Any? = if (value == default) null else codec.encode(value)

    private fun decode(snapshot: Snapshot): T = snapshot.values[name]?.let(codec.decode) ?: default
}
