package app.kreate.android.themed.common.component.settings

import android.content.Context
import android.content.Intent
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import app.kreate.android.R
import it.fast4x.rimusic.service.modern.PlayerServiceModern
import org.koin.compose.koinInject

object RestartPlayerService {

    private var active: Boolean by mutableStateOf( false )

    fun requestRestart() { active = true }

    @OptIn(UnstableApi::class)
    @Composable
    fun Render(
        modifier: Modifier = Modifier,
        context: Context = koinInject()
    ) {
        AnimatedVisibility(
            visible = active,
            enter = slideInHorizontally { it } + fadeIn(),
            exit = slideOutHorizontally { it } + fadeOut(),
            modifier = modifier
        ) {
            Icon(
                painter = painterResource( R.drawable.restart_player ),
                contentDescription = stringResource( R.string.info_restart_player_service ),
                tint = colorResource( R.color.important ),
                modifier = Modifier.size( 24.dp ).clickable {
                    val intent = Intent(context, PlayerServiceModern::class.java)
                        .setAction( PlayerServiceModern.ACTION_RESTART )
                    context.startService( intent )
                    active = false
                }
            )
        }
    }

}