package it.fast4x.rimusic.ui.items

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.kreate.android.R
import it.fast4x.innertube.Innertube
import it.fast4x.rimusic.cleanPrefix
import it.fast4x.rimusic.colorPalette
import it.fast4x.rimusic.enums.ThumbnailRoundness
import it.fast4x.rimusic.models.Album
import it.fast4x.rimusic.thumbnailShape
import it.fast4x.rimusic.typography
import it.fast4x.rimusic.ui.components.themed.TextPlaceholder
import it.fast4x.rimusic.ui.styling.Dimensions
import it.fast4x.rimusic.ui.styling.shimmer
import it.fast4x.rimusic.utils.conditional
import it.fast4x.rimusic.utils.rememberPreference
import it.fast4x.rimusic.utils.secondary
import it.fast4x.rimusic.utils.semiBold
import it.fast4x.rimusic.utils.shimmerEffect
import it.fast4x.rimusic.utils.thumbnailRoundnessKey
import me.knighthat.coil.ImageCacheFactory

@Composable
fun AlbumItem(
    album: Album,
    thumbnailSizePx: Int,
    thumbnailSizeDp: Dp,
    modifier: Modifier = Modifier,
    alternative: Boolean = false,
    yearCentered: Boolean = true,
    showAuthors: Boolean = false,
    disableScrollingText: Boolean,
    isYoutubeAlbum: Boolean = false
) {
    AlbumItem(
        thumbnailUrl = album.thumbnailUrl,
        title = album.title,
        authors = album.authorsText,
        year = album.year,
        yearCentered = yearCentered,
        thumbnailSizePx = thumbnailSizePx,
        thumbnailSizeDp = thumbnailSizeDp,
        alternative = alternative,
        showAuthors = showAuthors,
        modifier = modifier,
        disableScrollingText = disableScrollingText,
        isYoutubeAlbum = isYoutubeAlbum
    )
}

@Composable
fun AlbumItem(
    album: Innertube.AlbumItem,
    thumbnailSizePx: Int,
    thumbnailSizeDp: Dp,
    modifier: Modifier = Modifier,
    alternative: Boolean = false,
    yearCentered: Boolean? = true,
    showAuthors: Boolean? = false,
    isYoutubeAlbum: Boolean = false,
    disableScrollingText: Boolean
) {
    AlbumItem(
        thumbnailUrl = album.thumbnail?.url,
        title = album.info?.name,
        authors = album.authors?.joinToString(", ") { it.name ?: "" },
        year = album.year,
        yearCentered = yearCentered,
        thumbnailSizePx = thumbnailSizePx,
        thumbnailSizeDp = thumbnailSizeDp,
        alternative = alternative,
        modifier = modifier,
        disableScrollingText = disableScrollingText,
        isYoutubeAlbum = isYoutubeAlbum
    )
}

@Composable
fun AlbumItem(
    thumbnailUrl: String?,
    title: String?,
    authors: String?,
    year: String?,
    yearCentered: Boolean? = true,
    thumbnailSizePx: Int,
    thumbnailSizeDp: Dp,
    modifier: Modifier = Modifier,
    alternative: Boolean = false,
    showAuthors: Boolean? = false,
    disableScrollingText: Boolean,
    isYoutubeAlbum: Boolean = false
) {
    ItemContainer(
        alternative = alternative,
        thumbnailSizeDp = thumbnailSizeDp,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Box {
            ImageCacheFactory.Thumbnail(
                thumbnailUrl = thumbnailUrl,
                contentScale = ContentScale.FillWidth
            )

            if (isYoutubeAlbum) {
                Image(
                    painter = painterResource(R.drawable.ytmusic),
                    colorFilter = ColorFilter.tint(Color.Red.copy(0.75f).compositeOver(Color.White)),
                    modifier = Modifier
                        .size(40.dp)
                        .padding(all = 5.dp),
                    contentDescription = "Background Image",
                    contentScale = ContentScale.Fit
                )
            }
        }
        ItemInfoContainer {
            BasicText(
                text = cleanPrefix(title ?: ""),
                style = typography().xs.semiBold,
                maxLines = 1, //if (alternative) 1 else 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .conditional(!disableScrollingText) { basicMarquee(iterations = Int.MAX_VALUE) }
            )

            if (!alternative || showAuthors == true) {
                authors?.let {
                    BasicText(
                        text = cleanPrefix(authors),
                        style = typography().xs.semiBold.secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .conditional(!disableScrollingText) { basicMarquee(iterations = Int.MAX_VALUE) }
                            .align(
                                if (yearCentered == true) Alignment.CenterHorizontally else Alignment.Start
                            )
                    )
                }
            }

            BasicText(
                text = year ?: "",
                style = typography().xxs.semiBold.secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .align(
                        if (yearCentered == true) Alignment.CenterHorizontally else Alignment.Start
                    )
            )
        }
    }
}

