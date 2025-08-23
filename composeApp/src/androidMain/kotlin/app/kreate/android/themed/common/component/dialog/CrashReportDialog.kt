package app.kreate.android.themed.common.component.dialog

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastJoinToString
import androidx.compose.ui.util.fastMaxOfOrNull
import androidx.core.net.toUri
import app.kreate.android.R
import app.kreate.android.utils.CrashHandler
import it.fast4x.rimusic.ui.styling.LocalAppearance
import it.fast4x.rimusic.utils.textCopyToClipboard
import me.knighthat.utils.Repository
import me.knighthat.utils.TimeDateUtils
import java.io.File

class CrashReportDialog(private val context: Context): Dialog() {

    override val dialogTitle: String
        @Composable
        get() = stringResource( R.string.dialog_title_crash_report )

    lateinit var crashlogFile: File

    init {
        val dateFormat = TimeDateUtils.logFileName()
        val dateFileMapping = CrashHandler.getDir( context ).listFiles()?.associateBy {
            val matchResult = CrashHandler.fileNameRegex.find( it.name )

            if( matchResult == null || matchResult.groups.isEmpty() )
                null
            else
                dateFormat.parse( matchResult.groups[1]!!.value )
        }.orEmpty()

        dateFileMapping.keys
                       .filterNotNull()
                       .fastMaxOfOrNull { it }
                       ?.let( dateFileMapping::get )
                       ?.also { crashlogFile = it }
    }

    fun isAvailable(): Boolean = ::crashlogFile.isInitialized

    fun openCrashlogFile() =
        context.contentResolver.openInputStream( crashlogFile.toUri() )

    fun crashlogText(): String =
        openCrashlogFile()?.bufferedReader()
                          ?.readLines()
                          .orEmpty()
                          .fastJoinToString( "" ) { it }

    override fun showDialog() {
        if( isAvailable() )
            super.showDialog()
    }

    override fun hideDialog() {
        super.hideDialog()

        if( isAvailable() )
            crashlogFile.deleteOnExit()
    }

    @Composable
    override fun DialogBody() {
        val (colorPalette, typography) = LocalAppearance.current

        Box(
            Modifier.background( colorPalette.background0 )
                    .fillMaxWidth( .9f )
                    .heightIn( max = 150.dp )
        ) {
            Column(
                Modifier.verticalScroll(rememberScrollState() )
                        .fillMaxSize()
                        .padding( all = 10.dp )
            ) {
                openCrashlogFile()?.bufferedReader()
                                  ?.readLines()
                                  .orEmpty()
                                  .forEach {
                                      BasicText(
                                          text = it,
                                          style = typography.xs
                                      )
                                  }
            }
        }
    }

    @Composable
    override fun DialogFooter() {
        val (colorPalette, typography) = LocalAppearance.current

        Row(
            horizontalArrangement = Arrangement.End,
            modifier = Modifier.fillMaxWidth( .9f )
        ) {
            Button(
                onClick = ::hideDialog,
                colors = ButtonDefaults.buttonColors().copy(
                    containerColor = Color.Transparent
                )
            ) {
                Text(
                    text = stringResource( R.string.dialog_button_dont_report ),
                    style = typography.xs,
                    color = colorPalette.accent
                )
            }

            val uriHandler = LocalUriHandler.current
            Button(
                onClick = {
                    hideDialog()

                    textCopyToClipboard( crashlogText(), context )

                    uriHandler.openUri(
                        with(Repository ) {
                            "$REPO_URL$ISSUE_TEMPLATE_PATH"
                        }
                    )
                },
                colors = ButtonDefaults.buttonColors().copy(
                    containerColor = colorPalette.accent
                )
            ) {
                Text(
                    text = stringResource( R.string.dialog_button_open_report_ticket ),
                    style = typography.xs,
                    color = colorPalette.onAccent
                )
            }
        }
    }
}