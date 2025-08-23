package app.kreate.android.themed.common.screens.settings.other

import android.content.Context
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import app.kreate.android.Preferences
import app.kreate.android.R
import app.kreate.android.themed.common.component.dialog.CrashReportDialog
import app.kreate.android.themed.common.component.settings.SettingComponents
import app.kreate.android.themed.common.component.settings.SettingEntrySearch
import app.kreate.android.themed.common.component.settings.header
import app.kreate.android.themed.common.screens.settings.StorageSizeEntry
import app.kreate.android.utils.logging.RollingFileLoggingTree
import it.fast4x.rimusic.colorPalette
import it.fast4x.rimusic.utils.textCopyToClipboard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.ExperimentalSerializationApi
import me.knighthat.component.dialog.InputDialogConstraints
import me.knighthat.utils.Toaster
import timber.log.Timber
import java.io.File

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

        if( search appearsIn R.string.enable_log_debug )
            SettingComponents.BooleanEntry(
                preference = Preferences.RUNTIME_LOG,
                title = stringResource( R.string.enable_log_debug ),
                subtitle = stringResource( R.string.if_enabled_create_a_log_file_to_highlight_errors ),
                action = SettingComponents.Action.RESTART_APP
            )
        AnimatedVisibility(
            visible = Preferences.RUNTIME_LOG.value,
            modifier = Modifier.padding( start = SettingComponents.CHILDREN_PADDING.dp )
        ) {
            Column {
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

                SettingComponents.ListEntry(
                    preference = Preferences.RUNTIME_LOG_LEVEL,
                    title = stringResource( R.string.setting_entry_runtime_log_level ),
                    getName = {
                        RollingFileLoggingTree.levelStringMapping[it]!!
                                              .lowercase()
                                              .replaceFirstChar( Char::uppercase )
                    },
                    getList = {
                        RollingFileLoggingTree.levelStringMapping.keys.sorted().toTypedArray()
                    },
                    subtitle = RollingFileLoggingTree.levelStringMapping[Preferences.RUNTIME_LOG_LEVEL.value]!!,
                    action = SettingComponents.Action.NONE
                )

                val fileCount by Preferences.RUNTIME_LOG_FILE_COUNT
                SettingComponents.InputDialogEntry(
                    preference = Preferences.RUNTIME_LOG_FILE_COUNT,
                    title = stringResource( R.string.setting_entry_runtime_log_file_count ),
                    subtitle = stringResource(
                        R.string.string_description_runtime_log_file_count,
                        pluralStringResource( R.plurals.file, fileCount, fileCount )
                    ),
                    constraint = InputDialogConstraints.POSITIVE_INTEGER,
                    keyboardOption = KeyboardOptions(keyboardType = KeyboardType.Number),
                    action = SettingComponents.Action.RESTART_APP
                )

                val maxSizePerFile by Preferences.RUNTIME_LOG_MAX_SIZE_PER_FILE
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
        }
    }
}