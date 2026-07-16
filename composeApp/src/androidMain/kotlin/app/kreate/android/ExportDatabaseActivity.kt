package app.kreate.android

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import app.kreate.database.Database
import app.kreate.di.DATABASE_FILENAME
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.knighthat.utils.TimeDateUtils
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import java.io.FileInputStream

class ExportDatabaseActivity : AppCompatActivity(), KoinComponent {

    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.sqlite3")
    ) { uri ->
        if (uri == null) {
            finish()
            return@registerForActivityResult
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Perform the copy operation in the background
                applicationContext.contentResolver.openOutputStream(uri)?.use { outStream ->
                    val dbFile = applicationContext.getDatabasePath( DATABASE_FILENAME )
                    FileInputStream(dbFile).use { inStream ->
                        inStream.copyTo(outStream)
                    }
                }

                // Optional: Show success message on Main thread
                launch(Dispatchers.Main) {
                    Toast.makeText(applicationContext, "Database exported!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                finish() // Close activity when done
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 2. Prepare the database (checkpoint ensures all data is written to disk)
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                Database.checkpoint()
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // 3. Launch the file picker (must be done on Main thread)
            launch(Dispatchers.Main) {
                // You can set a default filename here
                exportLauncher.launch("${get<Context>().getString(app.kreate.resources.R.string.app_name)}_database_${TimeDateUtils.localizedDateNoDelimiter()}_${TimeDateUtils.timeNoDelimiter()}")
            }
        }
    }
}