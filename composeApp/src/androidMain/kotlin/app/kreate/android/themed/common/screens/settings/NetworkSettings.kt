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
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kreate.android.enums.DohServer
import app.kreate.android.themed.common.component.settings.SettingEntrySearch
import app.kreate.android.themed.common.component.settings.animatedEntry
import app.kreate.android.themed.common.component.settings.entry
import app.kreate.android.themed.common.component.settings.header
import app.kreate.components.settings.EnumEntry
import app.kreate.components.settings.InputDialogEntry
import app.kreate.components.settings.InputDialogProperties
import app.kreate.components.settings.SettingComponents
import app.kreate.compose.R
import app.kreate.preferences.Preferences
import app.kreate.utils.Toaster
import co.touchlab.kermit.Logger
import it.fast4x.rimusic.colorPalette
import it.fast4x.rimusic.enums.NavigationBarPosition
import it.fast4x.rimusic.ui.styling.Dimensions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.knighthat.component.dialog.InputDialogConstraints
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
                    preference = Preferences.IS_PROXY_ENABLED,
                    title = stringResource( R.string.enable_proxy )
                )
            }
            animatedEntry(
                key = "proxyChildren",
                visibleState = app.kreate.preferences.Preferences.IS_PROXY_ENABLED,
                modifier = Modifier.padding( start = 25.dp )
            ) {
                Column {
                    val resources = LocalResources.current

                    if( search appearsIn R.string.proxy_mode )
                        SettingComponents.EnumEntry(
                            preference = Preferences.PROXY_SCHEME,
                            title = stringResource( R.string.proxy_mode ),
                            onValueChanged = {
                                when( it ) {
                                    Proxy.Type.DIRECT -> resources.getString( R.string.proxy_mode_direct )
                                    else              -> it.name
                                                           .lowercase()
                                                           .replaceFirstChar( Char::uppercase )
                                }
                            }
                        )

                    if( search appearsIn R.string.proxy_host ) {
                        val host by Preferences.PROXY_HOST.collectAsStateWithLifecycle()

                        SettingComponents.InputDialogEntry(
                            title = stringResource( R.string.proxy_host ),
                            constraint = Regex(InputDialogConstraints.ALL),
                            state = rememberTextFieldState( host ),
                            onConfirmRequest = {
                                Preferences.PROXY_HOST.update( it.text.toString() )
                            },
                            properties = InputDialogProperties.default.copy(
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done)
                            )
                        )
                    }

                    if( search appearsIn R.string.proxy_port ) {
                        val port by Preferences.PROXY_PORT.collectAsStateWithLifecycle()
                        SettingComponents.InputDialogEntry(
                            title = stringResource( R.string.proxy_port ),
                            constraint = Regex(InputDialogConstraints.POSITIVE_INTEGER),
                            state = rememberTextFieldState( port.toString() ),
                            onConfirmRequest = {
                                try {
                                    val value = it.text.toString().toInt()
                                    Preferences.PROXY_PORT.update( value )
                                } catch( err: NumberFormatException ) {
                                    Logger.e( "", err, "ProxyPort" )
                                }
                            },
                            properties = InputDialogProperties.default.copy(
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done)
                            )
                        )
                    }

                    if( search appearsIn R.string.setting_entry_test_proxy )
                        SettingComponents.Entry(
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
            item {
                val dohServer by app.kreate.preferences.Preferences.DOH_SERVER.collectAsStateWithLifecycle()

                if( search appearsIn R.string.setting_entry_test_doh && dohServer !== DohServer.NONE )
                    SettingComponents.Entry(
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