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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import me.knighthat.kreate3.BuildConfig
import me.knighthat.kreate3.R
import me.knighthat.kreate3.api.RemoteFile
import me.knighthat.kreate3.preferences.Preferences
import me.knighthat.kreate3.preferences.collectAsStateWithLifecycle
import me.knighthat.kreate3.ui.component.PreviewTheme
import me.knighthat.kreate3.ui.component.ScreenPreviews
import me.knighthat.kreate3.ui.component.fileSize
import me.knighthat.kreate3.ui.component.rememberOpenLink
import me.knighthat.kreate3.ui.navigation.LocalNavigator
import me.knighthat.kreate3.ui.navigation.PatreonSettings
import me.knighthat.kreate3.ui.screen.settings.SettingsItem
import org.koin.androidx.compose.koinViewModel
import java.text.DateFormat
import java.util.Date
import kotlin.time.Instant
import androidx.lifecycle.compose.collectAsStateWithLifecycle as collectFlowAsState

/** The Remote tab of the Backup page: the backups kept online with the Patreon account. */
@Composable
fun RemoteBackupsPage(
    contentPadding: PaddingValues,
    viewModel: RemoteBackupsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectFlowAsState()
    val checking by viewModel.checking.collectFlowAsState()
    val binarySizes by Preferences.BINARY_SIZES.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    val openLink = rememberOpenLink()

    RemoteBackupsContent(
        state = state,
        checking = checking,
        binarySizes = binarySizes,
        contentPadding = contentPadding,
        onSignIn = { navigator?.open(PatreonSettings) },
        // The patreon build's homepage is its Patreon page, with the tiers
        onUpgrade = { openLink(BuildConfig.HOMEPAGE_URL) },
        onRetry = viewModel::retry,
        onCheckMembership = viewModel::checkMembership,
    )
}

/**
 * @param checking whether the membership is being asked again
 * @param binarySizes see [Preferences.BINARY_SIZES]
 */
@Composable
private fun RemoteBackupsContent(
    state: RemoteBackupsState,
    checking: Boolean,
    binarySizes: Boolean,
    contentPadding: PaddingValues,
    onSignIn: () -> Unit,
    onUpgrade: () -> Unit,
    onRetry: () -> Unit,
    onCheckMembership: () -> Unit,
) {
    when (state) {
        RemoteBackupsState.Loading -> Notice(contentPadding) { CircularProgressIndicator() }

        RemoteBackupsState.SignedOut -> Notice(contentPadding) {
            Message(stringResource(R.string.setting_description_configure_patreon_status_signed_out))
            TextButton(onClick = onSignIn) { Text(stringResource(R.string.action_sign_in)) }
        }

        RemoteBackupsState.Locked -> Notice(contentPadding) {
            Message(stringResource(R.string.status_remote_backups_not_in_tier))
            TextButton(onClick = onUpgrade) { Text(stringResource(R.string.action_upgrade)) }
            // The pledge may have just changed, or the token is older than its features
            TextButton(onClick = onCheckMembership, enabled = !checking) {
                Text(stringResource(R.string.action_check_again))
            }
        }

        RemoteBackupsState.Failed -> Notice(contentPadding) {
            Message(stringResource(R.string.status_couldnt_load_remote_backups))
            TextButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
        }

        is RemoteBackupsState.Loaded -> if (state.files.isEmpty()) {
            Notice(contentPadding) {
                Message(stringResource(R.string.status_no_remote_backups))
                Message(stringResource(R.string.status_remote_backups_kept))
            }
        } else {
            val resources = LocalResources.current
            val dateFormat = remember {
                DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
            }
            LazyColumn(contentPadding = contentPadding, modifier = Modifier.fillMaxSize()) {
                item {
                    Text(
                        stringResource(R.string.status_remote_backups_kept),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                items(state.files, key = RemoteFile::name) { file ->
                    val size = resources.fileSize(file.size, binarySizes)
                    // Only shows what is kept, a tap does nothing
                    SettingsItem(
                        title = file.name,
                        summary = file.lastModified
                            ?.let {
                                stringResource(
                                    R.string.status_remote_backup,
                                    size,
                                    dateFormat.format(Date(it.toEpochMilliseconds()))
                                )
                            }
                            ?: size,
                    )
                }
            }
        }
    }
}

@Composable
private fun Notice(contentPadding: PaddingValues, content: @Composable () -> Unit) = Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    modifier = Modifier
        .fillMaxSize()
        .padding(contentPadding)
        .padding(horizontal = 32.dp),
) { content() }

@Composable
private fun Message(text: String) = Text(
    text,
    style = MaterialTheme.typography.bodyLarge,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    textAlign = TextAlign.Center,
)

@ScreenPreviews
@Composable
private fun RemoteBackupsPreview() = PreviewTheme {
    RemoteBackupsContent(
        checking = false,
        state = RemoteBackupsState.Loaded(
            listOf(
                RemoteFile("kreate_20261115.zip", 4_812_000, Instant.parse("2026-11-15T09:30:00Z")),
                RemoteFile("kreate_20261015.zip", 4_506_000, Instant.parse("2026-10-15T09:30:00Z")),
                RemoteFile("kreate_20260915.zip", 4_120_000, null),
            )
        ),
        binarySizes = false,
        contentPadding = PaddingValues(),
        onSignIn = {},
        onUpgrade = {},
        onRetry = {},
        onCheckMembership = {},
    )
}
