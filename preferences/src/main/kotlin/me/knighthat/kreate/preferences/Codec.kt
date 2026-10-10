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

/**
 * Converts a preference of type [T] to and from a type the storage understands
 * (see [PreferenceStorage]). The conversion is part of the stored format, changing
 * it needs a migration step.
 *
 * @property decode must never throw, return `null` when the stored value is unreadable
 */
internal class Codec<T>(val encode: (T) -> Any, val decode: (Any) -> T?)

internal inline fun <T, reified S : Any> codec(
    noinline encode: (T) -> S,
    noinline decode: (S) -> T?,
): Codec<T> = Codec(encode) { (it as? S)?.let(decode) }

/**
 * Stored by name, so reordering constants is safe. Unknown names fall back to the default.
 */
internal inline fun <reified E : Enum<E>> enumCodec(): Codec<E> =
    codec<E, String>({ it.name }) { name -> enumValues<E>().firstOrNull { it.name == name } }
