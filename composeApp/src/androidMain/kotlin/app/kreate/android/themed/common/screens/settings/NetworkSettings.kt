package app.kreate.android.themed.common.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.kreate.android.Preferences
import app.kreate.android.R
import app.kreate.android.enums.DohServer
import app.kreate.android.themed.common.component.settings.SettingComponents
import app.kreate.android.themed.common.component.settings.SettingEntrySearch
import app.kreate.android.themed.common.component.settings.animatedEntry
import app.kreate.android.themed.common.component.settings.entry
import app.kreate.android.themed.common.component.settings.header
import it.fast4x.rimusic.colorPalette
import it.fast4x.rimusic.enums.NavigationBarPosition
import it.fast4x.rimusic.ui.styling.Dimensions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.knighthat.component.dialog.InputDialogConstraints
import me.knighthat.utils.Toaster
import okhttp3.Dns
import org.koin.java.KoinJavaComponent.inject
import java.net.Proxy

@Composable
fun NetworkSettings( paddingValues: PaddingValues ) {
    val scrollState = rememberLazyListState()

    val search = remember {
        SettingEntrySearch( scrollState, R.string.tab_network, R.drawable.app_icon_monochrome )
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
            header(
                titleId = R.string.proxy,
                subtitle = { stringResource( R.string.restarting_rimusic_is_required ) }
            )
            entry( search, R.string.enable_proxy ) {
                SettingComponents.BooleanEntry(
                    Preferences.IS_PROXY_ENABLED,
                    R.string.enable_proxy
                )
            }
            animatedEntry(
                key = "proxyChildren",
                visible = Preferences.IS_PROXY_ENABLED.value,
                modifier = Modifier.padding( start = 25.dp )
            ) {
                Column {
                    if( search appearsIn R.string.proxy_mode )
                        SettingComponents.EnumEntry(
                            Preferences.PROXY_SCHEME,
                            R.string.proxy_mode,
                            {
                                when( it ) {
                                    Proxy.Type.DIRECT -> stringResource( R.string.proxy_mode_direct )
                                    else              -> it.name
                                                           .lowercase()
                                                           .replaceFirstChar( Char::uppercase )
                                }
                            }
                        )

                    if( search appearsIn R.string.proxy_host )
                        SettingComponents.InputDialogEntry(
                            preference = Preferences.PROXY_HOST,
                            titleId = R.string.proxy_host,
                            constraint = InputDialogConstraints.ALL,
                            keyboardOption = KeyboardOptions(keyboardType = KeyboardType.Uri)
                        )

                    if( search appearsIn R.string.proxy_port )
                        SettingComponents.InputDialogEntry(
                            preference = Preferences.PROXY_PORT,
                            titleId = R.string.proxy_port,
                            constraint = InputDialogConstraints.POSITIVE_INTEGER,
                            keyboardOption = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )

                    if( search appearsIn R.string.setting_entry_test_proxy )
                        SettingComponents.Text(
                            title = stringResource( R.string.setting_entry_test_proxy ),
                            onClick = {
                                CoroutineScope( Dispatchers.IO ).launch {
                                    val proxy: Proxy by inject(Proxy::class.java)
                                    if( proxy !== Proxy.NO_PROXY )
                                        Toaster.s( R.string.success_proxy_verified )
                                    // Failed proxy message will be displayed automatically
                                }
                            }
                        )
                }
            }

            header(
                titleId = R.string.setting_header_dns_over_https,
                subtitle = { stringResource( R.string.restarting_rimusic_is_required ) }
            )
            entry( search, R.string.setting_entry_select_dns_over_https_server ) {
                SettingComponents.EnumEntry(
                    preference = Preferences.DOH_SERVER,
                    title = stringResource( R.string.setting_entry_select_dns_over_https_server ),
                    action = SettingComponents.Action.RESTART_APP
                )
            }
            entry(
                search = search,
                titleId = R.string.setting_entry_test_doh,
                additionalCheck =  Preferences.DOH_SERVER.value != DohServer.NONE
            ) {
                SettingComponents.Text(
                    title = stringResource( R.string.setting_entry_test_doh ),
                    onClick = {
                        CoroutineScope( Dispatchers.IO ).launch {
                            val dns: Dns by inject(Dns::class.java)
                            if( dns !== Dns.SYSTEM )
                                Toaster.s( R.string.success_doh_verified )
                            // Failed dns message will be displayed automatically
                        }
                    }
                )
            }
        }
    }
}