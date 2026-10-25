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

package me.knighthat.kreate3.ui.screen.settings.backup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument
import androidx.activity.result.contract.ActivityResultContracts.OpenDocument
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.knighthat.kreate3.R
import me.knighthat.kreate3.ui.component.TileButton
import me.knighthat.kreate3.ui.screen.settings.SettingsPage
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
    var confirmRestore by remember { mutableStateOf(false) }

    val exportTo = rememberLauncherForActivityResult(CreateDocument("application/zip")) { uri ->
        uri?.let(viewModel::export)
    }
    val restoreFrom = rememberLauncherForActivityResult(OpenDocument()) { uri ->
        uri?.let(viewModel::restore)
    }

    SettingsPage("Backup", onBack, modifier) {
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
                title = if (working == BackupTask.RESTORE) stringResource(R.string.setting_description_restore_status_reading) else stringResource(
                    R.string.setting_title_restore
                ),
                subtitle = stringResource(R.string.setting_description_restore),
                onClick = {
                    if (working == null) confirmRestore = true
                },
                busy = working == BackupTask.RESTORE,
                contentPadding = padding,
                modifier = tile,
            )
            TileButton(
                icon = R.drawable.download,
                title = if (working == BackupTask.EXPORT) stringResource(R.string.setting_description_back_up_status_saving) else stringResource(
                    R.string.setting_title_back_up
                ),
                subtitle = stringResource(R.string.setting_description_back_up),
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
            title = { Text(stringResource(R.string.setting_title_restore_a_backup)) },
            text = {
                Text(stringResource(R.string.setting_description_restore_a_backup))
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
