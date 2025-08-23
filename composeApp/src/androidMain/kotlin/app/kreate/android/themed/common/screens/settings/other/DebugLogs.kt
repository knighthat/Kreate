package app.kreate.android.themed.common.screens.settings.other

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import app.kreate.android.Preferences
import app.kreate.android.R
import app.kreate.android.themed.common.component.dialog.CrashReportDialog
import app.kreate.android.themed.common.component.settings.SettingComponents
import app.kreate.android.themed.common.component.settings.SettingEntrySearch
import it.fast4x.rimusic.colorPalette
import it.fast4x.rimusic.utils.textCopyToClipboard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.knighthat.utils.Toaster
import timber.log.Timber
import java.io.File

const val RUNTIME_LOGS_FILENAME = "runtime.log"

@Composable
private fun Entry(
    context: Context,
    @StringRes titleId: Int,
    @StringRes subtitleId: Int,
    isEnabled: Boolean,
    onExport: () -> Unit,
    logText: () -> String
) =
    SettingComponents.Text(
        title = stringResource( titleId ),
        onClick = onExport,
        subtitle = stringResource( subtitleId ),
        isEnabled = isEnabled
    ) {
        Icon(
            painter = painterResource( R.drawable.copy ),
            contentDescription = stringResource( R.string.copy_log_to_clipboard ),
            tint = colorPalette().background4,
            modifier = Modifier.size( 24.dp )
                               .clickable( enabled = isEnabled ) {
                                   val text = logText()
                                   if ( text.isEmpty() )
                                       Toaster.w( R.string.no_log_available )
                                   else
                                       textCopyToClipboard( text, context )
                               }
        )
    }

@Composable
fun DebugLogs( context: Context, search: SettingEntrySearch ) {
    var from by remember { mutableStateOf<File?>( null ) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument( "text/plain" )
    ) { uri ->
        if( from == null || uri == null ) {
            Timber.tag( "Logs" ).e( "Can't export from `null` or to `null` file" )
            Toaster.e( R.string.error_failed_to_export_logs )
            return@rememberLauncherForActivityResult
        }

        // Write in background to prevent UI from freezing up when there's a large file
        CoroutineScope( Dispatchers.IO ).launch {
            context.contentResolver.openInputStream( from!!.toUri() )?.use { inputStream ->
                context.contentResolver.openOutputStream( uri )?.use { outputStream ->
                    inputStream.copyTo( outputStream )
                }
            }

            Toaster.done()
        }
    }

    if( search appearsIn R.string.setting_entry_logs ) {
        val logFile = File(context.filesDir.resolve( "logs" ), RUNTIME_LOGS_FILENAME)
        Entry(
            context = context,
            titleId = R.string.setting_entry_logs,
            subtitleId = R.string.setting_description_copy_or_export_logs,
            isEnabled = Preferences.RUNTIME_LOG.value,
            onExport = {
                from = logFile
                launcher.launch( RUNTIME_LOGS_FILENAME )
            },
            logText = {
                val lines = if ( logFile.exists() && logFile.canRead() )
                    logFile.readLines()
                else
                    emptyList()

                lines.joinToString( "\n" )
            }
        )
    }

    if( search appearsIn R.string.setting_entry_crash_log ) {
        val crashReportDialog = remember( context ) { CrashReportDialog(context) }
        Entry(
            context = context,
            titleId = R.string.setting_entry_crash_log,
            subtitleId = R.string.setting_description_copy_or_export_logs,
            isEnabled = crashReportDialog.isAvailable(),
            onExport = {
                from = crashReportDialog.crashlogFile
                launcher.launch( crashReportDialog.crashlogFile.name )
            },
            logText = crashReportDialog::crashlogText
        )
    }
}