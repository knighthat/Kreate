package me.knighthat.utils

import app.kreate.util.MODIFIED_PREFIX
import org.jetbrains.annotations.Contract

object PropUtils {

    /**
     * Returns [curPropVal] if it has been modified; otherwise, returns [fetPropVal].
     *
     * This function checks whether [curPropVal] has been modified ().
     * If it is noted by [MODIFIED_PREFIX], it is considered modified and retained.
     * Otherwise, the function returns [fetPropVal].
     *
     * @param curPropVal The current property value, potentially modified externally.
     * @param fetPropVal The latest fetched property value.
     *
     * @return [curPropVal] if modified; otherwise, [fetPropVal].
     */
    @Contract("!null,!null->!null")
    fun retainIfModified( curPropVal: String?, fetPropVal: String? ): String? =
        if( curPropVal?.startsWith( MODIFIED_PREFIX, true ) == true )
            curPropVal
        else
            fetPropVal
}