@Composable
fun AlbumItemPlaceholder(
    thumbnailSizeDp: Dp,
    modifier: Modifier = Modifier,
    alternative: Boolean = false
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

        ItemInfoContainer {
            TextPlaceholder()

            if (!alternative) {
                TextPlaceholder()
            }

            TextPlaceholder(
                modifier = Modifier
                    .padding(top = 4.dp)
            )
        }
    }
}

/**
 * New component is more resemble to the final
 * [AlbumItem] that's currently being used.
 */
@Composable
fun AlbumPlaceholder(
    showYear: Boolean = true,
    modifier: Modifier = Modifier
) {
    val thumbnailSizeDp = Dimensions.thumbnails.album

    val thumbnailRoundness by rememberPreference( thumbnailRoundnessKey, ThumbnailRoundness.None )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy( 12.dp ),
        modifier = modifier.padding(
                               vertical = Dimensions.itemsVerticalPadding,
                               horizontal = 16.dp
                           )
                           // [width] placed behind padding to ensure
                           // outer padding instead of inner
                           .width( thumbnailSizeDp )
    ) {

        Row( Modifier.fillMaxWidth() ) {
            Box(
                Modifier.size( thumbnailSizeDp )
                        .clip( thumbnailRoundness.shape )
                        .shimmerEffect()
            )
        }

        Row(
            Modifier.padding( top = 4.dp )
                    .fillMaxWidth( .7f )
                    .clip( RoundedCornerShape(25) )
        ) {
            // Title with shimmer effect
            BasicText(
                text = "",
                style = typography().xs.semiBold.secondary,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                modifier = Modifier.weight( 1f ).shimmerEffect()
            )
        }

        if( showYear )
            Row(
                Modifier.padding( top = 4.dp )
                        .fillMaxWidth( .3f )
                        .clip( RoundedCornerShape(25) )
            ) {
                // Year (if enabled) with shimmer effect
                BasicText(
                    text = "",
                    style = typography().xs.semiBold.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.weight( 1f ).shimmerEffect()
                )
            }
    }
}


@Composable
fun AlbumItemGridPlaceholder(
    thumbnailSizeDp: Dp,
    modifier: Modifier = Modifier,
    alternative: Boolean = false,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        repeat(3) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = modifier.padding(
                    vertical = Dimensions.itemsVerticalPadding,
                    horizontal = 0.dp
                )
            ) {
                Box(
                    Modifier.size(thumbnailSizeDp - 8.dp)
                        .clip(thumbnailShape())
                        .shimmerEffect()
                )
                Box(
                    Modifier
                        .padding(top = 8.dp)
                        .width((thumbnailSizeDp - 8.dp) * 0.8f)
                        .height(12.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .shimmerEffect()
                )
                Box(
                    Modifier
                        .padding(top = 4.dp)
                        .width((thumbnailSizeDp - 8.dp) * 0.4f)
                        .height(10.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .shimmerEffect()
                )
            }
        }
    }
}

@Composable
fun AlbumItemListPlaceholder(
    thumbnailSizeDp: Dp,
    modifier: Modifier = Modifier,
    alternative: Boolean = false,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(thumbnailSizeDp - 8.dp)
                .clip(thumbnailShape())
                .shimmerEffect()
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(16.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .shimmerEffect()
            )
            Box(
                Modifier
                    .fillMaxWidth(0.6f)
                    .height(12.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .shimmerEffect()
            )
        }
    }
}