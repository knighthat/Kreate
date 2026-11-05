/*
 * Kreate
 * Copyright (C) 2026 KnightHat
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package me.knighthat.kreate3.logging

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.knighthat.kreate3.AppLanguage
import me.knighthat.kreate3.KreateActivity
import me.knighthat.kreate3.R
import me.knighthat.kreate3.ui.component.PreviewTheme
import me.knighthat.kreate3.ui.component.ScreenPreviews
import java.io.File
import kotlin.system.exitProcess

/**
 * Shown after a crash, see [CrashHandler]. Runs in its own process (`:crash`), so it works
 * whatever state the app's died in, and touches neither Koin nor the settings: the theme follows
 * the system, the text is the crash log it is given.
 */
class CrashActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) =
        super.attachBaseContext(AppLanguage.wrap(newBase))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val file = intent.getStringExtra(EXTRA_FILE)?.let(::File)
        val log = file?.takeIf { it.exists() }?.readText().orEmpty()

        setContent {
            CrashTheme {
                val exportTo =
                    rememberLauncherForActivityResult(CreateDocument("text/plain")) { uri ->
                        uri?.let { export(it, log) }
                    }
                CrashContent(
                    log = log,
                    onCopy = { copy(log) },
                    onExport = { exportTo.launch(file?.name ?: "kreate-crash.log") },
                    onClose = { close() },
                    onRestart = { restart() },
                )
            }
        }
    }

    private fun copy(log: String) {
        val clipboard = getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText("Kreate crash log", log))
        // Android 13+ shows its own confirmation
        if (Build.VERSION.SDK_INT < 33)
            Toast.makeText(this, R.string.snackbar_log_copied, Toast.LENGTH_SHORT).show()
    }

    private fun export(uri: Uri, log: String) {
        val saved = try {
            contentResolver.openOutputStream(uri, "wt")?.use { it.write(log.toByteArray()) } != null
        } catch (_: Exception) {
            false
        }
        Toast.makeText(
            this,
            if (saved) R.string.snackbar_log_saved else R.string.snackbar_log_save_failed,
            Toast.LENGTH_SHORT
        ).show()
    }

    /** Ends this screen and its process, there is nothing else in it. */
    private fun close() {
        finishAndRemoveTask()
        Process.killProcess(Process.myPid())
        exitProcess(0)
    }

    private fun restart() {
        val launch = packageManager.getLaunchIntentForPackage(packageName)?.component
            ?: android.content.ComponentName(this, KreateActivity::class.java)
        startActivity(Intent.makeRestartActivityTask(launch))
        close()
    }

    companion object {

        /** The path of the crash log to show. */
        const val EXTRA_FILE = "crash_file"
    }
}

@Composable
private fun CrashTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colors: ColorScheme = when {
        Build.VERSION.SDK_INT >= 31 ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colors, content = content)
}

/**
 * @param log the crash log, shown as it is
 */
@Composable
private fun CrashContent(
    log: String,
    onCopy: () -> Unit,
    onExport: () -> Unit,
    onClose: () -> Unit,
    onRestart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier.fillMaxSize()) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .safeDrawingPadding()
                .padding(16.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.warning),
                contentDescription = stringResource(R.string.semantic_app_crashed),
                // A plain red, the theme's error color is pale in dark mode
                tint = Color(0xFFE53935),
                modifier = Modifier
                    .size(96.dp)
                    .align(Alignment.CenterHorizontally),
            )
            Text(
                stringResource(R.string.screen_crash_hint),
                style = MaterialTheme.typography.bodyMedium,
            )

            SelectionContainer(Modifier.weight(1f)) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Text(
                        log.ifEmpty { stringResource(R.string.screen_crash_status_no_log) },
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        softWrap = false,
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .horizontalScroll(rememberScrollState())
                            .padding(12.dp),
                    )
                }
            }

            Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onCopy, Modifier.weight(1f)) {
                    Text(stringResource(R.string.action_copy), maxLines = 1)
                }
                OutlinedButton(onExport, Modifier.weight(1f)) {
                    Text(stringResource(R.string.action_export), maxLines = 1)
                }
            }
            Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClose, Modifier.weight(1f)) {
                    Text(stringResource(R.string.action_close), maxLines = 1)
                }
                Button(onRestart, Modifier.weight(1f)) {
                    Text(stringResource(R.string.action_restart), maxLines = 1)
                }
            }
        }
    }
}

//<editor-fold defaultstate="collapsed" desc="Previews">
@ScreenPreviews
@Composable
private fun CrashPreview() = PreviewTheme {
    CrashContent(
        log = """
            Time: 2026-11-04 12:00:00.000 +0000
            App: 3.0.0-beta.1 (1), debug
            Android: 16 (API 36)
            Device: Google Pixel 6 Pro
            Thread: main

            java.lang.IllegalStateException: Sample failure
                at me.knighthat.kreate3.Sample.run(Sample.kt:12)
        """.trimIndent(),
        onCopy = {},
        onExport = {},
        onClose = {},
        onRestart = {},
    )
}
//</editor-fold>
