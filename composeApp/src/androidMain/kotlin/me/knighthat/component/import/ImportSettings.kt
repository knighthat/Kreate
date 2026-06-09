package me.knighthat.component.import

import android.content.Context
import android.net.Uri
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.util.fastMapNotNull
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import app.kreate.di.PrefType
import app.kreate.di.Storage
import com.github.doyaaaaaken.kotlincsv.dsl.csvReader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.knighthat.component.ImportFromFile
import me.knighthat.component.dialog.RestartAppDialog
import org.koin.java.KoinJavaComponent.get
import java.io.InputStream

class ImportSettings private constructor(
    launcher: ManagedActivityResultLauncher<Array<String>, Uri?>
): ImportFromFile(launcher) {

    companion object {
        suspend fun onImportFromCsv( inStream: InputStream ) =
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
                               Triple(type, key, value)
                       }
                       .also { entries ->
                           get<Storage>(Storage::class.java, PrefType.DEFAULT).edit { file ->
                               // Clear old app configurations so the backup replaces everything
                               file.clear()

                               entries.forEach { (type, key, value) ->
                                   when( type.lowercase() ) {
                                       "string_set" -> {
                                           val key = stringSetPreferencesKey(key)
                                           val value = value.split(",").toSet()
                                           file[key] = value
                                       }
                                       "string" -> {
                                           val key = stringPreferencesKey(key)
                                           file[key] = value
                                       }
                                       "int" -> {
                                           val key = intPreferencesKey(key)
                                           val value = value.toIntOrNull() ?: return@forEach
                                           file[key] = value
                                       }
                                       "long" -> {
                                           val key = longPreferencesKey(key)
                                           val value = value.toLongOrNull() ?: return@forEach
                                           file[key] = value
                                       }
                                       "float" -> {
                                           val key = floatPreferencesKey(key)
                                           val value = value.toFloatOrNull() ?: return@forEach
                                           file[key] = value
                                       }
                                       "boolean" -> {
                                           val key = booleanPreferencesKey(key)
                                           val value = value.toBooleanStrictOrNull() ?: return@forEach
                                           file[key] = value
                                       }
                                       "double" -> {
                                           val key = doublePreferencesKey(key)
                                           val value = value.toDoubleOrNull() ?: return@forEach
                                           file[key] = value
                                       }
                                       // Ignore unapproved types
                                       else -> return@forEach
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
                               ?.use {
                                   onImportFromCsv( it )
                               }

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