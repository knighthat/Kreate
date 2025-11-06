package app.kreate.util

const val MODIFIED_PREFIX = "modified:"

/**
 * Assumption: all prefixes end with ":" and have at least 1 (other) character.
 * Removes a "prefix of prefixes" including multiple times the same prefix (at different locations).
 */
fun cleanPrefix( text: String ): String {
    return text.substringAfter( MODIFIED_PREFIX )
}
