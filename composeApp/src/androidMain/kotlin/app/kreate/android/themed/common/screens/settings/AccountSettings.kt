package app.kreate.android.themed.common.screens.settings

import android.webkit.CookieManager
import android.webkit.WebStorage
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.rememberNavController
import app.kreate.android.Preferences
import app.kreate.android.R
import app.kreate.android.themed.common.component.settings.SettingComponents
import app.kreate.android.themed.common.component.settings.SettingEntrySearch
import app.kreate.android.themed.common.component.settings.section
import coil.compose.AsyncImage
import it.fast4x.innertube.utils.parseCookieString
import it.fast4x.rimusic.colorPalette
import it.fast4x.rimusic.enums.NavigationBarPosition
import it.fast4x.rimusic.extensions.discord.DiscordLoginAndGetToken
import it.fast4x.rimusic.extensions.youtubelogin.YouTubeLogin
import it.fast4x.rimusic.thumbnailShape
import it.fast4x.rimusic.ui.components.CustomModalBottomSheet
import it.fast4x.rimusic.ui.styling.Dimensions
import it.fast4x.rimusic.utils.RestartPlayerService
import it.fast4x.rimusic.utils.isAtLeastAndroid81
import me.knighthat.utils.Toaster

@ExperimentalMaterial3Api
@Composable
fun AccountSettings( paddingValues: PaddingValues ) {
    val context = LocalContext.current
    val scrollState = rememberLazyListState()

    val search = remember {
        SettingEntrySearch( scrollState, R.string.tab_accounts, R.drawable.person )
    }
    val (restartService, onRestartServiceChange) = rememberSaveable { mutableStateOf( false ) }

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
            section( "YOUTUBE MUSIC" ) {
                var visitorData by Preferences.YOUTUBE_VISITOR_DATA
                var dataSyncId by Preferences.YOUTUBE_SYNC_ID
                var cookie by Preferences.YOUTUBE_COOKIES
                var accountName by Preferences.YOUTUBE_ACCOUNT_NAME
                var accountEmail by Preferences.YOUTUBE_ACCOUNT_EMAIL
                var accountChannelHandle by Preferences.YOUTUBE_SELF_CHANNEL_HANDLE

                if( search appearsIn "YOUTUBE MUSIC" )
                    SettingComponents.BooleanEntry(
                        Preferences.YOUTUBE_LOGIN,
                        "Enable YouTube Music Login"
                    ) {
                        if( it ) return@BooleanEntry

                        Preferences.YOUTUBE_VISITOR_DATA.reset()
                        Preferences.YOUTUBE_SYNC_ID.reset()
                        Preferences.YOUTUBE_COOKIES.reset()
                        Preferences.YOUTUBE_ACCOUNT_NAME.reset()
                        Preferences.YOUTUBE_ACCOUNT_EMAIL.reset()
                        Preferences.YOUTUBE_SELF_CHANNEL_HANDLE.reset()
                    }

                AnimatedVisibility( Preferences.YOUTUBE_LOGIN.value ) {
                    var accountThumbnail by Preferences.YOUTUBE_ACCOUNT_AVATAR
                    var loginYouTube by remember { mutableStateOf(false) }
                    var isLoggedIn = remember(cookie) {
                        "SAPISID" in parseCookieString(cookie)
                    }

                    Column(
                        Modifier.padding( start = 25.dp )
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ){

                            if (isLoggedIn && accountThumbnail != "")
                                AsyncImage(
                                    model = accountThumbnail,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .height(50.dp)
                                        .clip(thumbnailShape())
                                )

                            Column {
                                val (title, subtitle) = remember( isLoggedIn, accountName, accountChannelHandle ) {
                                    if (isLoggedIn)
                                        "Disconnect" to "$accountName $accountChannelHandle"
                                    else
                                        "Connect" to ""
                                }

                                if( search appearsIn title )
                                    SettingComponents.Text(
                                        title = title,
                                        subtitle = subtitle,
                                        onClick = {
                                            if (isLoggedIn) {

                                                Preferences.YOUTUBE_VISITOR_DATA.reset()
                                                Preferences.YOUTUBE_SYNC_ID.reset()
                                                Preferences.YOUTUBE_COOKIES.reset()
                                                Preferences.YOUTUBE_ACCOUNT_NAME.reset()
                                                Preferences.YOUTUBE_ACCOUNT_EMAIL.reset()
                                                Preferences.YOUTUBE_SELF_CHANNEL_HANDLE.reset()
                                                Preferences.YOUTUBE_ACCOUNT_AVATAR.reset()
                                                loginYouTube = false
                                                //Delete cookies after logout
                                                val cookieManager = CookieManager.getInstance()
                                                cookieManager.removeAllCookies(null)
                                                cookieManager.flush()
                                                WebStorage.getInstance().deleteAllData()
                                                onRestartServiceChange( true )
                                            } else
                                                loginYouTube = true
                                        },
                                    ) {
                                        Icon(
                                            painter = painterResource( R.drawable.ytmusic ),
                                            contentDescription = title,
                                            tint = colorPalette().text,
                                            modifier = Modifier.size( 24.dp )
                                        )
                                    }

                                CustomModalBottomSheet(
                                    showSheet = loginYouTube,
                                    onDismissRequest = {
                                        loginYouTube = false
                                    },
                                    containerColor = colorPalette().background0,
                                    contentColor = colorPalette().background0,
                                    modifier = Modifier.fillMaxWidth(),
                                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                                    dragHandle = {
                                        Surface(
                                            modifier = Modifier.padding(vertical = 0.dp),
                                            color = colorPalette().background0,
                                            shape = thumbnailShape()
                                        ) {}
                                    },
                                    shape = Preferences.THUMBNAIL_BORDER_RADIUS.value.shape
                                ) {
                                    YouTubeLogin(
                                        onLogin = { cookieRetrieved ->
                                            if (cookieRetrieved.contains("SAPISID")) {
                                                isLoggedIn = true
                                                loginYouTube = false
                                                Toaster.i( "Login successful" )
                                                onRestartServiceChange( true )
                                            }

                                        }
                                    )
                                }

                                var restartActivity by Preferences.RESTART_ACTIVITY
                                RestartPlayerService(restartService, onRestart = {
                                    onRestartServiceChange( false )
                                    restartActivity = !restartActivity
                                })
                            }
                        }

                        if( search appearsIn "Sync data with YTM account" )
                            SettingComponents.BooleanEntry(
                                Preferences.YOUTUBE_PLAYLISTS_SYNC,
                                "Sync data with YTM account",
                                subtitle = "Playlists, albums, artists, history, like, etc."
                            )
                    }
                }
            }

            if( isAtLeastAndroid81 )
                section( R.string.social_discord ) {

                    if( search appearsIn R.string.discord_enable_rich_presence )
                        SettingComponents.BooleanEntry(
                            Preferences.DISCORD_LOGIN,
                            R.string.discord_enable_rich_presence
                        )

                    AnimatedVisibility( Preferences.DISCORD_LOGIN.value ) {
                        var loginDiscord by remember { mutableStateOf(false) }
                        var discordPersonalAccessToken by Preferences.DISCORD_ACCESS_TOKEN

                        val (titleId, subtitle) = remember {
                            if( Preferences.DISCORD_ACCESS_TOKEN.value.isBlank() )
                                R.string.discord_connect to ""
                            else
                                R.string.discord_disconnect to context .getString( R.string.discord_connected_to_discord_account )
                        }

                        Column {
                            if( search appearsIn titleId )
                                SettingComponents.Text(
                                    title = stringResource( titleId ),
                                    subtitle = subtitle,
                                    onClick = {
                                        if ( discordPersonalAccessToken.isNotEmpty() )
                                            discordPersonalAccessToken = ""
                                        else
                                            loginDiscord = true
                                    }
                                ) {
                                    Image(
                                        painter = painterResource( R.drawable.piped_logo ),
                                        contentDescription = null,
                                        modifier = Modifier.size( 24.dp )
                                    )
                                }

                            CustomModalBottomSheet(
                                showSheet = loginDiscord,
                                onDismissRequest = {
                                    loginDiscord = false
                                },
                                containerColor = colorPalette().background0,
                                contentColor = colorPalette().background0,
                                modifier = Modifier.fillMaxWidth(),
                                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                                dragHandle = {
                                    Surface(
                                        modifier = Modifier.padding(vertical = 0.dp),
                                        color = colorPalette().background0,
                                        shape = thumbnailShape()
                                    ) {}
                                },
                                shape = Preferences.THUMBNAIL_BORDER_RADIUS.value.shape
                            ) {
                                DiscordLoginAndGetToken(
                                    rememberNavController(),
                                    onGetToken = { token ->
                                        loginDiscord = false
                                        discordPersonalAccessToken = token
                                        Toaster.i( token )
                                    }
                                )
                            }
                        }
                    }
                }
        }
    }
}