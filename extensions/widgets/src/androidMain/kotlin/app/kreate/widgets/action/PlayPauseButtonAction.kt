package app.kreate.widgets.action

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.media3.common.Player
import co.touchlab.kermit.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.get


class PlayPauseButtonAction : ActionCallback, KoinComponent {

    companion object {

        val IS_PLAYING_KEY = ActionParameters.Key<Boolean>("IS_PLAYING")
    }

    private val logger = Logger.withTag( "PlayPauseButtonAction" )

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        logger.d { "Request from $glanceId with parameters: $parameters" }

        val isPlaying = parameters[IS_PLAYING_KEY]
        if( isPlaying == null ) {
            logger.w { "Missing ${IS_PLAYING_KEY.name} parameter!" }
            return
        }

        val player = get<Player>()
        withContext( Dispatchers.Main ) {
            if( isPlaying ) player.pause() else player.play()
        }
    }
}