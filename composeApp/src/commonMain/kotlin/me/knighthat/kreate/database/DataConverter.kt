package me.knighthat.kreate.database

import androidx.room.TypeConverter
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration


object DataConverter {

    @TypeConverter
    fun longToDuration( duration: Long ): Duration = duration.toDuration( DurationUnit.SECONDS )

    @TypeConverter
    fun durationToLong( duration: Duration ): Long = duration.inWholeSeconds

    @TypeConverter
    fun fromInstant( instant: Instant? ): Long? = instant?.toEpochMilli()

    @TypeConverter
    fun toInstant( timestamp: Long? ): Instant? = timestamp?.let { Instant.ofEpochMilli( it ) }
}