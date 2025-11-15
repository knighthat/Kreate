package app.kreate.android.themed.common.screens.settings

import android.content.res.Resources
import androidx.annotation.RawRes
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.NonRestartableComposable
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
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import me.knighthat.component.settings.Contributor
import me.knighthat.component.settings.Developer
import me.knighthat.component.settings.Translator
import me.knighthat.utils.Repository
import me.knighthat.utils.Toaster
import timber.log.Timber


// Prevent this from being init until it's needed
private val json: Json by lazy {
    Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }
}

@OptIn(ExperimentalSerializationApi::class)
private inline fun <reified T: Contributor> getContributors(resources: Resources, @RawRes raw: Int ): List<T> =
    runCatching {
        resources.openRawResource( raw )
                 .use { inStream ->
                     json.decodeFromStream<List<T>>( inStream )
                 }
    }.onFailure { err ->
        Timber.tag( "About" ).e( err )
        Toaster.e(
            if( T::class == Developer::class )
                R.string.error_failed_to_load_developers
            else if( T::class == Translator::class )
                R.string.error_failed_to_load_translators
            else
            R.string.error_unknown
        )
    }.getOrDefault( emptyList() )

@NonRestartableComposable
@Composable
private fun RenderContributors( contributors: List<Contributor> ) =
    LazyColumn(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy( 15.dp ),
        contentPadding = PaddingValues(
            horizontal = SettingComponents.HORIZONTAL_PADDING.dp,
            vertical = 20.dp
        ),
        modifier = Modifier.fillMaxWidth()
                           .heightIn( 150.dp, 300.dp )
    ) {
        items(
            items = contributors,
            key = System::identityHashCode
        ) { contributor ->
            contributor.Draw( Contributor.Values.default )
        }
    }

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

            val translators = getContributors<Translator>( context.resources, R.raw.translators )
                .sortedBy( Translator::displayName )
            header( { "${translators.size} ${context.getString(R.string.translators)}" } )
            entry( search, R.string.translators ) {
                RenderContributors( translators )
            }

            val result = getContributors<Developer>( context.resources, R.raw.contributors )
                .sortedBy( Developer::contributions )
                .reversed()
                .toMutableList()
            // If owner presents and not at the top of the list
            // then remove it from current position and add it back to the top
            val ownerIndex = result.indexOfFirst( Contributor::isOwner )
            if( ownerIndex > 0 ) {
                val owner = result.removeAt( ownerIndex )
                result.add( 0, owner )
            }
            val developers = result.toList()
            header( { "${developers.size} ${context.getString( R.string.about_developers_designers )}" } )
            entry( search, R.string.contributors ) {
                RenderContributors( developers )
            }
        }
    }
}