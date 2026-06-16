package app.kreate.components.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue


class ActionHandlerImpl : ActionHandler {

    override fun requestRestartApp() {
        RestartApp.isVisible = true
    }

    override fun requestRestartPlaybackService() {
        RestartPlaybackService.isVisible = true
    }

    object RestartApp {

        var isVisible by mutableStateOf( false )
    }

    object RestartPlaybackService {

        var isVisible by mutableStateOf( false )
    }
}