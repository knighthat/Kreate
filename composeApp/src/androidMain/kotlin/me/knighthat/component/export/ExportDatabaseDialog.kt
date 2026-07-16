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
import app.kreate.compose.R
import app.kreate.database.Database
import app.kreate.di.DATABASE_FILENAME
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.knighthat.component.ExportToFileDialog
import me.knighthat.utils.TimeDateUtils
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import java.io.FileInputStream

class ExportDatabaseDialog private constructor(
    valueState: MutableState<TextFieldValue>,
    activeState: MutableState<Boolean>,
    launcher: ManagedActivityResultLauncher<String, Uri?>
): ExportToFileDialog(valueState, activeState, launcher), KoinComponent {

    companion object {
        @Composable
        operator fun invoke( context: Context ): ExportDatabaseDialog =
            ExportDatabaseDialog(
                remember {
                    mutableStateOf( TextFieldValue() )
                },
                rememberSaveable { mutableStateOf(false) },
                rememberLauncherForActivityResult(
                    ActivityResultContracts.CreateDocument("application/vnd.sqlite3")
                ) { uri ->
                    // [uri] must be non-null (meaning path exists) in order to work
                    uri ?: return@rememberLauncherForActivityResult

                    /*
                        WAL [Database#checkpoint] shouldn't be running inside a `query` or `transaction` block
                        because it requires all commits to be finalized and written to
                        base file.
                     */
                    CoroutineScope(Dispatchers.IO).launch {
                        Database.checkpoint()

                        context.applicationContext
                               .contentResolver
                               .openOutputStream(uri)
                               ?.use { outStream ->
                                   val dbFile = context.getDatabasePath( DATABASE_FILENAME )
                                   FileInputStream(dbFile).use { inStream ->
                                       inStream.copyTo(outStream)
                                   }
                               }
                    }
                }
            )
    }

    override val extension: String = "sqlite"
    override val dialogTitle: String
        @Composable
        get() = stringResource( R.string.title_name_your_export )

    override fun defaultFileName(): String =
        "${get<Context>().getString(app.kreate.resources.R.string.app_name)}_database_${TimeDateUtils.localizedDateNoDelimiter()}_${TimeDateUtils.timeNoDelimiter()}"
}