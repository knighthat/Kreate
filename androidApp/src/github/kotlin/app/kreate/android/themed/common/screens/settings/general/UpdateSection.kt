package app.kreate.android.themed.common.screens.settings.general

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kreate.android.themed.common.component.settings.SettingEntrySearch
import app.kreate.android.themed.common.component.settings.entry
import app.kreate.android.themed.common.component.settings.header
import app.kreate.components.settings.EnumEntry
import app.kreate.components.settings.SettingComponents
import app.kreate.compose.R
import app.kreate.preferences.Preferences
import app.kreate.util.VERSION_NAME
import it.fast4x.rimusic.enums.CheckUpdateState
import it.fast4x.rimusic.ui.components.themed.SecondaryTextButton
import me.knighthat.updater.ChangelogsDialog
import me.knighthat.updater.Updater
import org.jetbrains.compose.resources.stringResource


internal fun updateSection( scope: LazyListScope, search: SettingEntrySearch ) = with( scope ) {
    header( R.string.update )

    entry( search, R.string.update ) {
        val checkUpdate by Preferences.CHECK_UPDATE.collectAsStateWithLifecycle()

        SettingComponents.EnumEntry(
            preference = Preferences.CHECK_UPDATE,
            title = stringResource( R.string.setting_entry_update_checker ),
            subtitle = stringResource( checkUpdate.subtitleId, stringResource(app.kreate.resources.R.string.app_name) ),
            trailingContent = {
                AnimatedVisibility(
                    visible = checkUpdate === CheckUpdateState.DISABLED,
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

        SettingComponents.Entry(
            title = stringResource( R.string.setting_entry_view_changelogs ),
            onClick = changelogs::showDialog,
            subtitle = "v$VERSION_NAME"
        )
    }
    item( "showNoUpdateAvailableToaster" ) {
        val updateAvailableTitle = stringResource(
            R.string.show_quotes,
            stringResource( R.string.info_no_update_available )
        )
        if( search appearsIn updateAvailableTitle ) {
            val isActive by Preferences.SHOW_CHECK_UPDATE_STATUS.collectAsStateWithLifecycle()

            SettingComponents.BooleanEntry(
                preference = Preferences.SHOW_CHECK_UPDATE_STATUS,
                title = updateAvailableTitle,
                subtitle = stringResource(
                    if( isActive )
                        R.string.setting_description_show_no_update_available_yes
                    else
                        R.string.setting_description_show_no_update_available_no
                )
            )
        }
    }
}
