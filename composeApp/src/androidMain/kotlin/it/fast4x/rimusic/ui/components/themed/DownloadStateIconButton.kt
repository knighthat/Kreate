package it.fast4x.rimusic.ui.components.themed

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.Indication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import app.kreate.android.R
import app.kreate.android.service.download.CacheState
import app.kreate.android.service.download.DownloadHelper
import it.fast4x.rimusic.ui.styling.LocalAppearance
import org.koin.compose.koinInject

@UnstableApi
@Composable
fun DownloadStateIconButton(
    onClick: () -> Unit,
    onCancelButtonClicked: () -> Unit,
    @DrawableRes icon: Int,
    color: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    indication: Indication? = null,
    downloadState: Int
) {

    if (downloadState == Download.STATE_DOWNLOADING
                || downloadState == Download.STATE_QUEUED
                || downloadState == Download.STATE_RESTARTING
                ){
        Image(
            painter = painterResource(R.drawable.download_progress),
            contentDescription = null,
            colorFilter = ColorFilter.tint(color),
            modifier = Modifier
                .clickable(
                    indication = indication ?: ripple(bounded = false),
                    interactionSource = remember { MutableInteractionSource() },
                    enabled = enabled,
                    onClick = onCancelButtonClicked
                )
                .then(modifier)
        )
        /*
        CircularProgressIndicator(
            strokeWidth = 2.dp,
            color = colorPalette().text,
            modifier = Modifier
                .size(16.dp)

        )

         */
    } else {
        Image(
            painter = painterResource(icon),
            contentDescription = null,
            colorFilter = ColorFilter.tint(color),
            modifier = Modifier
                .clickable(
                    indication = indication ?: ripple(bounded = false),
                    interactionSource = remember { MutableInteractionSource() },
                    enabled = enabled,
                    onClick = onClick
                )
                .then(modifier)
        )
    }
}

@Composable
fun CacheIcon(
    mediaItem: MediaItem,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    indication: Indication? = null,
    cacheState: CacheState = koinInject(),
    downloadHelper: DownloadHelper = koinInject()
) {
    val (colorPalette, _) = LocalAppearance.current
    val state by cacheState.stateOf( mediaItem.mediaId )
        .collectAsStateWithLifecycle(CacheState.State.Unknown)

    val iconId = when( state ) {
        CacheState.State.Downloaded -> R.drawable.downloaded
        CacheState.State.Downloading -> R.drawable.download_progress
        is CacheState.State.Cached,
        CacheState.State.Unknown -> R.drawable.download
    }
    val color = when( state ) {
        is CacheState.State.Cached,
        CacheState.State.Downloaded,
        CacheState.State.Downloading -> colorPalette.accent
        CacheState.State.Unknown -> colorPalette.textDisabled
    }

    Image(
        painter = painterResource( iconId ),
        contentDescription = null,
        colorFilter = ColorFilter.tint(color),
        modifier = modifier.combinedClickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = indication ?: ripple(bounded = false),
            enabled = enabled,
            onClick = {
                downloadHelper.downloadMediaItem( mediaItem )
            },
            onLongClick = {
                downloadHelper.removeMediaItem( mediaItem )
            }
        )
    )
}