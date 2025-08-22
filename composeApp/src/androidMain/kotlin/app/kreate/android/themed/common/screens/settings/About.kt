package app.kreate.android.themed.common.screens.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import app.kreate.android.R
import app.kreate.android.themed.common.component.settings.SettingComponents
import app.kreate.android.themed.common.component.settings.SettingEntrySearch
import app.kreate.android.themed.common.component.settings.about.Contributors
import app.kreate.android.themed.common.component.settings.entry
import app.kreate.android.themed.common.component.settings.header
import it.fast4x.rimusic.colorPalette
import it.fast4x.rimusic.enums.NavRoutes
import it.fast4x.rimusic.enums.NavigationBarPosition
import it.fast4x.rimusic.enums.UiType
import it.fast4x.rimusic.typography
import it.fast4x.rimusic.ui.components.navigation.header.TabToolBar
import it.fast4x.rimusic.ui.styling.Dimensions
import it.fast4x.rimusic.utils.getVersionName
import it.fast4x.rimusic.utils.secondary
import me.knighthat.utils.Repository

@Composable
fun About(
    navController: NavController,
    paddingValues: PaddingValues
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val scrollState = rememberLazyListState()

    val search = remember {
        SettingEntrySearch( scrollState, R.string.about, R.drawable.person )
    }
    val contributors = remember { Contributors(context) }

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

        Row(
            horizontalArrangement = if( UiType.ViMusic.isCurrent() ) Arrangement.End else Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding( horizontal = SettingComponents.HORIZONTAL_PADDING.dp )
                               .fillMaxWidth()
                               .wrapContentHeight()
        ) {
            BasicText(
                text = "v${getVersionName()} by ",
                style = typography().s.secondary,
            )
            Row(
                Modifier.clickable {
                    val url = "${Repository.GITHUB}/${Repository.OWNER}"
                    uriHandler.openUri( url )
                }
            ) {
                Image(
                    painter = painterResource( R.drawable.github_logo ),
                    contentDescription = null
                )
                BasicText(
                    text = Repository.OWNER,
                    style = typography().s.secondary.copy(
                        textDecoration = TextDecoration.Underline
                    ),
                    modifier = Modifier.align( Alignment.CenterVertically )
                )
            }
        }

        LazyColumn(
            state = scrollState,
            contentPadding = PaddingValues(bottom = Dimensions.bottomSpacer)
        ) {
            // Social platforms
            item {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth( .9f )
                                       .padding( top = 10.dp )
                ) {
                    // [Icon] overrides vector's color at render time.
                    // Using [Image] to retain original color(s)
                    Image(
                        painter = painterResource( R.drawable.discord_logo ),
                        contentDescription = "Discord server",
                        modifier = Modifier.size( TabToolBar.TOOLBAR_ICON_SIZE )
                                           .clickable( null, ripple(false) ) {
                                               uriHandler.openUri( "https://discord.gg/WYr9ZgJzpx" )
                                           }
                    )

                    Spacer( Modifier.width( 15.dp ) )

                    Image(
                        painter = painterResource( R.drawable.github_logo ),
                        contentDescription = "Github discussion board",
                        modifier = Modifier.size( TabToolBar.TOOLBAR_ICON_SIZE )
                                           .clickable( null, ripple(false) ) {
                                               uriHandler.openUri( "${Repository.REPO_URL}/discussions" )
                                           }
                    )
                }
            }

            header( R.string.troubleshooting )
            entry( search, R.string.view_the_source_code ) {
                SettingComponents.Text(
                    title = stringResource( R.string.view_the_source_code ),
                    subtitle = stringResource( R.string.you_will_be_redirected_to_github ),
                    onClick = {
                        uriHandler.openUri( Repository.REPO_URL )
                    }
                )
            }
            entry( search, R.string.word_documentation ) {
                SettingComponents.Text(
                    title = stringResource( R.string.word_documentation ),
                    subtitle = stringResource( R.string.opens_link_in_web_browser ),
                    onClick = {
                        uriHandler.openUri( "https://kreate.knighthat.me" )
                    }
                )
            }
            entry( search, R.string.report_an_issue ) {
                SettingComponents.Text(
                    title = stringResource( R.string.report_an_issue ),
                    subtitle = stringResource( R.string.you_will_be_redirected_to_github ),
                    onClick = {
                        uriHandler.openUri(
                            with(Repository ) {
                                "$REPO_URL$ISSUE_TEMPLATE_PATH"
                            }
                        )
                    }
                )
            }
            entry( search, R.string.request_a_feature_or_suggest_an_idea ) {
                SettingComponents.Text(
                    title = stringResource( R.string.request_a_feature_or_suggest_an_idea ),
                    subtitle = stringResource( R.string.you_will_be_redirected_to_github ),
                    onClick = {
                        uriHandler.openUri(
                            with(Repository ) {
                                "$REPO_URL$FEATURE_REQUEST_TEMPLATE_PATH"
                            }
                        )
                    }
                )
            }
            entry( search, R.string.word_licenses ) {
                SettingComponents.Text(
                    title = stringResource( R.string.word_licenses ),
                    onClick = { NavRoutes.LICENSES.navigateHere( navController ) },
                )
            }

            header( { "${contributors.translators.size} ${context.getString(R.string.translators)}" } )
            entry( search, R.string.translators ) {
                Contributors.Show( contributors.translators )
            }

            header( { "${contributors.developers.size} ${context.getString( R.string.about_developers_designers )}" } )
            entry( search, R.string.contributors ) {
                Contributors.Show( contributors.developers )
            }
        }
    }
}