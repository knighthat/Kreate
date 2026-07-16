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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import app.kreate.android.LocalFlavorSpecificFunctions
import app.kreate.android.themed.common.component.settings.SettingEntrySearch
import app.kreate.android.themed.common.component.settings.entry
import app.kreate.android.themed.common.component.settings.header
import app.kreate.android.themed.common.screens.settings.general.playerSettingsSection
import app.kreate.components.settings.EnumEntry
import app.kreate.components.settings.ListEntry
import app.kreate.components.settings.SettingComponents
import app.kreate.compose.R
import app.kreate.constant.Language
import app.kreate.preferences.Preferences
import it.fast4x.rimusic.colorPalette
import it.fast4x.rimusic.enums.NavigationBarPosition
import it.fast4x.rimusic.ui.styling.Dimensions
import me.knighthat.utils.Toaster
import java.text.Collator
import java.util.Locale

@ExperimentalMaterial3Api
@UnstableApi
@Composable
fun GeneralSettings( paddingValues: PaddingValues ) {
    val scrollState = rememberLazyListState()
    val flavorSpecificFunctions = LocalFlavorSpecificFunctions.current

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
            flavorSpecificFunctions.updateSection( this, search )

            header(
                titleId = R.string.languages,
                subtitle = {
                    val language by app.kreate.preferences.Preferences.APP_LANGUAGE.collectAsStateWithLifecycle()
                    stringResource( R.string.currently_selected, language.displayName )
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

                            Preferences.APP_LANGUAGE.update( it )
                        } catch (err: Exception) {
                            err.printStackTrace()
                            err.message?.also( Toaster::e )
                        }
                    }
                )
            }
            entry( search, R.string.setting_entry_app_region ) {
                val selected by Preferences.APP_REGION.collectAsStateWithLifecycle()
                var values: Map<String, String> by remember { mutableStateOf(emptyMap()) }

                SettingComponents.ListEntry(
                    entries = values.values.toTypedArray(),
                    selected = selected,
                    title = stringResource( R.string.setting_entry_app_region ),
                    getName = { it },
                    subtitle = stringResource( R.string.setting_description_app_region ),
                    onConfirmRequest =  { displayName ->
                        values.entries
                            // Reverse lookup by displayName
                            .firstOrNull { (_, v) -> v == displayName }
                            ?.value
                            ?.also( Preferences.APP_REGION::update )
                    }
                )

                val appLang by Preferences.APP_LANGUAGE.collectAsStateWithLifecycle()
                LaunchedEffect( appLang ) {
                    val appLocale = appLang.toLocale()

                    Locale.getISOCountries()
                          .associateWith {
                              Locale.Builder()
                                    .setRegion( it )
                                    .build()
                                    .getDisplayCountry( appLocale )
                          }
                          .filterValues( CharSequence::isNotBlank )
                          .toList()
                          .sortedWith(
                              compareBy(
                                  comparator = Collator.getInstance(appLocale),
                                  selector = { it.second }
                              )
                          )
                          .toMap()
                          .also { values = it }
                }
            }

            playerSettingsSection( search )
        }
    }
}