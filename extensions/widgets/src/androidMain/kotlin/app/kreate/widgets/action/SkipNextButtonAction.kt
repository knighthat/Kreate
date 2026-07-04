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


internal class SkipNextButtonAction : ActionCallback, KoinComponent {

    private val logger = Logger.withTag( "SkipNextButtonAction" )

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        logger.d { "Request from $glanceId with parameters: $parameters" }

        withContext( Dispatchers.Main ) {
            get<Player>().seekToNextMediaItem()
        }
    }
}