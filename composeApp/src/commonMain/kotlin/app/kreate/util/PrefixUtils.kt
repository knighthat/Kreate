package app.kreate.util

const val MODIFIED_PREFIX = "modified:"
const val LOCAL_KEY_PREFIX = "local:"


/**
 * Assumption: all prefixes end with ":" and have at least 1 (other) character.
 * Removes a "prefix of prefixes" including multiple times the same prefix (at different locations).
 *
 * Additionally, drop `isYoutubePlaylist` column because it can be determined by `browseId`
 * and doesn't require a hard-coded value
 */
fun cleanPrefix(text: String): String {
    val splitText = text.split(":")
    var i = 0
    while (i < splitText.size-1) {
        if ("${splitText[i]}:" !in listOf(MODIFIED_PREFIX, LOCAL_KEY_PREFIX)) {
            break
        }
        i++
    }
    if(i >= splitText.size) return ""
    return splitText.subList(i, splitText.size).joinToString(":")
}
