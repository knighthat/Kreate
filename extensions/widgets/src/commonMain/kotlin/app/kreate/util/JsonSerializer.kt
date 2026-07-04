package app.kreate.util

import co.touchlab.kermit.Logger
import kotlinx.serialization.json.Json

/**
 * Utility for converting [Serializable] objects to JSON strings,
 * and parsing them back into typesafe objects.
 */
object JsonSerializer {

    /**
     * Serializes a [Serializable] instance into a JSON string.
     *
     * @param data The typed data class instance to serialize.
     * @return A valid JSON string representation of the object.
     */
    inline fun <reified T> toJson( data: T ): String = Json.encodeToString( data )

    /**
     * Deserializes a JSON string back into a [Serializable] instance.
     *
     * If the string is null, empty, or structurally malformed (e.g., due to an
     * outdated schema version mismatch), this method catches the exception
     * gracefully and returns null.
     *
     * @param jsonString The raw JSON string retrieved from DataStore.
     * @return A [Serializable] instance, or null if parsing fails or input is empty.
     */
    inline fun <reified T> fromJson( jsonString: String? ): T? {
        if ( jsonString.isNullOrEmpty() ) return null

        return try {
            Json.decodeFromString<T>( jsonString )
        } catch ( err: Exception ) {
            Logger.e( "", err, "JsonSerializer" )
            null
        }
    }
}