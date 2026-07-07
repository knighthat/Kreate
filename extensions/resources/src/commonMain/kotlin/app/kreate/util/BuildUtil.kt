package app.kreate.util


private var isDebug: Boolean? = null

val IS_DEBUG: Boolean
    get() = isDebug == true

/**
 * This function can only be called once,
 * subsequence calls result in [IllegalStateException].
 */
fun setDebugMode( debugMode: Boolean ) {
    check( isDebug == null )
    isDebug = debugMode
}
