package me.knighthat.component.export

import android.content.Context
import android.net.Uri
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import app.kreate.android.BuildConfig
import app.kreate.android.R
import app.kreate.di.PrefType
import app.kreate.di.Storage
import com.github.doyaaaaaken.kotlincsv.dsl.csvWriter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import me.knighthat.component.ExportToFileDialog
import org.koin.java.KoinJavaComponent.get
import java.io.OutputStream

class ExportSettingsDialog private constructor(
    valueState: MutableState<TextFieldValue>,
    activeState: MutableState<Boolean>,
    launcher: ManagedActivityResultLauncher<String, Uri?>
): ExportToFileDialog(valueState, activeState, launcher) {

    companion object {
        private suspend fun onExportToCsv( outStream: OutputStream ) {
            val preferences = get<Storage>(Storage::class.java, PrefType.DEFAULT)
            val entries = preferences.data.firstOrNull()?.asMap().orEmpty()

            csvWriter().open( outStream ) {
                writeRow( "Type", "Key", "Value" )

                entries.forEach { (k, v) ->
                    val type: String
                    val value: String
                    when( v ) {
                        is Set<*> -> {
                            type = "string_set"
                            value = v.joinToString(",") { it.toString() }
                        }
                        else -> {
                            type = v::class.simpleName ?: ""
                            value = v.toString()
                        }
                    }

                    writeRow(type, k.name, value)
                }

                close()
            }
        }

        @Composable
        operator fun invoke( context: Context ): ExportSettingsDialog =
            ExportSettingsDialog(
                remember {
                    mutableStateOf( TextFieldValue() )
                },
                rememberSaveable { mutableStateOf(false) },
                rememberLauncherForActivityResult(
                    ActivityResultContracts.CreateDocument( "text/csv" )
                ) { uri ->
                    // [uri] must be non-null (meaning path exists) in order to work
                    uri ?: return@rememberLauncherForActivityResult

                    // Run in background to prevent UI thread
                    // from freezing due to large file.
                    CoroutineScope(Dispatchers.IO).launch {
                        context.contentResolver
                               .openOutputStream( uri )
                               ?.use {
                                   onExportToCsv( it )
                               }
                    }
                }
            )
    }

    override val extension: String = "csv"
    override val dialogTitle: String
        @Composable
        get() = stringResource( R.string.title_export_settings )

    override fun defaultFileName(): String = "${BuildConfig.APP_NAME}_settings"
}