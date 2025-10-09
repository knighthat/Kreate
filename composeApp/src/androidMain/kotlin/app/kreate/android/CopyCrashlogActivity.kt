package app.kreate.android

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import app.kreate.android.themed.common.component.dialog.CrashReportDialog
import it.fast4x.rimusic.utils.textCopyToClipboard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


class CopyCrashlogActivity: AppCompatActivity() {

    override fun onCreate( savedInstanceState: Bundle? ) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launch( Dispatchers.IO ) {
            val appContext = this@CopyCrashlogActivity.applicationContext
            var logText: String? = null

            val report = CrashReportDialog( appContext )
            if( report.isAvailable() )
                logText = contentResolver.openInputStream(report.crashlogFile.toUri() )
                                         ?.bufferedReader()
                                         ?.readText()

            with( Dispatchers.Main ) {
                if( !logText.isNullOrBlank() )
                    textCopyToClipboard( logText, appContext )
            }

            finish()
        }
    }
}