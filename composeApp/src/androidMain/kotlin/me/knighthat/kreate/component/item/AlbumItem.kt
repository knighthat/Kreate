package me.knighthat.kreate.component.item

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastJoinToString
import coil3.compose.AsyncImage
import me.knighthat.innertube.model.InnertubeAlbum
import me.knighthat.innertube.response.Runs


const val ALBUM_ITEM_WIDTH = 108
const val ALBUM_ITEM_COMPONENT_SPACING = 5

@Composable
private fun AlbumItem(
    thumbnailUrl: String?,
    title: String,
    subtitle: Runs?,
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
                text = subtitle.runs.fastJoinToString( "" ) { it.text },
                color = MaterialTheme.colorScheme.outline,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 2
            )
    }

@Composable
fun AlbumItem( album: InnertubeAlbum, modifier: Modifier = Modifier ) =
    AlbumItem( album.thumbnails.firstOrNull()?.url, album.name, album.subtitle, modifier)