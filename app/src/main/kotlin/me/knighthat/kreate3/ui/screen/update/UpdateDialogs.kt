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

package me.knighthat.kreate3.ui.screen.update

import android.content.ActivityNotFoundException
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.touchlab.kermit.Logger
import me.knighthat.kreate3.BuildConfig
import me.knighthat.kreate3.R
import me.knighthat.kreate3.notification.Notifier
import me.knighthat.kreate3.ui.component.ComponentPreviews
import me.knighthat.kreate3.ui.component.PreviewTheme
import me.knighthat.kreate3.ui.screen.settings.about.Changelog
import me.knighthat.kreate3.update.AppRelease
import me.knighthat.kreate3.update.AppUpdater
import me.knighthat.kreate3.update.UpdateFailure
import me.knighthat.kreate3.update.UpdateState

private const val TAG = "UpdateDialogs"

/**
 * What [updater] has to show, over every screen: a new release with its notes (Download & install,
 * Ignore this version, Close), the download and install behind a dialog that can't be closed until
 * it fails, and the answer of a check from the settings as a message. The system's install
 * confirmation is opened from here, by the screen on top.
 */
@Composable
fun UpdateDialogs(updater: AppUpdater, notifier: Notifier) {
    val state by updater.state.collectAsStateWithLifecycle()
    val resources = LocalResources.current
    val context = LocalContext.current

    when (val current = state) {
        UpdateState.UpToDate, UpdateState.CheckFailed -> LaunchedEffect(current) {
            notifier.post(
                resources.getString(
                    if (current == UpdateState.UpToDate)
                        R.string.snackbar_up_to_date
                    else
                        R.string.snackbar_update_check_failed
                )
            )
            updater.dismiss()
        }

        is UpdateState.Available -> UpdateAvailableDialog(
            release = current.release,
            installed = BuildConfig.VERSION_NAME.substringBefore('+'),
            onInstall = { updater.install(current.release) },
            onIgnore = { updater.ignore(current.release) },
            onClose = updater::dismiss,
        )

        is UpdateState.Downloading -> UpdateProgressDialog(
            version = current.release.version,
            status = R.string.status_downloading,
            progress = current.progress,
        )

        is UpdateState.Installing -> {
            UpdateProgressDialog(
                version = current.release.version,
                status = R.string.status_installing,
                progress = null,
            )
            val confirmation = current.confirmation
            if (confirmation != null)
                LaunchedEffect(confirmation) {
                    try {
                        context.startActivity(confirmation)
                    } catch (e: ActivityNotFoundException) {
                        Logger.w(e, TAG) { "No screen for the install confirmation" }
                    }
                    updater.confirmationShown()
                }
        }

        is UpdateState.Failed -> UpdateFailedDialog(
            version = current.release.version,
            reason = current.reason,
            onClose = updater::dismiss,
        )

        UpdateState.Idle, is UpdateState.Checking -> Unit
    }
}

/** A newer [release] and its notes; [installed] is the app's version. */
@Composable
private fun UpdateAvailableDialog(
    release: AppRelease,
    installed: String,
    onInstall: () -> Unit,
    onIgnore: () -> Unit,
    onClose: () -> Unit,
) = AlertDialog(
    onDismissRequest = onClose,
    title = { Text(stringResource(R.string.dialog_title_update_available)) },
    text = {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Text(stringResource(R.string.dialog_body_update_available, release.version, installed))
            NotesList(R.string.section_header_changelog_added, release.notes.adds)
            NotesList(R.string.section_header_changelog_removed, release.notes.removes)
            NotesList(R.string.section_header_changelog_changed, release.notes.changes)
            NotesList(R.string.section_header_changelog_fixed, release.notes.fixes)
        }
    },
    confirmButton = {
        // Stacked at the end, the install on top
        Column(horizontalAlignment = Alignment.End) {
            TextButton(onClick = onInstall) { Text(stringResource(R.string.action_download_and_install)) }
            TextButton(onClick = onIgnore) { Text(stringResource(R.string.action_ignore_this_version)) }
            TextButton(onClick = onClose) { Text(stringResource(R.string.action_close)) }
        }
    },
)

/** A heading of the release's notes over its [items] as a bulleted list, nothing without items. */
@Composable
private fun NotesList(@StringRes title: Int, items: List<String>) {
    if (items.isEmpty()) return

    Text(
        text = stringResource(title),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
    )
    // The bullet sits in its own column so wrapped lines start under the text, and a screen reader
    // reads bullet and text as one item
    items.forEach {
        Row(
            Modifier
                .padding(vertical = 2.dp)
                .semantics(mergeDescendants = true) {}
        ) {
            Text("•", Modifier.width(16.dp))
            Text(it)
        }
    }
}

/**
 * The download or install of [version], which can't be closed: [status] over a bar filled to
 * [progress] (0 to 1), moving on its own while `null`.
 */
@Composable
private fun UpdateProgressDialog(version: String, @StringRes status: Int, progress: Float?) =
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        title = { Text(stringResource(R.string.dialog_title_updating, version)) },
        text = {
            Column {
                Text(stringResource(status))
                val bar = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
                if (progress != null)
                    LinearProgressIndicator(progress = { progress }, modifier = bar)
                else
                    LinearProgressIndicator(bar)
            }
        },
        confirmButton = {},
    )

/** Why the update to [version] stopped. */
@Composable
private fun UpdateFailedDialog(version: String, reason: UpdateFailure, onClose: () -> Unit) =
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.dialog_title_updating, version)) },
        text = {
            Text(
                stringResource(
                    when (reason) {
                        UpdateFailure.DOWNLOAD -> R.string.error_update_download
                        UpdateFailure.DAMAGED -> R.string.error_update_damaged
                        UpdateFailure.CANCELED -> R.string.error_update_canceled
                        UpdateFailure.INSTALL -> R.string.error_update_install
                    }
                )
            )
        },
        confirmButton = {
            TextButton(onClick = onClose) { Text(stringResource(R.string.action_close)) }
        },
    )

//<editor-fold defaultstate="collapsed" desc="Previews">
private val PREVIEW_RELEASE = AppRelease(
    version = "3.1.0",
    notes = Changelog(
        version = "3.1.0",
        adds = listOf("Updates from GitHub, checked once a day"),
        fixes = listOf("Lyrics shown for the song before"),
    ),
    apkUrl = "",
    size = 0,
    sha256 = null,
)

@ComponentPreviews
@Composable
private fun UpdateAvailablePreview() = PreviewTheme {
    Surface { UpdateAvailableDialog(PREVIEW_RELEASE, "3.0.2", {}, {}, {}) }
}

@ComponentPreviews
@Composable
private fun UpdateProgressPreview() = PreviewTheme {
    Surface { UpdateProgressDialog("3.1.0", R.string.status_downloading, .4f) }
}

@ComponentPreviews
@Composable
private fun UpdateFailedPreview() = PreviewTheme {
    Surface { UpdateFailedDialog("3.1.0", UpdateFailure.CANCELED) {} }
}
//</editor-fold>
