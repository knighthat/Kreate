package app.kreate.android.themed.common.screens.settings.general

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.kreate.android.BuildConfig
import app.kreate.android.Preferences
import app.kreate.android.R
import app.kreate.android.themed.common.component.settings.SettingComponents
import app.kreate.android.themed.common.component.settings.SettingEntrySearch
import app.kreate.android.themed.common.component.settings.entry
import app.kreate.android.themed.common.component.settings.header
import it.fast4x.rimusic.enums.CheckUpdateState
import it.fast4x.rimusic.ui.components.themed.SecondaryTextButton
import me.knighthat.updater.ChangelogsDialog
import me.knighthat.updater.Updater


fun LazyListScope.updateSection( search: SettingEntrySearch ) {
    header( R.string.update )

    entry( search, R.string.update ) {
        SettingComponents.EnumEntry(
            preference = Preferences.CHECK_UPDATE,
            titleId = R.string.setting_entry_update_checker,
            subtitle = stringResource( Preferences.CHECK_UPDATE.value.subtitleId, BuildConfig.APP_NAME ),
            trailingContent = {
                AnimatedVisibility(
                    visible = Preferences.CHECK_UPDATE.either( CheckUpdateState.DISABLED ),
                    // Slide in from right + fade in effect.
                    enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(initialAlpha = 0f),
                    // Slide out from left + fade out effect.
                    exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(targetAlpha = 0f)
                ) {
                    val context = LocalContext.current
                    SecondaryTextButton(
                        text = stringResource( R.string.info_check_update_now ),
                        onClick = { Updater.checkForUpdate( context, true ) }
                    )
                }
            }
        )
    }
    entry( search, R.string.setting_entry_view_changelogs ) {
        val context = LocalContext.current
        val changelogs = remember { ChangelogsDialog(context) }
        changelogs.Render()

        SettingComponents.Text(
            title = stringResource( R.string.setting_entry_view_changelogs ),
            onClick = changelogs::showDialog,
            subtitle = "v${BuildConfig.VERSION_NAME}"
        )
    }
    item( "showNoUpdateAvailableToaster" ) {
        val updateAvailableTitle = stringResource(
            R.string.show_quotes,
            stringResource( R.string.info_no_update_available )
        )
        if( search appearsIn updateAvailableTitle )
            SettingComponents.BooleanEntry(
                preference = Preferences.SHOW_CHECK_UPDATE_STATUS,
                title = updateAvailableTitle,
                subtitle = stringResource(
                    if( Preferences.SHOW_CHECK_UPDATE_STATUS.value )
                        R.string.setting_description_show_no_update_available_yes
                    else
                        R.string.setting_description_show_no_update_available_no
                )
            )
    }
}
