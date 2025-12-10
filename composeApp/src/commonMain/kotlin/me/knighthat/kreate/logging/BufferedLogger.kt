package me.knighthat.kreate.logging

import co.touchlab.kermit.Logger


/**
 * A logger with main purpose of holding logs until appropriate logger takes place.
 */
interface BufferedLogger {

    fun flushTo( logger: Logger )
}