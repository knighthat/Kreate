package it.fast4x.rimusic.ui.screens.settings

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import app.kreate.android.R
import it.fast4x.rimusic.appContext
import it.fast4x.rimusic.colorPalette
import it.fast4x.rimusic.enums.NavigationBarPosition
import it.fast4x.rimusic.enums.UiType
import it.fast4x.rimusic.extensions.contributors.ShowDevelopers
import it.fast4x.rimusic.extensions.contributors.ShowTranslators
import it.fast4x.rimusic.extensions.contributors.countDevelopers
import it.fast4x.rimusic.extensions.contributors.countTranslators
import it.fast4x.rimusic.typography
import it.fast4x.rimusic.ui.components.navigation.header.TabToolBar
import it.fast4x.rimusic.ui.components.themed.Title
import it.fast4x.rimusic.ui.styling.Dimensions
import it.fast4x.rimusic.utils.bold
import it.fast4x.rimusic.utils.getVersionName
import it.fast4x.rimusic.utils.secondary
import me.knighthat.utils.Repository

@ExperimentalAnimationApi
@Composable
fun About() {
    val uriHandler = LocalUriHandler.current

    Column(
        modifier = Modifier
            .background(colorPalette().background0)
            .fillMaxHeight()
            .fillMaxWidth(
                if (NavigationBarPosition.Right.isCurrent())
                    Dimensions.contentWidthRightBar
                else
                    1f
            )
            .verticalScroll(rememberScrollState())
    ) {

        if( UiType.ViMusic.isCurrent() )
            if( NavigationBarPosition.Right.isCurrent() || NavigationBarPosition.Left.isCurrent() )
                Spacer( Modifier.height( Dimensions.halfheaderHeight ) )

        Row(
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            // Info icon with white circle around it
            Box(
                modifier = Modifier.padding( end = 5.dp )                           // A lil space after icon
                    .size( 24.dp )
                    .border(
                        BorderStroke( 1.dp, colorPalette().text ),
                        shape = CircleShape
                    )
                    .padding( 1.dp )                                 // Space within border
                    .fillMaxSize()
                    .align( Alignment.CenterVertically )
            ) {
                Icon(
                    painter = painterResource( R.drawable.information ),
                    tint = colorPalette().text,
                    contentDescription = null,
                    modifier = Modifier.align( Alignment.Center )
                )
            }

            val pkgManager = appContext().packageManager
            val appInfo = pkgManager.getApplicationInfo( appContext().packageName, 0 )
            BasicText(
                text = pkgManager.getApplicationLabel( appInfo ).toString(),
                style = TextStyle(
                    fontSize = typography().xxl.bold.fontSize,
                    fontWeight = typography().xxl.bold.fontWeight,
                    color = colorPalette().text,
                    textAlign = if( UiType.ViMusic.isNotCurrent() ) TextAlign.Center else TextAlign.End
                ),
                modifier = Modifier.align( Alignment.CenterVertically )
            )
        }

        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxSize()
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

        // Social platforms
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

        SettingsGroupSpacer()

        SettingsEntryGroupText(title = stringResource(R.string.troubleshooting))

        SettingsEntry(
            title = stringResource( R.string.view_the_source_code ),
            text = stringResource( R.string.you_will_be_redirected_to_github ),
            onClick = {
                uriHandler.openUri( Repository.REPO_URL )
            }
        )

        SettingsEntry(
            title = stringResource( R.string.word_documentation ),
            text = stringResource( R.string.opens_link_in_web_browser ),
            onClick = {
                uriHandler.openUri( "https://kreate.knighthat.me" )
            }
        )

        SettingsEntry(
            title = stringResource(R.string.report_an_issue),
            text = stringResource(R.string.you_will_be_redirected_to_github),
            onClick = {
                val issuePath = "/issues/new?assignees=&labels=bug&template=bug_report.yaml"
                uriHandler.openUri( Repository.REPO_URL.plus(issuePath) )
            }
        )

        SettingsEntry(
            title = stringResource(R.string.request_a_feature_or_suggest_an_idea),
            text = stringResource(R.string.you_will_be_redirected_to_github),
            onClick = {
                val issuePath = "/issues/new?assignees=&labels=feature_request&template=feature_request.yaml"
                uriHandler.openUri( Repository.REPO_URL.plus(issuePath) )
            }
        )

        SettingsGroupSpacer()

        Title(
            title = stringResource(R.string.contributors)
        )

        SettingsEntryGroupText(title = "${ countTranslators() } " + stringResource(R.string.translators))
        SettingsDescription(text = stringResource(R.string.in_alphabetical_order))
        ShowTranslators()

        SettingsGroupSpacer()

        SettingsEntryGroupText(title = "${ countDevelopers() } ${stringResource( R.string.about_developers_designers )}")
        SettingsDescription(text = stringResource(R.string.in_alphabetical_order))
        ShowDevelopers()

        SettingsGroupSpacer(
            modifier = Modifier.height(Dimensions.bottomSpacer)
        )
    }
}
