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

package me.knighthat.kreate3.ui.screen.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument
import androidx.activity.result.contract.ActivityResultContracts.OpenDocument
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.knighthat.kreate3.R
import me.knighthat.kreate3.ui.component.TileButton
import org.koin.androidx.compose.koinViewModel

/**
 * Saves the profiles, their settings and the library to a file, and restores them from one.
 */
@Composable
fun BackupSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BackupViewModel = koinViewModel(),
) {
    val working by viewModel.working.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var confirmRestore by remember { mutableStateOf(false) }

    val exportTo = rememberLauncherForActivityResult(CreateDocument("application/zip")) { uri ->
        uri?.let(viewModel::export)
    }
    val restoreFrom = rememberLauncherForActivityResult(OpenDocument()) { uri ->
        uri?.let(viewModel::restore)
    }

    LaunchedEffect(viewModel) { viewModel.messages.collect { snackbar.showSnackbar(it) } }

    SettingsPage("Backup", onBack, modifier, snackbar) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            val tile = Modifier.fillMaxWidth()
            val padding = PaddingValues(horizontal = 16.dp, vertical = 32.dp)

            TileButton(
                icon = R.drawable.upload,
                title = if (working == BackupTask.RESTORE) "Reading…" else "Restore",
                subtitle = "Replace everything with a backup. The app restarts after a restore.",
                onClick = {
                    if (working == null) confirmRestore = true
                },
                busy = working == BackupTask.RESTORE,
                contentPadding = padding,
                modifier = tile,
            )
            TileButton(
                icon = R.drawable.download,
                title = if (working == BackupTask.EXPORT) "Saving…" else "Back up",
                subtitle = "Save your profiles, settings and library to a file. Passwords and sign ins stay on this device, they are never in a backup.",
                onClick = {
                    if (working == null) exportTo.launch(viewModel.fileName())
                },
                busy = working == BackupTask.EXPORT,
                contentPadding = padding,
                modifier = tile,
            )
        }
    }

    if (confirmRestore)
        AlertDialog(
            onDismissRequest = { confirmRestore = false },
            title = { Text("Restore a backup?") },
            text = {
                Text(
                    "Every profile, setting and the library are replaced with the backup's, then " +
                            "the app restarts. Passwords and sign ins of profiles in the backup " +
                            "that are on this device are kept."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmRestore = false
                        restoreFrom.launch(arrayOf("application/zip", "application/octet-stream"))
                    },
                ) { Text("Pick backup") }
            },
            dismissButton = { TextButton(onClick = { confirmRestore = false }) { Text("Cancel") } },
        )
}
