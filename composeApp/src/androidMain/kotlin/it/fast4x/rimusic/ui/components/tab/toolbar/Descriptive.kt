package it.fast4x.rimusic.ui.components.tab.toolbar

import androidx.annotation.StringRes
import app.kreate.utils.Toaster

interface Descriptive: Clickable {

    @get:StringRes
    val messageId: Int

    /**
     * What happens when user holds this icon for a while.
     *
     * By default, this will send out message
     */
    override fun onLongClick() = Toaster.i(messageId)
}