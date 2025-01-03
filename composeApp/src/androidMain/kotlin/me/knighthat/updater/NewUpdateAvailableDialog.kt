package me.knighthat.updater

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import it.fast4x.rimusic.R
import it.fast4x.rimusic.colorPalette
import it.fast4x.rimusic.typography
import it.fast4x.rimusic.ui.components.tab.toolbar.Dialog
import it.fast4x.rimusic.ui.components.themed.DefaultDialog
import it.fast4x.rimusic.ui.styling.shimmer
import it.fast4x.rimusic.utils.*
import me.knighthat.utils.Repository

object NewUpdateAvailableDialog: Dialog {

    /**
     * `false` by default.
     *
     * When this field is set to `true`, it'll
     * keep the dialog from showing up even when
     * [isActive] is `true`.
     *
     * This is used to prevent user from getting
     * annoyed by constant pop-up saying that
     * there's a new update available.
     *
     * This value will be reset once the app is
     * restart, either by user or by setting it
     * programmatically.
     */
    var isCancelled: Boolean by mutableStateOf( false )

    override val dialogTitle: String
        @Composable
        get() = TODO("Not yet implemented")

    override var isActive: Boolean by mutableStateOf( false )

    fun onDismiss() {
        isCancelled = true
        isActive = false
    }

    @Composable
    override fun Render() {
        if( isCancelled || !isActive ) return

        val uriHandler = LocalUriHandler.current

        @Composable
        fun DialogText(
            text: String,
            style: TextStyle,
            spacerHeight: Dp = 10.dp
        ) {
            BasicText(
                text = text,
                style = style,
            )
            Spacer( Modifier.height(spacerHeight) )
        }

        DefaultDialog( ::onDismiss ) {
            // Title
            DialogText(
                text = dialogTitle,
                style = typography().s.bold.copy( color = colorPalette().text )
            )

            // Update information
            DialogText(
                text = stringResource( R.string.app_update_dialog_new, Updater.build.readableSize ),
                style = typography().xs.semiBold.copy( color = colorPalette().text )
            )

            // Available actions
            DialogText(
                text = stringResource( R.string.actions_you_can_do ),
                style = typography().xs.semiBold.copy( color = colorPalette().textSecondary )
            )

            // Option 1: Go to github page to download
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding( bottom = 20.dp )
                                   .fillMaxWidth()
            ) {
                BasicText(
                    text = stringResource( R.string.open_the_github_releases_web_page_and_download_latest_version ),
                    style = typography().xxs.semiBold.copy( color = colorPalette().textSecondary ),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(0.8f)
                )
                Image(
                    painter = painterResource(R.drawable.globe),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(colorPalette().shimmer),
                    modifier = Modifier.size( 30.dp )
                                       .clickable {
                                           onDismiss()

                                           val tagUrl = Repository.GITHUB.plus( Repository.TAG_PATH )
                                           uriHandler.openUri( tagUrl )
                                       }
                )
            }
            Spacer( Modifier.height(10.dp) )

            // Option 2: Go straight to download page to start the download
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding( bottom = 20.dp )
                                   .fillMaxWidth()
            ) {
                BasicText(
                    text = stringResource(R.string.download_latest_version_from_github_you_will_find_the_file_in_the_notification_area_and_you_can_install_by_clicking_on_it),
                    style = typography().xxs.semiBold.copy(color = colorPalette().textSecondary),
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(0.8f)
                )
                Image(
                    painter = painterResource(R.drawable.downloaded),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(colorPalette().shimmer),
                    modifier = Modifier.size( 30.dp )
                                       .clickable {
                                           onDismiss()

                                           uriHandler.openUri( Updater.build.downloadUrl )
                                       }
                )
            }
            Spacer( Modifier.height(10.dp) )

            Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
                                   .clip( RoundedCornerShape(20) )
                                   .border( 3.dp, colorPalette().background2 )
                                   .clickable { onDismiss() }
            ) {
                BasicText(
                    text = stringResource( R.string.cancel ),
                    style = typography().xs.medium.color( colorPalette().text ),
                    modifier = Modifier.padding( vertical = 16.dp )
                )
            }
        }
    }
}