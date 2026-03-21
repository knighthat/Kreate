package me.knighthat.component.dialog

import android.app.Activity
import android.content.Context
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.kreate.android.BuildConfig
import app.kreate.android.R
import app.kreate.android.service.playback.PlaybackService
import it.fast4x.rimusic.colorPalette
import it.fast4x.rimusic.service.MyDownloadService
import it.fast4x.rimusic.typography
import it.fast4x.rimusic.utils.intent
import it.fast4x.rimusic.utils.medium
import org.koin.java.KoinJavaComponent.inject
import kotlin.system.exitProcess

object RestartAppDialog: ConfirmDialog {

    override val dialogTitle: String
        @Composable
        get() = stringResource( R.string.title_restart_required )

    override var isActive: Boolean by mutableStateOf(false)

    override fun hideDialog() = onConfirm()

    override fun onConfirm() {
        val context: Context by inject(Context::class.java)

        context.stopService( context.intent<PlaybackService>() )
        context.stopService( context.intent<MyDownloadService>() )

        // Close other activities
        (context as? Activity)?.finishAffinity()

        // Close app with exit 0 notify that no problem occurred
        exitProcess( 0 )
    }

    @Composable
    override fun Buttons() {
        BasicText(
            text = stringResource( R.string.confirm ),
            style = typography().xs
                                .medium
                                .copy(
                                    color = colorPalette().accent,
                                    textAlign = TextAlign.Center
                                ),
            modifier = InteractiveDialog.ButtonModifier()
                                        .fillMaxWidth( .98f )       // Creates some space between buttons
                                        .border(
                                            width = 2.dp,
                                            color = colorPalette().accent,
                                            shape = RoundedCornerShape(20)
                                        )
                                        .padding( vertical = 10.dp )
                                        .clickable( onClick = ::onConfirm )
        )
    }

    @Composable
    override fun DialogBody() {
        BasicText(
            text = stringResource( R.string.restart_dialog_body, BuildConfig.APP_NAME ),
            style = typography().xs.copy( color = colorPalette().text ),
            modifier = Modifier.padding( vertical = 20.dp )
        )
    }
}