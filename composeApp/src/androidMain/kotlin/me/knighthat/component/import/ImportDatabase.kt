package me.knighthat.component.import

import android.content.Context
import android.net.Uri
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import app.kreate.database.Database
import app.kreate.di.DATABASE_FILENAME
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.knighthat.component.ImportFromFile
import me.knighthat.component.dialog.RestartAppDialog
import java.io.FileOutputStream

class ImportDatabase private constructor(
    launcher: ManagedActivityResultLauncher<Array<String>, Uri?>
): ImportFromFile(launcher) {

    companion object {
        @Composable
        operator fun invoke( context: Context ): ImportDatabase =
            ImportDatabase(
                rememberLauncherForActivityResult(
                    ActivityResultContracts.OpenDocument()
                ) { uri ->
                    // [uri] must be non-null (meaning path exists) in order to work
                    uri ?: return@rememberLauncherForActivityResult

                    /*
                        WAL [Database#checkpoint] shouldn't be running inside a `query` or `transaction` block
                        because it requires all commits to be finalized and written to
                        base file.
                     */
                    CoroutineScope( Dispatchers.IO ).launch {
                        Database.checkpoint()
                        Database.close()

                        context.applicationContext
                               .contentResolver
                               .openInputStream(uri)
                               ?.use { inStream ->
                                   val dbFile = context.getDatabasePath( DATABASE_FILENAME )
                                   FileOutputStream( dbFile ).use { outStream ->
                                       inStream.copyTo(outStream)
                                   }
                               }

                        RestartAppDialog.showDialog()
                    }
                }
            )
    }

    override val supportedMimes: Array<String> = arrayOf(
        "application/vnd.sqlite3",
        "application/x-sqlite3",
        "application/octet-stream"
    )
}