package app.kreate.android.themed.common.screens.settings.other

import android.content.Context
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kreate.android.themed.common.component.dialog.CrashReportDialog
import app.kreate.android.themed.common.component.settings.SettingEntrySearch
import app.kreate.android.themed.common.component.settings.header
import app.kreate.android.themed.common.screens.settings.StorageSizeEntry
import app.kreate.components.settings.EnumEntry
import app.kreate.components.settings.ListEntry
import app.kreate.components.settings.NumberPickerEntry
import app.kreate.components.settings.SettingComponents
import app.kreate.compose.R
import app.kreate.preferences.Preferences
import app.kreate.util.getRuntimeLogDir
import app.kreate.utils.Toaster
import co.touchlab.kermit.Logger
import it.fast4x.rimusic.colorPalette
import it.fast4x.rimusic.utils.textCopyToClipboard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.ExperimentalSerializationApi
import kreate.resources.generated.resources.Res
import kreate.resources.generated.resources.file
import me.knighthat.utils.TimeDateUtils
import java.io.File
import java.util.UUID
import kotlin.io.path.createTempFile

@Composable
private fun CopyLogIcon(
    context: Context,
    logFile: () -> File,
    isEnabled: Boolean,
) {
    fun copyFileToClipboard() {
        CoroutineScope(Dispatchers.IO ).launch {
            val logText = context.contentResolver.openInputStream( logFile().toUri() )?.bufferedReader()?.readText()

            if( logText.isNullOrBlank() )
                Toaster.w( R.string.no_log_available )
            else
                textCopyToClipboard( logText, context )
        }
    }

    Icon(
        painter = painterResource( R.drawable.copy ),
        contentDescription = stringResource( R.string.copy_log_to_clipboard ),
        tint = colorPalette().background4,
        modifier = Modifier.size(24.dp)
                           .clickable(
                               enabled = isEnabled,
                               onClick = ::copyFileToClipboard
                           )
    )
}

@OptIn(ExperimentalSerializationApi::class)
fun LazyListScope.debugSection(search: SettingEntrySearch ) {
    header(
        titleId = R.string.debug,
        subtitle = { stringResource( R.string.restarting_rimusic_is_required ) }
    )
    item {
        val context = LocalContext.current
        var from by remember { mutableStateOf<File?>( null ) }
        val launcher = rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument( "text/plain" )
        ) { uri ->
            if( from == null || uri == null ) {
                Logger.e( tag = "Logs" ) { "Can't export from `null` or to `null` file" }
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

        if( search appearsIn R.string.setting_entry_crash_log ) {
            val crashReportDialog = remember( context ) { CrashReportDialog(context) }

            SettingComponents.Entry(
                title = stringResource( R.string.setting_entry_crash_log ),
                subtitle = stringResource( R.string.setting_description_copy_or_export_logs ),
                enabled = crashReportDialog.isAvailable(),
                onClick = {
                    from = crashReportDialog.crashlogFile
                    launcher.launch( crashReportDialog.crashlogFile.name )
                }
            ) {
                CopyLogIcon( context, crashReportDialog::crashlogFile, crashReportDialog.isAvailable() )
            }
        }

        if( search appearsIn R.string.setting_entry_runtime_log )
            SettingComponents.BooleanEntry(
                preference = Preferences.RUNTIME_LOG,
                title = stringResource( R.string.setting_entry_runtime_log ),
                subtitle = stringResource( R.string.setting_description_runtime_log, stringResource(app.kreate.resources.R.string.app_name) ),
                action = SettingComponents.Action.RESTART_APP
            )
        val isRuntimeLogEnabled by Preferences.RUNTIME_LOG.collectAsStateWithLifecycle()
        AnimatedVisibility(
            visible = isRuntimeLogEnabled,
            modifier = Modifier.padding( start = SettingComponents.CHILDREN_PADDING.dp )
        ) {
            Column {
                if( search appearsIn R.string.setting_entry_enable_runtime_log_share )
                    SettingComponents.BooleanEntry(
                        preference = Preferences.RUNTIME_LOG_SHARED,
                        title = stringResource( R.string.setting_entry_enable_runtime_log_share ),
                        subtitle = stringResource(
                            if( Preferences.RUNTIME_LOG_SHARED.value )
                                R.string.setting_description_runtime_log_share_on
                            else
                                R.string.setting_description_runtime_log_share_off
                        ),
                        action = SettingComponents.Action.RESTART_APP
                    )

                if( search appearsIn R.string.setting_entry_runtime_log_level )
                    SettingComponents.EnumEntry(
                        preference = Preferences.RUNTIME_LOG_SEVERITY,
                        title = stringResource( R.string.setting_entry_runtime_log_level )
                    )

                if( search appearsIn R.string.setting_entry_runtime_log_file_count ) {
                    val fileCount by Preferences.RUNTIME_LOG_FILE_COUNT.collectAsStateWithLifecycle()

                    SettingComponents.NumberPickerEntry(
                        preferences = Preferences.RUNTIME_LOG_FILE_COUNT,
                        unit = Res.plurals.file,
                        title = stringResource( R.string.setting_entry_runtime_log_file_count ),
                        subtitle = stringResource(
                            R.string.string_description_runtime_log_file_count,
                            pluralStringResource( R.plurals.file, fileCount, fileCount )
                        ),
                    )
                }

                if( search appearsIn R.string.settings_entry_runtime_log_max_size_per_file ) {
                    val maxSizePerFile by Preferences.RUNTIME_LOG_MAX_SIZE_PER_FILE.collectAsStateWithLifecycle()
                    val sizeString by remember { derivedStateOf {
                        Formatter.formatShortFileSize(context, maxSizePerFile)
                    } }
                    SettingComponents.StorageSizeEntry(
                        context = context,
                        preference = Preferences.RUNTIME_LOG_MAX_SIZE_PER_FILE,
                        title = stringResource( R.string.settings_entry_runtime_log_max_size_per_file ),
                        subtitle = stringResource( R.string.setting_description_runtime_log_max_size_per_file, sizeString ),
                        currentValue = maxSizePerFile,
                        action = SettingComponents.Action.RESTART_APP
                    )
                }

                if(  search appearsIn R.string.setting_entry_runtime_log ) {
                    val logFiles = remember {
                        val fileNameFormat = TimeDateUtils.logFileName()
                        getRuntimeLogDir()
                            .toFile()
                            .listFiles()
                            .orEmpty()
                            .sortedByDescending {
                                try {
                                    fileNameFormat.parse( it.nameWithoutExtension )
                                } catch( _: Exception ) {
                                    null
                                }
                            }
                            .toTypedArray()
                    }

                    SettingComponents.ListEntry(
                        entries = logFiles,
                        title = stringResource( R.string.setting_entry_runtime_log ),
                        subtitle = stringResource( R.string.setting_description_copy_or_export_logs ),
                        getName = { it.nameWithoutExtension },
                        selected = createTempFile( UUID.randomUUID().toString() ).toFile(),
                        action = SettingComponents.Action.NONE,
                        trailingContent = {
                            CopyLogIcon( context, logFiles::first, true )
                        },
                        onConfirmRequest = {
                            from = it
                            launcher.launch( it.name )
                        }
                    )
                }
            }
        }
    }
}