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
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.knighthat.kreate3.R
import me.knighthat.kreate3.ui.component.LocalIconOnly
import me.knighthat.kreate3.ui.component.MessageHost
import me.knighthat.kreate3.ui.component.PreviewTheme
import me.knighthat.kreate3.ui.component.ScreenPreviews
import me.knighthat.kreate3.ui.component.TileButton
import me.knighthat.kreate3.ui.screen.detail.DetailScaffold
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.getKoin

/** The Backup page's tabs, only in a build with a [RemoteBackupPage]. */
private enum class BackupTab(@param:StringRes val label: Int, @param:DrawableRes val icon: Int) {
    LOCAL(R.string.tab_local, R.drawable.mobile),
    REMOTE(R.string.tab_remote, R.drawable.cloud),
}

/**
 * Saves the profiles, their settings and the library to a file, and restores them from one. The
 * patreon build has a second tab with the backups kept online ([RemoteBackupPage]).
 */
@Composable
fun BackupSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BackupViewModel = koinViewModel(),
    remote: RemoteBackupPage? = getKoin().getOrNull(),
) {
    val working by viewModel.working.collectAsStateWithLifecycle()
    val remoteActive = remote?.active?.collectAsStateWithLifecycle()?.value == true

    val exportTo = rememberLauncherForActivityResult(CreateDocument("application/zip")) { uri ->
        uri?.let(viewModel::export)
    }
    val restoreFrom = rememberLauncherForActivityResult(OpenDocument()) { uri ->
        uri?.let(viewModel::restore)
    }

    BackupSettingsContent(
        working = working,
        onBackUp = { exportTo.launch(viewModel.fileName()) },
        onRestore = { restoreFrom.launch(arrayOf("application/zip", "application/octet-stream")) },
        onBack = onBack,
        modifier = modifier,
        remote = remote?.content,
        remoteActive = remoteActive,
    )
}

/**
 * @param working what runs now, `null` while nothing does
 * @param onBackUp asks where to save a backup
 * @param onRestore asks which backup to restore, once the user agreed to replace everything
 * @param remote the Remote tab, see [RemoteBackupPage]; without it the page has no tabs
 * @param remoteActive whether cloud backup is on, the Remote tab is grayed out otherwise
 */
@Composable
private fun BackupSettingsContent(
    working: BackupTask?,
    onBackUp: () -> Unit,
    onRestore: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    remote: (@Composable (PaddingValues) -> Unit)? = null,
    remoteActive: Boolean = false,
) {
    var confirmRestore by remember { mutableStateOf(false) }
    var picked by rememberSaveable { mutableStateOf(BackupTab.LOCAL) }
    // Back on Local while cloud backup is off, like when it stops while Remote shows
    val shown = if (remote != null && remoteActive) picked else BackupTab.LOCAL
    val iconOnly = LocalIconOnly.current

    DetailScaffold(
        title = stringResource(R.string.screen_title_backup),
        onBack = onBack,
        modifier = modifier,
        bottomBar = {
            if (remote != null)
                NavigationBar {
                    BackupTab.entries.forEach {
                        val label = stringResource(it.label)
                        NavigationBarItem(
                            selected = it == shown,
                            onClick = { picked = it },
                            enabled = it != BackupTab.REMOTE || remoteActive,
                            icon = {
                                Icon(
                                    painter = painterResource(it.icon),
                                    // Without the text, the icon has to name the tab
                                    contentDescription = if (iconOnly) label else null,
                                )
                            },
                            label = if (iconOnly) null else ({ Text(label) }),
                        )
                    }
                }
        },
        // Above the bar, the app's scaffold would put messages over it
        snackbarHost = { MessageHost() },
    ) { padding ->
        if (remote != null && shown == BackupTab.REMOTE)
            remote(padding)
        else
            LocalBackups(working, padding, onBackUp, onAskRestore = { confirmRestore = true })
    }

    if (confirmRestore)
        AlertDialog(
            onDismissRequest = { confirmRestore = false },
            title = { Text(stringResource(R.string.dialog_title_restore_a_backup)) },
            text = {
                Text(stringResource(R.string.dialog_body_restore_a_backup))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmRestore = false
                        onRestore()
                    },
                ) { Text(stringResource(R.string.action_pick_backup)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    confirmRestore = false
                }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
}

/** The Local tab: back up to a file, restore from one. */
@Composable
private fun LocalBackups(
    working: BackupTask?,
    contentPadding: PaddingValues,
    onBackUp: () -> Unit,
    onAskRestore: () -> Unit,
) = Column(
    verticalArrangement = Arrangement.spacedBy(16.dp),
    modifier = Modifier
        .fillMaxSize()
        .padding(contentPadding)
        .verticalScroll(rememberScrollState())
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
            if (working == null) onAskRestore()
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
            if (working == null) onBackUp()
        },
        busy = working == BackupTask.EXPORT,
        contentPadding = padding,
        modifier = tile,
    )
}

@ScreenPreviews
@Composable
private fun BackupSettingsPreview() = PreviewTheme {
    BackupSettingsContent(
        working = null,
        onBackUp = {},
        onRestore = {},
        onBack = {},
        remote = {},
        remoteActive = true,
    )
}
