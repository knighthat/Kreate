package app.kreate.android.themed.common.screens.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.core.os.LocaleListCompat
import androidx.media3.common.util.UnstableApi
import app.kreate.android.Preferences
import app.kreate.android.R
import app.kreate.android.themed.common.component.settings.SettingComponents
import app.kreate.android.themed.common.component.settings.SettingEntrySearch
import app.kreate.android.themed.common.component.settings.entry
import app.kreate.android.themed.common.component.settings.header
import app.kreate.android.themed.common.screens.settings.general.playerSettingsSection
import app.kreate.android.themed.common.screens.settings.general.updateSection
import app.kreate.android.utils.innertube.HOST_LANGUAGE
import app.kreate.constant.Language
import it.fast4x.rimusic.colorPalette
import it.fast4x.rimusic.enums.NavigationBarPosition
import it.fast4x.rimusic.ui.styling.Dimensions
import me.knighthat.utils.Toaster
import java.util.Locale

@ExperimentalMaterial3Api
@UnstableApi
@Composable
fun GeneralSettings( paddingValues: PaddingValues ) {
    val scrollState = rememberLazyListState()

    val search = remember {
        SettingEntrySearch( scrollState, R.string.tab_general, R.drawable.app_icon_monochrome )
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.background( colorPalette().background0 )
                           .padding( paddingValues )
                           .fillMaxHeight()
                           .fillMaxWidth(
                               if ( NavigationBarPosition.Right.isCurrent() )
                                   Dimensions.contentWidthRightBar
                               else
                                   1f
                           )
    ) {
        search.ToolBarButton()

        LazyColumn(
            state = scrollState,
            contentPadding = PaddingValues(bottom = Dimensions.bottomSpacer)
        ) {
            updateSection( search )

            header(
                titleId = R.string.languages,
                subtitle = {
                    stringResource( R.string.currently_selected, Preferences.APP_LANGUAGE.value.displayName )
                }
            )
            entry( search, R.string.app_language ) {
                SettingComponents.EnumEntry(
                    preference = Preferences.APP_LANGUAGE,
                    title = stringResource( R.string.app_language ),
                    subtitle = stringResource( R.string.setting_description_app_language ),
                    getName = { it.displayName },
                    onValueChanged = {
                        try {
                            val locales = if (it === Language.SYSTEM)
                                LocaleListCompat.getEmptyLocaleList()
                            else
                                LocaleListCompat.create( it.toLocale() )
                            // Apply it first before really selecting it
                            AppCompatDelegate.setApplicationLocales( locales )

                            Preferences.APP_LANGUAGE.value = it
                        } catch (err: Exception) {
                            err.printStackTrace()
                            err.message?.also( Toaster::e )
                        }
                    }
                )
            }
            entry( search, R.string.setting_entry_app_region ) {
                SettingComponents.ListEntry(
                    preference = Preferences.APP_REGION,
                    title = stringResource( R.string.setting_entry_app_region ),
                    getName = {
                        val locale = Locale(HOST_LANGUAGE, it)
                        locale.getDisplayCountry( locale )
                    },
                    getList = { Locale.getISOCountries() },
                    subtitle = stringResource( R.string.setting_description_app_region )
                )
            }

            playerSettingsSection( search )
        }
    }
}