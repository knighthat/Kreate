package app.kreate.android

import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import it.fast4x.rimusic.Database
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.knighthat.utils.TimeDateUtils
import java.io.FileInputStream

class ExportDatabaseActivity : AppCompatActivity() {

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
                    val dbFile = applicationContext.getDatabasePath(Database.FILE_NAME)
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
                exportLauncher.launch("${BuildConfig.APP_NAME}_database_${TimeDateUtils.localizedDateNoDelimiter()}_${TimeDateUtils.timeNoDelimiter()}")
            }
        }
    }
}