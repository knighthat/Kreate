package me.knighthat.kreate.component.item

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import me.knighthat.innertube.model.InnertubePlaylist
import me.knighthat.kreate.theme.AppTheme
import me.knighthat.kreate.util.shimmerEffect


const val PLAYLIST_ITEM_WIDTH = 108
const val PLAYLIST_ITEM_COMPONENT_SPACING = 5

@Composable
private fun PlaylistItem(
    thumbnailUrl: String?,
    title: String,
    subtitle: String?,
    modifier: Modifier
) =
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy( ALBUM_ITEM_COMPONENT_SPACING.dp ),
        modifier = modifier.requiredWidth( ALBUM_ITEM_WIDTH.dp )
    ) {
        AsyncImage(
            model = thumbnailUrl,
            contentScale = ContentScale.FillHeight,
            contentDescription = "$title's thumbnail",
            modifier = Modifier.size(ALBUM_ITEM_WIDTH.dp)
                               .aspectRatio( 1f )
                               .clip( RoundedCornerShape(5) )
        )

        Text(
            text = title,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 2
        )

        if( subtitle != null )
            Text(
                text = subtitle,
                color = MaterialTheme.colorScheme.outline,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 2
            )
    }

@Composable
@NonRestartableComposable
fun PlaylistItemPlaceholder( modifier: Modifier = Modifier) =
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy( ALBUM_ITEM_COMPONENT_SPACING.dp ),
        modifier = modifier.requiredWidth( ALBUM_ITEM_WIDTH.dp )
    ) {
        Box(
            Modifier.size(ALBUM_ITEM_WIDTH.dp)
                    .aspectRatio( 1f )
                    .clip( RoundedCornerShape(5) )
                    .shimmerEffect()
        )
    }

@Composable
fun PlaylistItem( playlist: InnertubePlaylist, modifier: Modifier = Modifier ) =
    PlaylistItem( playlist.thumbnails.firstOrNull()?.url, playlist.name, playlist.subtitleText, modifier )

@Preview
@Composable
private fun PlaylistItemPreview() = AppTheme( dynamicColor = false ) {
    PlaylistItem(
        thumbnailUrl = "https://lh3.googleusercontent.com/rxBhiakourW7eVAdmEtdgGjQ1B7_a0ymD0w39U_vIMiziBb-5fiN7mI9SsX8poDDzffqnAXAF4vaTxY=w544-h544-l90-rj",
        title = "Christmas Hits",
        subtitle = "Frank Sinatra, Nat King Cole, Bing Crosby, Pentatonix",
        modifier = Modifier
    )
}