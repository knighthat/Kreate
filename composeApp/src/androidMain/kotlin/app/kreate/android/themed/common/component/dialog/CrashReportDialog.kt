package app.kreate.android.themed.common.component.dialog

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastMaxOfOrNull
import androidx.core.net.toUri
import app.kreate.android.R
import app.kreate.android.utils.CrashHandler
import it.fast4x.rimusic.ui.styling.LocalAppearance
import it.fast4x.rimusic.utils.textCopyToClipboard
import me.knighthat.utils.Repository
import me.knighthat.utils.TimeDateUtils
import me.knighthat.utils.Toaster
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

    private fun writeCrashlogToClipboard() =
        openCrashlogFile()?.bufferedReader( Charsets.UTF_8 )
                          ?.readText()
                          ?.also { textCopyToClipboard( it, context ) }

    fun isAvailable(): Boolean = ::crashlogFile.isInitialized

    fun openCrashlogFile() =
        context.contentResolver.openInputStream( crashlogFile.toUri() )

    override fun showDialog() {
        if( isAvailable() )
            super.showDialog()
    }

    @Composable
    override fun DialogBody() {
        val (colorPalette, typography) = LocalAppearance.current

        Box(
            Modifier.background( colorPalette.background0 )
                    .fillMaxWidth( .9f )
                    .heightIn( max = 150.dp )
                    .border( 1.dp, colorPalette.text.copy( .2f ) )
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

            IconButton(
                onClick = {
                    writeCrashlogToClipboard()

                    Toaster.s( R.string.value_copied )
                },
                modifier = Modifier.padding( 8.dp )
                                   .align( Alignment.TopEnd )
            ) {
                Icon(
                    painter = painterResource( R.drawable.copy ),
                    tint = colorPalette.textSecondary,
                    contentDescription = stringResource( R.string.copy_log_to_clipboard ),
                    modifier = Modifier.background( colorPalette.background1 )
                        .padding( 8.dp )
                        .clip( CircleShape )
                )
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

                    writeCrashlogToClipboard()

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