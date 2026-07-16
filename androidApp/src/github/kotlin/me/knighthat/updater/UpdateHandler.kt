package me.knighthat.updater

import android.os.Environment
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kreate.util.IS_DEBUG
import it.fast4x.rimusic.enums.CheckUpdateState
import java.io.File

@Composable
fun UpdateHandler() {
    val context = LocalContext.current

    DownloadAndInstallDialog.Render()
    NewUpdatePrompt.Render()

    val check4UpdateState by app.kreate.preferences.Preferences.CHECK_UPDATE.collectAsStateWithLifecycle()
    LaunchedEffect( check4UpdateState ) {
        if( check4UpdateState != CheckUpdateState.DISABLED )
            Updater.checkForUpdate( context )
    }

    LaunchedEffect( Unit ) {
        val filename = Updater.getFileName()
        val apkFile = File(
            context.getExternalFilesDir( Environment.DIRECTORY_DOWNLOADS ),
            filename
        )
        if( apkFile.exists() && !IS_DEBUG)
            apkFile.delete()
    }
}