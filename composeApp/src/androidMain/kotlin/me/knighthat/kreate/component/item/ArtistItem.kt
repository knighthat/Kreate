package me.knighthat.kreate.component.item

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import me.knighthat.innertube.model.InnertubeArtist


const val ARTIST_ITEM_WIDTH = 108
const val ARTIST_ITEM_COMPONENT_SPACING = 5

@Composable
private fun ArtistItem(
    thumbnailUrl: String?,
    title: String,
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
            modifier = Modifier.size( ALBUM_ITEM_WIDTH.dp )
                               .aspectRatio( 1f )
                               .clip( CircleShape )
        )

        Text(
            text = title,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 2
        )
    }

@Composable
fun ArtistItem( artist: InnertubeArtist, modifier: Modifier = Modifier ) =
    ArtistItem( artist.thumbnails.firstOrNull()?.url, artist.name, modifier )