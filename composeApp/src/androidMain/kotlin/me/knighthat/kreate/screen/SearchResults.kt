package me.knighthat.kreate.screen

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastJoinToString
import coil3.compose.AsyncImage
import kotlinx.coroutines.flow.combine
import me.knighthat.innertube.model.InnertubeAlbum
import me.knighthat.innertube.model.InnertubeArtist
import me.knighthat.innertube.model.InnertubeItem
import me.knighthat.innertube.model.InnertubePlaylist
import me.knighthat.innertube.model.InnertubeSong
import me.knighthat.kreate.component.item.ALBUM_ITEM_THUMBNAIL_ROUNDNESS
import me.knighthat.kreate.component.item.SONG_ITEM_COMPONENTS_SPACING
import me.knighthat.kreate.component.item.SONG_ITEM_HORIZONTAL_PADDING
import me.knighthat.kreate.component.item.SONG_ITEM_MAX_HEIGHT
import me.knighthat.kreate.component.item.SONG_ITEM_THUMBNAIL_SIZE
import me.knighthat.kreate.component.item.SongItemPlaceHolder
import me.knighthat.kreate.viewmodel.SearchResultViewModel
import org.koin.compose.viewmodel.koinViewModel


@Composable
@NonRestartableComposable
private fun Item( item: InnertubeItem, modifier: Modifier = Modifier ) =
    Row(
        horizontalArrangement = Arrangement.spacedBy( SONG_ITEM_COMPONENTS_SPACING.dp ),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.border( 1.dp, MaterialTheme.colorScheme.inverseOnSurface )
                           .fillMaxWidth()
                           .requiredHeight( SONG_ITEM_MAX_HEIGHT.dp )
                           .padding( horizontal = SONG_ITEM_HORIZONTAL_PADDING.dp )
    ) {
        AsyncImage(
            model = item.thumbnails.firstOrNull()?.url,
            contentScale = ContentScale.FillHeight,
            contentDescription = "${item.name}'s thumbnail",
            modifier = Modifier.size( SONG_ITEM_THUMBNAIL_SIZE.dp )
                               .clip(
                                   when( item ) {
                                       is InnertubeArtist   -> CircleShape
                                       is InnertubeAlbum    -> ALBUM_ITEM_THUMBNAIL_ROUNDNESS
                                       else                 -> RectangleShape
                                   }
                               )
        )

        Column(
            horizontalAlignment = Alignment.Start,
            modifier = Modifier.weight( 1f )
                               .fillMaxHeight()
        ) {
            Text(
                text = item.name,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1
            )

            val subtitle = when( item ) {
                is InnertubeSong        -> item.artistsText
                is InnertubeArtist      -> item.shortNumMonthlyAudience
                is InnertubeAlbum       -> item.subtitle?.runs?.fastJoinToString( "" ) { it.text }
                is InnertubePlaylist    -> item.subtitle?.runs?.fastJoinToString( "" ) { it.text }
                else                    -> null
            }
            if( subtitle != null )
                Text(
                    text = subtitle,
                    color = MaterialTheme.colorScheme.outline,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1
                )
        }
    }

@Composable
fun SearchResults(
    viewModel: SearchResultViewModel = koinViewModel()
) {
    val results by viewModel.results.collectAsState()
    var hasMore by remember { mutableStateOf(false) }

    LazyColumn(
        state = viewModel.lazyListState
    ) {

        items(
            items = results,
            key = InnertubeItem::id
        ) { item ->
            Item( item )
        }

        if( hasMore )
            item( SearchResultViewModel.LOAD_MORE_KEY ) {
                SongItemPlaceHolder()
            }
    }

    LaunchedEffect( Unit ) {
        combine(
            viewModel.continuation,
            viewModel.sharedSearchProperties.tab
        ) { continuation, tab ->
            continuation[tab]?.second != null
        }.collect {
            hasMore = it
        }
    }
}