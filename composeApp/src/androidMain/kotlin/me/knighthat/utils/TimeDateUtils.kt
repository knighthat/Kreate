package me.knighthat.utils

import me.knighthat.utils.TimeDateUtils.localizedDate
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.Locale

object TimeDateUtils {

    /**
     * Generate a [DateFormat] that follows
     * user's date format.
     *
     * Format is calculated based on user's
     * locale setting
     *
     * Here are some examples:
     * - **en_US**: 3/9/25
     * - **en_GB**: 09/03/25
     * - **de_DE**: 09.03.25
     * - **ja_JP**: 25/03/09
     * - **fr_FR**: 09/03/2025
     */
    fun localizedDateFormat(): DateFormat =
        SimpleDateFormat.getDateInstance( SimpleDateFormat.SHORT, Locale.getDefault() )

    /**
     * Get [DateFormat] and convert it
     * into string.
     */
    fun localizedDate(): String = localizedDateFormat().format( Date() )

    /**
     * Remove all slashes, dots, and dashes from [localizedDate]
     */
    fun localizedDateNoDelimiter(): String =
        localizedDate().replace("/", "")
                       .replace("-", "")
                       .replace(".", "")

    /**
     * Generate a [SimpleDateFormat] represents time
     * in format "HHmmss" without colons.
     */
    fun timeFormatNoDelimiter(): SimpleDateFormat = SimpleDateFormat( "HHmmss", Locale.getDefault() )

    fun timeNoDelimiter(): String = timeFormatNoDelimiter().format( Date() )

    /**
     * @return epoch millis represents the start of [date]
     */
    fun toStartDateMillis( date: LocalDate ): Long =
        date.atStartOfDay( ZoneId.systemDefault() ).toInstant().toEpochMilli()

    fun logFileName(): DateFormat =
        SimpleDateFormat( "yyyy-MM-dd_HH-mm-ss", Locale.getDefault() )
}