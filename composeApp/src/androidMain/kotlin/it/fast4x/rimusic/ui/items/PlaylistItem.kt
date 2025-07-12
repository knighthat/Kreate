package it.fast4x.rimusic.ui.items

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.kreate.android.R
import coil.compose.AsyncImage
import it.fast4x.innertube.Innertube
import it.fast4x.rimusic.Database
import it.fast4x.rimusic.MONTHLY_PREFIX
import it.fast4x.rimusic.PINNED_PREFIX
import it.fast4x.rimusic.cleanPrefix
import it.fast4x.rimusic.colorPalette
import it.fast4x.rimusic.models.PlaylistPreview
import it.fast4x.rimusic.models.Song
import it.fast4x.rimusic.thumbnailShape
import it.fast4x.rimusic.typography
import it.fast4x.rimusic.ui.components.themed.TextPlaceholder
import it.fast4x.rimusic.ui.styling.onOverlay
import it.fast4x.rimusic.ui.styling.overlay
import it.fast4x.rimusic.ui.styling.shimmer
import it.fast4x.rimusic.utils.checkFileExists
import it.fast4x.rimusic.utils.color
import it.fast4x.rimusic.utils.conditional
import it.fast4x.rimusic.utils.medium
import it.fast4x.rimusic.utils.secondary
import it.fast4x.rimusic.utils.semiBold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import me.knighthat.coil.ImageCacheFactory

val HTTP_REGEX = Regex("^https?://.*")
val FOUR_CORNERS = listOf( Alignment.TopStart, Alignment.TopEnd, Alignment.BottomStart, Alignment.BottomEnd )

@Composable
fun RenderThumbnail(
    thumbnailUrl: String,
    contentDescription: String?,
    contentScale: ContentScale,
    modifier: Modifier = Modifier
) {
    if( thumbnailUrl.matches( HTTP_REGEX ) )
        ImageCacheFactory.Thumbnail( thumbnailUrl, contentDescription, contentScale, modifier )
    else
        AsyncImage(
            model = thumbnailUrl,
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = modifier
        )
}

@Composable
private fun BoxWithConstraintsScope.ThumbnailRenderer(
    thumbnailUrls: List<String>,
    contentScale: ContentScale = ContentScale.Crop
) {
    if( thumbnailUrls.size >= 4 ) {
        val halfWidth = maxWidth / 2
        val halfHeight = maxHeight / 2

        FOUR_CORNERS.forEachIndexed { index, corner ->
            RenderThumbnail(
                thumbnailUrl = thumbnailUrls[index],
                contentDescription = corner.toString(),
                contentScale = contentScale,
                modifier = Modifier.size( halfWidth, halfHeight )
                                   .align( corner )
            )
        }
    }
    else if( thumbnailUrls.isNotEmpty() )
        RenderThumbnail( thumbnailUrls.first(), "fullSizeRender", contentScale )
}

@Composable
fun PlaylistItem(
    @DrawableRes icon: Int,
    colorTint: Color,
    name: String?,
    songCount: Int?,
    thumbnailSizeDp: Dp,
    modifier: Modifier = Modifier,
    alternative: Boolean = false,
    showName: Boolean = true,
    iconSize: Dp = 34.dp,
    disableScrollingText: Boolean,
    isYoutubePlaylist : Boolean = false,
    isEditable : Boolean = false
) {
    PlaylistItem(
        thumbnailContent = {
            Image(
                painter = painterResource(icon),
                contentDescription = null,
                colorFilter = ColorFilter.tint(colorTint),
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(iconSize)
            )
        },
        songCount = songCount,
        name = name,
        channelName = null,
        thumbnailSizeDp = thumbnailSizeDp,
        modifier = modifier,
        alternative = alternative,
        showName = showName,
        disableScrollingText = disableScrollingText,
        isYoutubePlaylist = isYoutubePlaylist,
        isEditable = isEditable
    )
}

@Composable
fun PlaylistItem(
    playlist: PlaylistPreview,
    thumbnailSizePx: Int,
    thumbnailSizeDp: Dp,
    modifier: Modifier = Modifier,
    alternative: Boolean = false,
    showName: Boolean = true,
    disableScrollingText: Boolean,
    isYoutubePlaylist: Boolean,
    isEditable: Boolean
) {
    val context = LocalContext.current

    val thumbnails by remember {
        val customThumbnail = checkFileExists( context, "thumbnail/playlist_${playlist.playlist.id}" )

        if( customThumbnail != null )
            flowOf( listOf( customThumbnail ) )
        else
            Database.songPlaylistMapTable
                    .sortSongsByPlayTime( playlist.playlist.id )
                    .distinctUntilChanged()
                    .map { list ->
                        // Ensure it only takes not null thumbnailUrl
                        list.mapNotNull( Song::thumbnailUrl ).takeLast( 4 )
                    }
    }.collectAsState( emptyList(), Dispatchers.IO )

    PlaylistItem(
        browseId = playlist.playlist.browseId,
        thumbnailContent = { ThumbnailRenderer( thumbnails ) },
        songCount = playlist.songCount,
        name = playlist.playlist.name,
        channelName = null,
        thumbnailSizeDp = thumbnailSizeDp,
        modifier = modifier,
        alternative = alternative,
        showName = showName,
        disableScrollingText = disableScrollingText,
        isYoutubePlaylist = isYoutubePlaylist,
        isEditable = isEditable
    )
}

