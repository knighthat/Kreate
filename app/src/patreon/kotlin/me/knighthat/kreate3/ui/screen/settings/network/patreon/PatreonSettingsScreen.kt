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

package me.knighthat.kreate3.ui.screen.settings.network.patreon

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import me.knighthat.kreate3.BuildConfig
import me.knighthat.kreate3.R
import me.knighthat.kreate3.api.PatreonFeature
import me.knighthat.kreate3.api.PatreonProfile
import me.knighthat.kreate3.api.PatronStatus
import me.knighthat.kreate3.patreon.PatreonAccount
import me.knighthat.kreate3.patreon.PatreonAccountState
import me.knighthat.kreate3.patreon.PatreonSession
import me.knighthat.kreate3.preferences.GlobalPreferences
import me.knighthat.kreate3.preferences.collectAsStateWithLifecycle
import me.knighthat.kreate3.ui.component.PreviewTheme
import me.knighthat.kreate3.ui.component.ScreenPreviews
import me.knighthat.kreate3.ui.component.rememberOpenLink
import me.knighthat.kreate3.ui.screen.settings.SettingsItem
import me.knighthat.kreate3.ui.screen.settings.SettingsPage
import me.knighthat.kreate3.ui.screen.settings.SettingsSection
import me.knighthat.kreate3.ui.screen.settings.SwitchSetting
import org.koin.compose.koinInject
import java.text.DateFormat
import java.util.Date
import java.util.GregorianCalendar
import androidx.lifecycle.compose.collectAsStateWithLifecycle as collectFlowAsState

/** What the user is called on Patreon, `null` when the API didn't say. */
private val PatreonProfile?.displayName: String?
    get() = this?.name ?: this?.username

/** The day of [PatreonProfile.nextChargeDate], `null` when there is none or it can't be read. */
private val PatreonProfile?.renewalDate: Date?
    get() {
        // The day as Patreon wrote it, not moved to the device's time zone
        val (year, month, day) = this?.nextChargeDate
            ?.let { Regex("""^(\d{4})-(\d{2})-(\d{2})""").find(it) }
            ?.destructured
            ?: return null
        return GregorianCalendar(year.toInt(), month.toInt() - 1, day.toInt()).time
    }

/**
 * The Patreon page: signing in and out, the membership, and the cloud backup switch.
 */
@Composable
fun PatreonSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    account: PatreonAccount = koinInject(),
    session: PatreonSession = koinInject(),
) {
    val state by account.state.collectFlowAsState()
    val member by session.member.collectFlowAsState()
    val checking by account.checking.collectFlowAsState()
    val cloudBackup by GlobalPreferences.CLOUD_BACKUP.collectAsStateWithLifecycle()
    val openLink = rememberOpenLink()

    // Back from the login page without an answer, the user left it
    LifecycleResumeEffect(account) {
        account.abandon()
        onPauseOrDispose {}
    }

    PatreonSettingsContent(
        state = state,
        cloudBackup = cloudBackup,
        // From the token, the profile's copy is only shown
        backupUnlocked = member?.claims?.has(PatreonFeature.BACKUPS) == true,
        checking = checking,
        onSignIn = { account.start()?.let(openLink) },
        onCheckMembership = account::checkMembership,
        // The patreon build's homepage is its Patreon page, with the tiers
        onUpgrade = { openLink(BuildConfig.HOMEPAGE_URL) },
        onSignOut = account::signOut,
        onBack = onBack,
        modifier = modifier,
    )
}

/**
 * @param cloudBackup see [GlobalPreferences.CLOUD_BACKUP]
 * @param backupUnlocked whether the signed in tier has [PatreonFeature.BACKUPS]
 * @param checking whether the membership is being asked again, see [PatreonAccount.checkMembership]
 * @param onUpgrade opens the Patreon page with the tiers, from the switch while it's locked
 */
