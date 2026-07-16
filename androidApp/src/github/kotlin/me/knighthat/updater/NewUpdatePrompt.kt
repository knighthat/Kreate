package me.knighthat.updater

import android.app.DownloadManager
import android.os.Environment
import androidx.annotation.DrawableRes
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import app.kreate.android.R
import it.fast4x.rimusic.colorPalette
import it.fast4x.rimusic.typography
import it.fast4x.rimusic.ui.styling.shimmer
import it.fast4x.rimusic.utils.color
import it.fast4x.rimusic.utils.medium
import it.fast4x.rimusic.utils.semiBold
import me.knighthat.component.dialog.Dialog
import me.knighthat.component.dialog.InteractiveDialog
import me.knighthat.updater.DownloadAndInstallDialog.errorMessage
import me.knighthat.utils.Repository
import java.io.File

object NewUpdatePrompt: InteractiveDialog {

    override val dialogTitle: String
        @Composable
        get() = stringResource( R.string.update_available )

    override var isActive: Boolean by mutableStateOf( false )

    @Composable
    override fun DialogBody() {
        val uriHandler = LocalUriHandler.current

        @Composable
        fun Section(
            modifier: Modifier = Modifier,
            content: @Composable RowScope.() -> Unit = {}
        ) {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth( .9f )
                                   .then( modifier ),
                content = content
            )

            Spacer( Modifier.height( Dialog.SPACE_BETWEEN_SECTIONS.dp ) )
        }

        // Update information
        Section {
            BasicText(
                text = stringResource( R.string.app_update_dialog_new, Updater.build.readableSize ),
                style = typography().s.semiBold.copy( color = colorPalette().text )
            )
        }

        @Composable
        fun Action(
            text: String,
            @DrawableRes iconId: Int,
            onClick: () -> Unit
        ) = Section {
            BasicText(
                text = text,
                style = typography().xxs.semiBold.copy( color = colorPalette().textSecondary ),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth( .85f )
            )

            Spacer( Modifier.weight( 1f ) )

            Icon(
                painter = painterResource( iconId ),
                contentDescription = null,
                tint = colorPalette().shimmer,
                modifier = Modifier.size( 30.dp )
                                   .clickable( onClick = onClick )
                                   .align( Alignment.CenterVertically )
            )
        }

        // Option 1: Go to github page to download
        Action(
            stringResource( R.string.open_the_github_releases_web_page_and_download_latest_version ),
            R.drawable.globe,
        ) {
            hideDialog()

            val tagUrl = "${Repository.GITHUB}/${Repository.LATEST_TAG_URL}"
            uriHandler.openUri( tagUrl )
        }

        // Option 2: Go straight to download page to start the download
        val context = LocalContext.current
        Action(
            stringResource(R.string.download_latest_version_from_github_you_will_find_the_file_in_the_notification_area_and_you_can_install_by_clicking_on_it),
            R.drawable.downloaded
        ) {
            hideDialog()

            val downloadManager = context.getSystemService<DownloadManager>()
            if( downloadManager == null ) {
                errorMessage = context.getString( R.string.error_download_manager_init_failed )
                return@Action
            }
            // Saved to user's Android/data/me.knighthat.kreate(.debug)/Kreate-<buildType>.apk
            val apkFile = File(
                context.getExternalFilesDir( Environment.DIRECTORY_DOWNLOADS ),
                Updater.build.name
            )
            DownloadManager.Request( Updater.build.downloadUrl.toUri() )
                           .setDestinationUri( apkFile.toUri() )
                           .setTitle( Updater.build.name )
                           .setNotificationVisibility( DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED )
                           .let( downloadManager::enqueue )
        }
    }

    @Composable
    override fun Buttons() =
        Row(
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth( .9f )
                               .clip( RoundedCornerShape(20) )
                               .border( 3.dp, colorPalette().background2 )
                               .clickable { hideDialog() }
        ) {
            BasicText(
                text = stringResource( android.R.string.cancel ),
                style = typography().xs.medium.color( colorPalette().text ),
                modifier = Modifier.padding( vertical = 16.dp )
            )
        }
}