@Composable
fun PlaylistItem(
    playlist: Innertube.PlaylistItem,
    thumbnailSizePx: Int,
    thumbnailSizeDp: Dp,
    modifier: Modifier = Modifier,
    alternative: Boolean = false,
    showSongsCount: Boolean = true,
    disableScrollingText: Boolean,
    isYoutubePlaylist : Boolean = false,
    isEditable : Boolean = false
) {
    PlaylistItem(
        thumbnailContent = thumb@ {
            val thumbnailUrl = playlist.thumbnail?.url ?: return@thumb

            RenderThumbnail(
                thumbnailUrl,
                "${playlist.title}s_thumbnail",
                ContentScale.FillHeight,
                Modifier.fillMaxSize()
            )
        },
        songCount = playlist.songCount,
        showSongsCount = showSongsCount,
        name = playlist.info?.name,
        channelName = playlist.channel?.name,
        thumbnailSizeDp = thumbnailSizeDp,
        modifier = modifier,
        alternative = alternative,
        disableScrollingText = disableScrollingText,
        isYoutubePlaylist = isYoutubePlaylist,
        isEditable = isEditable
    )
}

@Composable
fun PlaylistItem(
    browseId: String? = null,
    thumbnailContent: @Composable BoxWithConstraintsScope.() -> Unit,
    songCount: Int?,
    name: String?,
    channelName: String?,
    thumbnailSizeDp: Dp,
    modifier: Modifier = Modifier,
    alternative: Boolean = false,
    showName: Boolean = true,
    showSongsCount: Boolean = true,
    disableScrollingText: Boolean,
    isYoutubePlaylist : Boolean = false,
    isEditable : Boolean = false
) {
    ItemContainer(
        alternative = alternative,
        thumbnailSizeDp = thumbnailSizeDp,
        modifier = modifier
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth()
                               .aspectRatio( 1f )
                               .clip( thumbnailShape() )
                               .background( colorPalette().background4 )
        ) {
            thumbnailContent()

            name ?: return@BoxWithConstraints
            val (icon, color) = when {
                name.startsWith( PINNED_PREFIX, true ) ->
                    painterResource( R.drawable.pin_filled ) to colorPalette().accent

                name.startsWith( MONTHLY_PREFIX, true ) ->
                    painterResource( R.drawable.stat_month ) to colorPalette().accent

                isYoutubePlaylist ->
                    painterResource( R.drawable.ytmusic ) to Color.Red.copy( .75f ).compositeOver( Color.White )

                else ->
                    ColorPainter(Color.Transparent) to Color.Transparent
            }

            Icon(
                painter = icon,
                contentDescription = "origin_indicator",
                tint = color,
                modifier = Modifier.size( 40.dp ).padding( all = 5.dp )
            )

            songCount?.let {
                if( !showSongsCount ) return@let

                BasicText(
                    text = songCount.toString(),
                    style = typography().xxs.medium.color( colorPalette().onOverlay ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding( all = 4.dp )
                                       .background(
                                           color = colorPalette().overlay,
                                           shape = RoundedCornerShape(4.dp)
                                       )
                                       .padding( all = 6.dp)
                                       .align(Alignment.BottomEnd)
                )
            }
        }

        ItemInfoContainer(
            horizontalAlignment = if (alternative && channelName == null) Alignment.CenterHorizontally else Alignment.Start,
            modifier = Modifier
                .fillMaxSize()
        ) {
            if (showName)
                if (name != null) {
                    BasicText(
                        //text = name.substringAfter(PINNED_PREFIX) ?: "",
                        text = cleanPrefix(name),
                        style = typography().xs.semiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .conditional(!disableScrollingText) { basicMarquee(iterations = Int.MAX_VALUE) }
                    )
                }

            channelName?.let {
                BasicText(
                    text = channelName,
                    style = typography().xs.semiBold.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .conditional(!disableScrollingText) { basicMarquee(iterations = Int.MAX_VALUE) }
                )
            }
        }
    }
}

@Composable
fun PlaylistItemPlaceholder(
    thumbnailSizeDp: Dp,
    modifier: Modifier = Modifier,
    alternative: Boolean = false,
) {
    ItemContainer(
        alternative = alternative,
        thumbnailSizeDp = thumbnailSizeDp,
        modifier = modifier
    ) {
        Spacer(
            modifier = Modifier
                .background(color = colorPalette().shimmer, shape = thumbnailShape())
                .size(thumbnailSizeDp)
        )

        ItemInfoContainer(
            horizontalAlignment = if (alternative) Alignment.CenterHorizontally else Alignment.Start,
        ) {
            TextPlaceholder()
            TextPlaceholder()
        }
    }
}
