package app.kreate.android.themed.common.screens.settings.general

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import app.kreate.android.BuildConfig
import app.kreate.android.Preferences
import app.kreate.android.R
import app.kreate.android.themed.common.component.settings.SettingComponents
import app.kreate.android.themed.common.component.settings.SettingEntrySearch
import app.kreate.android.themed.common.component.settings.entry
import app.kreate.android.themed.common.component.settings.header
import it.fast4x.rimusic.enums.CheckUpdateState
import it.fast4x.rimusic.ui.components.themed.SecondaryTextButton
import it.fast4x.rimusic.ui.styling.LocalAppearance
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
        val changelogs = remember {
            object: me.knighthat.component.dialog.Dialog {
                override val dialogTitle: String
                    @Composable
                    get() = "This version has stopped receiving new features"

                // Automatically enable dialog when this class is init
                override var isActive: Boolean by mutableStateOf( false )

                override fun hideDialog() {
                    super.hideDialog()
                    Preferences.SEEN_CHANGELOGS_VERSION.value = BuildConfig.VERSION_NAME
                }

                @Composable
                override fun DialogBody() {
                    val (colorPalette, typography) = LocalAppearance.current
                    val style = typography.m.copy(
                        color = colorPalette.text
                    )

                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp)
                    ) {
                        Text(
                            text = "Version 2.x.x is now in maintenance mode.",
                            style = style
                        )
                        Text(
                            text = "Playback will keep working until v3 is released to the public.",
                            style = style
                        )
                        Text(
                            text = buildAnnotatedString {
                                append("Want to try v3 early? Check out my ")
                                withLink(
                                    LinkAnnotation.Url(
                                        url = "https://www.patreon.com/knighthat",
                                        styles = TextLinkStyles(
                                            style = SpanStyle(
                                                color = Color(0xFF42A5F5),
                                                textDecoration = TextDecoration.Underline
                                            )
                                        )
                                    )
                                ) { append("Patreon") }
                                append(" to learn more.")
                            },
                            style = style
                        )
                    }
                }
            }
        }
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
