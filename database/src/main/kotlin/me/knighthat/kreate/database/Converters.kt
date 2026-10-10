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

package me.knighthat.kreate.database

import androidx.room.TypeConverter
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

private val NAMES_SERIALIZER = ListSerializer(String.serializer())

/**
 * Stores [Instant] as epoch millis, [Duration] as whole seconds and name lists as a JSON array.
 */
internal class Converters {

    @TypeConverter
    fun fromInstant(value: Instant?): Long? = value?.toEpochMilliseconds()

    @TypeConverter
    fun toInstant(value: Long?): Instant? = value?.let(Instant::fromEpochMilliseconds)

    @TypeConverter
    fun fromDuration(value: Duration?): Long? = value?.inWholeSeconds

    @TypeConverter
    fun toDuration(value: Long?): Duration? = value?.seconds

    @TypeConverter
    fun fromNames(value: List<String>?): String? =
        value?.let { Json.encodeToString(NAMES_SERIALIZER, it) }

    @TypeConverter
    fun toNames(value: String?): List<String>? =
        value?.let { runCatching { Json.decodeFromString(NAMES_SERIALIZER, it) }.getOrNull() }
}
