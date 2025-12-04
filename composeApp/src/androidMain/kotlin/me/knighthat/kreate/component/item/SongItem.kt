package me.knighthat.kreate.component.item

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
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
import me.knighthat.innertube.model.InnertubeSong
import me.knighthat.kreate.theme.AppTheme
import me.knighthat.kreate.util.shimmerEffect


const val SONG_ITEM_HORIZONTAL_PADDING = 10
const val SONG_ITEM_COMPONENTS_SPACING = 10
const val SONG_ITEM_MAX_HEIGHT = 60
const val SONG_ITEM_THUMBNAIL_SIZE = 50

@Composable
private fun SongItem(
    thumbnailUrl: String?,
    title: String,
    artists: String,
    showMenuIcon: Boolean,
    modifier: Modifier
) =
    Row(
        horizontalArrangement = Arrangement.spacedBy( SONG_ITEM_COMPONENTS_SPACING.dp ),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.requiredHeight( SONG_ITEM_MAX_HEIGHT.dp )
                           .padding( horizontal = SONG_ITEM_HORIZONTAL_PADDING.dp )
    ) {
        AsyncImage(
            model = thumbnailUrl,
            contentScale = ContentScale.FillHeight,
            contentDescription = "$title's thumbnail",
            modifier = Modifier.size( SONG_ITEM_THUMBNAIL_SIZE.dp )
        )

        Column(
            horizontalAlignment = Alignment.Start,
            modifier = Modifier.weight(  1f )
                               .fillMaxHeight()
        ) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1
            )

            Text(
                text = artists,
                color = MaterialTheme.colorScheme.outline,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1
            )
        }

        if( showMenuIcon )
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "song menu",
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding( 0.dp )
            )
    }

@Composable
@NonRestartableComposable
fun SongItemPlaceHolder( modifier: Modifier = Modifier ) =
    Row(
        horizontalArrangement = Arrangement.spacedBy( SONG_ITEM_COMPONENTS_SPACING.dp ),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.requiredHeight( SONG_ITEM_MAX_HEIGHT.dp )
                           .padding( horizontal = SONG_ITEM_HORIZONTAL_PADDING.dp )
    ) {
        Box(
            Modifier.size( SONG_ITEM_THUMBNAIL_SIZE.dp )
                    .shimmerEffect()
        )

        Column(
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy( 3.dp ),
            modifier = Modifier.weight(  1f )
                               .fillMaxHeight()
        ) {
            Box(
                contentAlignment = Alignment.BottomStart,
                modifier = Modifier.weight( 1f )
            ) {
                Box(
                    Modifier.clip( RoundedCornerShape(10) )
                            .fillMaxSize( .75f )
                            .shimmerEffect()
                )
            }

            Box(
                contentAlignment = Alignment.TopStart,
                modifier = Modifier.weight( 1f )
            ) {
                Box(
                    Modifier.clip( RoundedCornerShape(10) )
                            .fillMaxWidth( .35f )
                            .fillMaxHeight( .5f )
                            .shimmerEffect()
                )
            }
        }
    }

@Composable
fun SongItem( song: InnertubeSong, showMenuIcon: Boolean, modifier: Modifier = Modifier ) =
    SongItem( song.thumbnails.firstOrNull()?.url, song.name, song.artistsText, showMenuIcon, modifier )

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL)
@Composable
private fun InnertubeSongPreview() = AppTheme(dynamicColor = false) {
    SongItem(
        thumbnailUrl = "https://i.ibb.co/3mLGkPwY/app-logo.png",
        title = "Kreate is awesome",
        artists = "Knight Hat",
        showMenuIcon = true,
        modifier = Modifier
    )
}