@Composable
private fun PatreonSettingsContent(
    state: PatreonAccountState,
    cloudBackup: Boolean,
    backupUnlocked: Boolean,
    checking: Boolean,
    onSignIn: () -> Unit,
    onCheckMembership: () -> Unit,
    onUpgrade: () -> Unit,
    onSignOut: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val patreon = stringResource(R.string.patreon)
    var askSignOut by remember { mutableStateOf(false) }
    val signedIn = state is PatreonAccountState.SignedIn

    SettingsPage(patreon, onBack, modifier) {
        SettingsSection(stringResource(R.string.setting_title_account))
        when (state) {
            PatreonAccountState.SignedOut, PatreonAccountState.SigningIn -> SettingsItem(
                title = stringResource(R.string.setting_title_sign_in),
                summary = if (state == PatreonAccountState.SigningIn)
                    stringResource(R.string.setting_description_sign_in_status_waiting)
                else
                    stringResource(R.string.setting_description_sign_in_patreon, patreon),
                // Again while waiting, in case the page was closed
                onClick = onSignIn,
                trailing = {
                    Icon(painterResource(R.drawable.chevron_right), contentDescription = null)
                },
            )

            is PatreonAccountState.SignedIn -> {
                SettingsItem(
                    title = state.profile.displayName ?: stringResource(R.string.status_signed_in),
                    summary = stringResource(R.string.setting_description_tap_to_sign_out),
                    onClick = { askSignOut = true },
                )
                SettingsItem(
                    title = stringResource(R.string.setting_title_tier),
                    summary = state.profile?.tierTitle
                        ?: stringResource(R.string.setting_description_patreon_tier_none),
                )
                val renewal = remember(state.profile) { state.profile.renewalDate }
                val dateFormat = remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }
                SettingsItem(
                    title = stringResource(R.string.setting_title_renewal_date),
                    summary = renewal?.let(dateFormat::format)
                        ?: stringResource(R.string.setting_description_patreon_renewal_none),
                )
                // A pledge changed on Patreon shows only after a refresh
                if (!backupUnlocked)
                    SettingsItem(
                        title = stringResource(R.string.setting_title_check_membership),
                        summary = if (checking)
                            stringResource(R.string.status_checking)
                        else
                            stringResource(R.string.setting_description_check_membership, patreon),
                        onClick = if (checking) null else onCheckMembership,
                    )
            }
        }

        SettingsSection(stringResource(R.string.screen_title_backup))
        // Kept while signed out or without the feature, it applies again once both are back
        val canBackUp = signedIn && backupUnlocked
        SwitchSetting(
            title = stringResource(R.string.setting_title_cloud_backup),
            checked = cloudBackup && canBackUp,
            onCheckedChange = { GlobalPreferences.CLOUD_BACKUP.update(it) },
            summary = when {
                !signedIn ->
                    stringResource(R.string.setting_description_configure_patreon_status_signed_out)

                !backupUnlocked ->
                    stringResource(R.string.setting_description_cloud_backup_status_not_in_tier)

                else -> stringResource(R.string.setting_description_cloud_backup, patreon)
            },
            enabled = canBackUp,
            onDisabledClick = if (signedIn && !backupUnlocked) onUpgrade else null,
        )

        if (askSignOut)
            AlertDialog(
                onDismissRequest = { askSignOut = false },
                title = { Text(stringResource(R.string.dialog_title_sign_out, patreon)) },
                text = { Text(stringResource(R.string.dialog_body_sign_out_of_patreon)) },
                confirmButton = {
                    TextButton(onClick = {
                        askSignOut = false
                        onSignOut()
                    }) { Text(stringResource(R.string.action_sign_out)) }
                },
                dismissButton = {
                    TextButton(onClick = { askSignOut = false }) {
                        Text(stringResource(R.string.action_cancel))
                    }
                },
            )
    }
}

@ScreenPreviews
@Composable
private fun PatreonSettingsPreview() = PreviewTheme {
    PatreonSettingsContent(
        state = PatreonAccountState.SignedIn(
            PatreonProfile(
                id = "1",
                name = "Neon Coast",
                username = "neoncoast",
                avatar = null,
                tier = 3,
                tierTitle = "Bug Hunter",
                features = setOf(PatreonFeature.BACKUPS),
                status = PatronStatus.ACTIVE_PATRON,
                nextChargeDate = "2026-12-01T00:00:00.000+00:00",
            )
        ),
        cloudBackup = true,
        backupUnlocked = true,
        checking = false,
        onSignIn = {},
        onCheckMembership = {},
        onUpgrade = {},
        onSignOut = {},
        onBack = {},
    )
}
