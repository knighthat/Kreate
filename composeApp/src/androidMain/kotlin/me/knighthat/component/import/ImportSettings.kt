package me.knighthat.component.import

import android.content.Context
import android.net.Uri
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastMapNotNull
import androidx.core.content.edit
import app.kreate.android.Preferences
import com.github.doyaaaaaken.kotlincsv.dsl.csvReader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.knighthat.component.ImportFromFile
import me.knighthat.component.dialog.RestartAppDialog
import me.knighthat.utils.csv.PreferenceCSV
import java.io.InputStream

class ImportSettings private constructor(
    launcher: ManagedActivityResultLauncher<Array<String>, Uri?>
): ImportFromFile(launcher) {

    companion object {
        fun onImportFromCsv( inStream: InputStream ) =
            csvReader().readAllWithHeader( inStream )
                       .fastMapNotNull { row ->
                           val type = row["Type"]
                           val key = row["Key"]
                           val value = row["Value"].orEmpty()

                           if(
                               type.isNullOrBlank()
                               || key.isNullOrBlank()
                               || (type.lowercase() == "string" && value.isBlank())
                           )
                               null
                           else
                               PreferenceCSV(type, key, value)
                       }
                       .also { preferences ->
                           Preferences.preferences.edit( true ) {
                               preferences.fastForEach { (type, key, value) ->
                                   val valueStr = value.toString()
                                   when( type.lowercase() ) {
                                       "string" -> putString( key, valueStr )
                                       "int" -> putInt( key, valueStr.toInt() )
                                       "long" -> putLong( key, valueStr.toLong() )
                                       "float" -> putFloat( key, valueStr.toFloat() )
                                       "boolean" -> putBoolean( key, valueStr.toBoolean() )
                                   }
                               }
                           }
                       }

        @Composable
        operator fun invoke( context: Context ): ImportSettings =
            ImportSettings(
                rememberLauncherForActivityResult(
                    ActivityResultContracts.OpenDocument()
                ) { uri ->
                    // [uri] must be non-null (meaning path exists) in order to work
                    uri ?: return@rememberLauncherForActivityResult

                    // Run in background to prevent UI thread
                    // from freezing due to large file.
                    CoroutineScope(Dispatchers.IO).launch {
                        context.contentResolver
                               .openInputStream( uri )
                               ?.use( ::onImportFromCsv )

                        RestartAppDialog.showDialog()
                    }
                }
            )
    }

    override val supportedMimes: Array<String> = arrayOf(
        "text/csv",
        "text/comma-separated-values",
        "application/vnd.ms-excel",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    )
}