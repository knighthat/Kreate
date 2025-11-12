package app.kreate.util

import java.util.StringJoiner
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration


/**
 * Matches:
 * - dd:hh:mm:ss
 * - hh:mm:ss
 * - mm:ss
 * - ss
 *
 * Single or double-digits are accepted
 */
val DURATION_FORMAT_REGEX = Regex("^\\d+(?::\\d+){0,3}$")

// These are multiplier that turns current unit to seconds
private val TIME_UNITS = arrayOf(1, 60, 3600, 86400)

private fun String.isDigitOnly(): Boolean = Regex("^\\d+").matches( this )

/**
 * Converts current string into [Duration].
 *
 * Returns [Duration.ZERO] if blank,
 * or format is invalid.
 *
 * @return [Duration] instance from [String], [Duration.ZERO] if [String] is `null`
 */
fun String?.toDuration(): Duration {
    if( this.isNullOrBlank() )
        // No data to convert, result to 0
        return Duration.ZERO
    else if( this.isDigitOnly() )
        // Assume it's seconds if only digits present
        return this.toLong().toDuration( DurationUnit.SECONDS )
    else if( DURATION_FORMAT_REGEX.matches( this ) ) {
        var totalSeconds = 0L

        val parts = this.split(":").reversed()
        for( i in 0..parts.lastIndex ) {
            val p = parts[i]

            // Requires all parts to be digits only
            // This also eliminates negative numbers
            if( !p.isDigitOnly() ) return Duration.ZERO

            val value = p.toInt()
            if( (i < 2 && value > 59)
                || (i == 2 && value > 23)
            )
                // Second [0], minute [1] are ranged from 0-59
                // while hour [2] is ranged from 0-23
                return Duration.ZERO

            totalSeconds += value * TIME_UNITS[i]
        }

        return totalSeconds.toDuration( DurationUnit.SECONDS )
    } else
        return Duration.ZERO
}

/**
 * Convert [Duration] to human-readable text
 *
 * Examples:
 * - 128:20:06:28
 * - 7:32:01
 *
 * **Hours**, **minutes**, and **seconds** are always shown in
 * 2-digit format. **Days** have no limit
 */
fun Duration.readableText(): String {
    val joiner = StringJoiner(":")
    this.toComponents { days, hours, minutes, seconds, nanoseconds ->
        if( days > 0 )
            // Allow single digit
            joiner.add( "$days" )
        // If [days] is a non-zero number,
        // this number must be added too
        if( hours > 0 || days > 0 )
            joiner.add(
                // Always shows 2-digit number
                "%02d".format( hours )
            )
        joiner.add(
            // Always shows 2-digit number
            "%02d".format( minutes )
        )
        joiner.add(
            // Always shows 2-digit number
            "%02d".format( seconds )
        )
    }

    return joiner.toString()
}