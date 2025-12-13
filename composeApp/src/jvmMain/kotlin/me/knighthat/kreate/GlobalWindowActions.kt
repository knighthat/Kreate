package me.knighthat.kreate

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.window.ApplicationScope
import kotlin.system.exitProcess


object GlobalWindowActions {

    fun closeApplication( application: ApplicationScope ) = application.exitApplication()

    fun handleKeyEvent( application: ApplicationScope, event: KeyEvent ): Boolean {
        if( event.isCtrlPressed
            && event.type == KeyEventType.KeyDown
            && event.key == Key.Q
        ) {
            closeApplication( application )

            // Even though this is not necessary, but return true
            // here to mark that this event is being consumed.
            return true
        }

        return false
    }
}