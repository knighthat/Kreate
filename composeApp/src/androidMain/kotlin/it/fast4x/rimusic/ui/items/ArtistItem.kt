package it.fast4x.rimusic.ui.items

import androidx.compose.foundation.Image

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
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
import coil.compose.AsyncImage
import it.fast4x.innertube.Innertube
import it.fast4x.rimusic.cleanPrefix

import it.fast4x.rimusic.models.Artist
import it.fast4x.rimusic.thumbnailShape
import it.fast4x.rimusic.typography

import it.fast4x.rimusic.utils.conditional
import it.fast4x.rimusic.utils.secondary
import it.fast4x.rimusic.utils.semiBold
import it.fast4x.rimusic.utils.thumbnail
import it.fast4x.rimusic.utils.shimmerEffect

@Composable
fun ArtistItem(
    artist: Artist,
    thumbnailSizePx: Int,
    thumbnailSizeDp: Dp,
    modifier: Modifier = Modifier,
    alternative: Boolean = false,
    showName: Boolean = true,
    disableScrollingText: Boolean,
    isYoutubeArtist : Boolean = false
) {
    ArtistItem(
        thumbnailUrl = artist.thumbnailUrl,
        name = artist.name ?: "",
        subscribersCount = null,
        thumbnailSizePx = thumbnailSizePx,
        thumbnailSizeDp = thumbnailSizeDp,
        modifier = modifier,
        alternative = alternative,
        showName = showName,
        disableScrollingText = disableScrollingText,
        isYoutubeArtist = isYoutubeArtist
    )
}

@Composable
fun ArtistItem(
    artist: Innertube.ArtistItem,
    thumbnailSizePx: Int,
    thumbnailSizeDp: Dp,
    modifier: Modifier = Modifier,
    alternative: Boolean = false,
    disableScrollingText: Boolean,
    isYoutubeArtist : Boolean = false,
    smallThumbnail: Boolean = false
) {
    ArtistItem(
        thumbnailUrl = artist.thumbnail?.url,
        name = artist.info?.name,
        subscribersCount = artist.subscribersCountText,
        thumbnailSizePx = thumbnailSizePx,
        thumbnailSizeDp = thumbnailSizeDp,
        modifier = modifier,
        alternative = alternative,
        disableScrollingText = disableScrollingText,
        isYoutubeArtist = isYoutubeArtist,
        smallThumbnail = smallThumbnail
    )
}

@Composable
fun ArtistItem(
    thumbnailUrl: String?,
    name: String?,
    subscribersCount: String?,
    thumbnailSizePx: Int,
    thumbnailSizeDp: Dp,
    modifier: Modifier = Modifier,
    alternative: Boolean = false,
    showName: Boolean = true,
    disableScrollingText: Boolean,
    isYoutubeArtist : Boolean = false,
    smallThumbnail: Boolean = false
) {
    ItemContainer(
        alternative = alternative,
        thumbnailSizeDp = thumbnailSizeDp,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Box {
            AsyncImage(
                model = thumbnailUrl?.thumbnail(thumbnailSizePx),
                contentDescription = null,
                modifier = Modifier
                    //.clip(CircleShape)
                    .clip(thumbnailShape())
                    .requiredSize(thumbnailSizeDp)
            )
            if (isYoutubeArtist) {
                Image(
                    painter = painterResource(R.drawable.ytmusic),
                    colorFilter = ColorFilter.tint(
                        Color.Red.copy(0.75f).compositeOver(Color.White)
                    ),
                    modifier = Modifier
                        .size(if (smallThumbnail) 30.dp else 40.dp)
                        .padding(all = 5.dp),
                    contentDescription = "Background Image",
                    contentScale = ContentScale.Fit
                )
            }
        }

    if (showName)
        ItemInfoContainer(
            horizontalAlignment = if (alternative) Alignment.CenterHorizontally else Alignment.Start,
        ) {
            BasicText(
                text = cleanPrefix(name ?: ""),
                style = typography().xs.semiBold,
                maxLines = 1, //if (alternative) 1 else 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .conditional(!disableScrollingText) { basicMarquee(iterations = Int.MAX_VALUE) }
            )

            subscribersCount?.let {
                BasicText(
                    text = subscribersCount,
                    style = typography().xxs.semiBold.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .conditional(!disableScrollingText) { basicMarquee(iterations = Int.MAX_VALUE) }
                )
            }
        }
    }
}

@Composable
fun ArtistItemPlaceholder(
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
            Modifier.size(thumbnailSizeDp)
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
