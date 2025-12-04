package me.knighthat.kreate

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastMapNotNull
import kotlinx.coroutines.delay
import me.knighthat.innertube.model.InnertubeAlbum
import me.knighthat.innertube.model.InnertubeArtist
import me.knighthat.innertube.model.InnertubeItem
import me.knighthat.innertube.model.InnertubePlaylist
import me.knighthat.innertube.model.InnertubeSong
import me.knighthat.innertube.response.Runs
import me.knighthat.innertube.response.Thumbnails
import me.knighthat.kreate.component.LoadMoreContentType
import me.knighthat.kreate.component.item.ALBUM_ITEM_WIDTH
import me.knighthat.kreate.component.item.ARTIST_ITEM_WIDTH
import me.knighthat.kreate.component.item.AlbumItem
import me.knighthat.kreate.component.item.ArtistItem
import me.knighthat.kreate.component.item.PLAYLIST_ITEM_WIDTH
import me.knighthat.kreate.component.item.PlaylistItem
import me.knighthat.kreate.component.item.PlaylistItemPlaceholder
import me.knighthat.kreate.component.item.SONG_ITEM_HORIZONTAL_PADDING
import me.knighthat.kreate.component.item.SongItem
import me.knighthat.kreate.component.item.SongItemPlaceHolder
import me.knighthat.kreate.di.TopLayoutConfiguration.Title
import me.knighthat.kreate.theme.AppTheme
import me.knighthat.kreate.viewmodel.HomeScreenViewModel
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.Duration.Companion.milliseconds


private const val TITLE_HEIGHT = 40
private const val QUICK_PICK_ITEMS_PER_PAGE = 4
private const val ROW_ITEM_SPACING = 10
private const val ROW_ITEM_CONTENT_PADDING = 5
private const val HEADER_VERTICAL_PADDING = 5

@Composable
@NonRestartableComposable
private fun Header( title: String, browseId: String?, params: String? ) =
    Box(
        contentAlignment = Alignment.BottomStart,
        modifier = Modifier.fillMaxWidth()
                           .padding(
                               horizontal = SONG_ITEM_HORIZONTAL_PADDING.dp,
                               vertical = HEADER_VERTICAL_PADDING.dp
                           )
                           .height( TITLE_HEIGHT.dp )
    ) {
        Row {
            val color = MaterialTheme.colorScheme.secondary

            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = color,
                modifier = Modifier.weight( 1f )
            )

            if( browseId != null )
                Icon(
                    imageVector = Icons.AutoMirrored.Default.ArrowForward,
                    contentDescription = "See more",
                    tint = color,
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
        }
    }

@Composable
private fun SongPages( songs: List<InnertubeSong> ) {
    val pages = remember( songs ) {
        songs.chunked( QUICK_PICK_ITEMS_PER_PAGE )
    }
    val pagerState = rememberPagerState { pages.size }
    val pageSize = remember {
        object : PageSize {
            override fun Density.calculateMainAxisPageSize(
                availableSpace: Int,
                pageSpacing: Int
            ): Int =
                if( pagerState.currentPage == pagerState.pageCount - 1 ) {
                    // 4 - it's spacing and the page before it
                    availableSpace - 4 * pageSpacing
                } else
                    // Each page takes 3/4 of the screen's width
                    ((availableSpace - 2 * pageSpacing) * .75f).toInt()
        }
    }

    Column( Modifier.fillMaxWidth() ) {
        HorizontalPager(
            state = pagerState,
            pageSize = pageSize,
            beyondViewportPageCount = 1,
            verticalAlignment = Alignment.Top
        ) { index ->
            Column {
                pages[index].fastForEach { song ->
                    SongItem( song, false )
                }
            }
        }

        Row(
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
                               .align( Alignment.CenterHorizontally )
                               .padding( bottom = 8.dp )
        ) {
            repeat(pagerState.pageCount) { iteration ->
                val color = if (pagerState.currentPage == iteration)
                    MaterialTheme.colorScheme.onSurface
                else
                    MaterialTheme.colorScheme.onSurfaceVariant

                Box(
                    Modifier.padding( 2.dp )
                            .clip( CircleShape )
                            .background( color )
                            .size( 5.dp )
                )
            }
        }
    }
}

@Composable
@NonRestartableComposable
private fun SongPagesPlaceholder() =
    Column( Modifier.fillMaxWidth() ) {
        repeat( QUICK_PICK_ITEMS_PER_PAGE ) {
            SongItemPlaceHolder()
        }
    }

@Composable
private fun ItemRow( items: List<InnertubeItem>, modifier: Modifier = Modifier) =
    LazyHorizontalGrid(
        rows = GridCells.Fixed(2),
        horizontalArrangement = Arrangement.spacedBy( ROW_ITEM_SPACING.dp ),
        contentPadding = PaddingValues( horizontal = ROW_ITEM_CONTENT_PADDING.dp ),
        modifier = modifier.fillMaxWidth()
                           // Fake height - this will make sure it wraps around enough
                           // while preventing it from complaining about not having
                           // appropriate height
                           .requiredHeightIn(
                               max = (PLAYLIST_ITEM_WIDTH + ARTIST_ITEM_WIDTH + ALBUM_ITEM_WIDTH).dp
                           )
    ) {
        items(
            items = items,
            key = InnertubeItem::id
        ) { item ->
            when( item ) {
                is InnertubeArtist      -> ArtistItem( item )
                is InnertubeAlbum       -> AlbumItem( item )
                is InnertubePlaylist    -> PlaylistItem( item )
            }
        }
    }

@Composable
@NonRestartableComposable
private fun PlaylistRowPlaceholder( modifier: Modifier = Modifier ) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy( ROW_ITEM_SPACING.dp ),
        modifier = modifier.fillMaxWidth()
                           .padding( start = ROW_ITEM_CONTENT_PADDING.dp )
                           .horizontalScroll(
                               state = rememberScrollState(),
                               enabled = false
                           )
    ) {
        repeat( 5 ) {
            PlaylistItemPlaceholder()
        }
    }
}

@Composable
fun HomeScreen(
    viewModel: HomeScreenViewModel = koinViewModel()
) =
    PullToRefreshBox(
        isRefreshing = viewModel.isRefreshing,
        onRefresh = viewModel::onRefresh,
        modifier = Modifier.fillMaxSize()
    ) {
        val sections by viewModel.sections.collectAsState()

        val hasMore by viewModel.hasMore.collectAsState(false)

        LaunchedEffect( sections ) {
            val title = sections.firstOrNull()?.title
            if( title == null ) {
                delay( 500.milliseconds )
                viewModel.onRefresh()

                return@LaunchedEffect
            }

            // Set title to first section title
            viewModel.topLayoutConfiguration.title = title
        }

        LazyColumn(
            state = viewModel.topLayoutConfiguration.lazyListState,
            modifier = Modifier.fillMaxSize()
        ) {
            for( s in sections ) {
                // Not a real component, its purpose is to provide
                // section's title so that main title can be updated
                // accordingly
                if( s.title.isNullOrBlank() )
                    item { Spacer(Modifier.height( TITLE_HEIGHT.dp )) }
                else {
                    val title = s.title.orEmpty()
                    item( Title(title) ) { Header( title, s.browseId, s.params ) }
                }

                val songs = s.contents.fastMapNotNull { it as? InnertubeSong }
                if( songs.any() )
                    item {
                        SongPages( songs )
                    }

                val items = s.contents.filterNot { it is InnertubeSong }
                if( items.any() )
                    item {
                        ItemRow( items )
                    }
            }

            if( sections.isEmpty() )
                item {
                    SongPagesPlaceholder()

                    Spacer(Modifier.height( TITLE_HEIGHT.dp ))
                    PlaylistRowPlaceholder()

                    Spacer(Modifier.height( TITLE_HEIGHT.dp ))
                    PlaylistRowPlaceholder()
                }
            else if( hasMore )
                item( contentType = LoadMoreContentType ) {
                    Spacer(Modifier.height( TITLE_HEIGHT.dp ))
                    PlaylistRowPlaceholder()
                }
        }
    }

@Preview(
    uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL,
    showSystemUi = false
)
@Composable
private fun HeaderPreview() = AppTheme( dynamicColor = false ) {
    Header( "Quick picks", null, null )
}

@Preview(
    uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL,
    showSystemUi = false
)
@Composable
private fun SongPagesPreview() = AppTheme( dynamicColor = false ) {
    val items = remember {
        buildList {
            add(
                object : InnertubeSong {
                    override val id: String = "sFL8eH69w7A"
                    override val name: String = "Storyteller"
                    override val artistsText: String = "TRUE"
                    override val artists: List<Runs.Run> = emptyList()
                    override val durationText: String? = "4:35"
                    override val thumbnails: List<Thumbnails.Thumbnail> = listOf(
                        object : Thumbnails.Thumbnail {
                            override val url: String = "https://lh3.googleusercontent.com/ckFfjbiM1NUOnJWEvoVqkNhZP97bhpT30k2QpJey0jlGzVNQny3Rwkd_-oWUFrotR3o7UqP-k1jMveet=w60-h60-l90-rj"
                            override val width: Short = 100
                            override val height: Short = 100
                        }
                    )
                    override val album: Runs.Run? = null
                    override val isExplicit: Boolean = false

                    override fun shareUrl(host: String): String = ""
                }
            )

            add(
                object : InnertubeSong {
                    override val id: String = "jg51kFe7CWU"
                    override val name: String = "Somebody That I Used To Know (feat. Kimbra)"
                    override val artistsText: String = "Gotye"
                    override val artists: List<Runs.Run> = emptyList()
                    override val durationText: String? = "4:05"
                    override val thumbnails: List<Thumbnails.Thumbnail> = listOf(
                        object : Thumbnails.Thumbnail {
                            override val url: String = "https://lh3.googleusercontent.com/baVeQFQOrl0GBm29XgdkDhNZw_mUG4wcWZUDW1I1mUO0PaSH5331T5DYIF4itsEyYmGqirRhbVHUR25r=w60-h60-l90-rj"
                            override val width: Short = 100
                            override val height: Short = 100
                        }
                    )
                    override val album: Runs.Run? = null
                    override val isExplicit: Boolean = false

                    override fun shareUrl(host: String): String = ""
                }
            )

            add(
                object : InnertubeSong {
                    override val id: String = "u6w1mB2xIs4"
                    override val name: String = "유리구슬 (Glass Bead)"
                    override val artistsText: String = "GFRIEND"
                    override val artists: List<Runs.Run> = emptyList()
                    override val durationText: String? = "3:24"
                    override val thumbnails: List<Thumbnails.Thumbnail> = listOf(
                        object : Thumbnails.Thumbnail {
                            override val url: String = "https://lh3.googleusercontent.com/8xMZiDGWdj_uDCiTFyAkEBQ5Ooo0C5FSnEIwDStjh8kSYkgFXKD1GJf7bIJrR_ba7oCjdXYYmzOPmYs=w60-h60-l90-rj"
                            override val width: Short = 100
                            override val height: Short = 100
                        }
                    )
                    override val album: Runs.Run? = null
                    override val isExplicit: Boolean = false

                    override fun shareUrl(host: String): String = ""
                }
            )

            add(
                object : InnertubeSong {
                    override val id: String = "WPGLUSu1rxE"
                    override val name: String = "sister's noise"
                    override val artistsText: String = "fripSide"
                    override val artists: List<Runs.Run> = emptyList()
                    override val durationText: String? = "4:21"
                    override val thumbnails: List<Thumbnails.Thumbnail> = listOf(
                        object : Thumbnails.Thumbnail {
                            override val url: String = "https://lh3.googleusercontent.com/w3QfQEccUcLJ2Hrxtgu-OV9nRPEM7iwV3T7JVWh7-th7RtIqEuJv3RSDqZvt3u0gNGhsMUfzqZhRwhIp0A=w544-h544-s-l90-rj"
                            override val width: Short = 100
                            override val height: Short = 100
                        }
                    )
                    override val album: Runs.Run? = null
                    override val isExplicit: Boolean = false

                    override fun shareUrl(host: String): String = ""
                }
            )

            add(
                object : InnertubeSong {
                    override val id: String = "pQBntSH-qFA"
                    override val name: String = "The Feels"
                    override val artistsText: String = "TWICE"
                    override val artists: List<Runs.Run> = emptyList()
                    override val durationText: String? = "3:19"
                    override val thumbnails: List<Thumbnails.Thumbnail> = listOf(
                        object : Thumbnails.Thumbnail {
                            override val url: String = "https://lh3.googleusercontent.com/lUrFhM76brGznc5g02_XrTtljNci4zJngcg6_MAmtUvMX-AnFAhI9ZZxlLp_kbFqFlxE88_U-z9diO1YgQ=w60-h60-l90-rj"
                            override val width: Short = 100
                            override val height: Short = 100
                        }
                    )
                    override val album: Runs.Run? = null
                    override val isExplicit: Boolean = false

                    override fun shareUrl(host: String): String = ""
                }
            )

            add(
                object : InnertubeSong {
                    override val id: String = "A4yti_57kgE"
                    override val name: String = "NO LIE"
                    override val artistsText: String = "EVERGLOW (에버글로우)"
                    override val artists: List<Runs.Run> = emptyList()
                    override val durationText: String? = "3:39"
                    override val thumbnails: List<Thumbnails.Thumbnail> = listOf(
                        object : Thumbnails.Thumbnail {
                            override val url: String = "https://lh3.googleusercontent.com/VDTaB1cUS5AlooaG7UF2jmneDeceGIQgmyHIbJXoVXDzjI6A6CdOG5GIXx1WVeTBaoxrIlniwvIBPo4=w60-h60-l90-rj"
                            override val width: Short = 100
                            override val height: Short = 100
                        }
                    )
                    override val album: Runs.Run? = null
                    override val isExplicit: Boolean = false

                    override fun shareUrl(host: String): String = ""
                }
            )

            add(
                object : InnertubeSong {
                    override val id: String = "R3uXuwo5rOc"
                    override val name: String = "Niji No Kanatani (From \"THE FIRST TAKE\")"
                    override val artistsText: String = "ReoNa"
                    override val artists: List<Runs.Run> = emptyList()
                    override val durationText: String? = "6:05"
                    override val thumbnails: List<Thumbnails.Thumbnail> = listOf(
                        object : Thumbnails.Thumbnail {
                            override val url: String = "https://lh3.googleusercontent.com/WWYBrkiphuJItehVNAkXw8dO246APSqtpqeaKHynFVKzTiGSwKZoIKK5j59Rr3caybRtRV6tiwQr3tsP=w544-h544-l90-rj"
                            override val width: Short = 100
                            override val height: Short = 100
                        }
                    )
                    override val album: Runs.Run? = null
                    override val isExplicit: Boolean = false

                    override fun shareUrl(host: String): String = ""
                }
            )

            add(
                object : InnertubeSong {
                    override val id: String = "9Kq4gt91sgU"
                    override val name: String = "White Silence (At Billboard Live Osaka 2015.7.20)"
                    override val artistsText: String = "TK from Ling tosite sigure"
                    override val artists: List<Runs.Run> = emptyList()
                    override val durationText: String? = "8:24"
                    override val thumbnails: List<Thumbnails.Thumbnail> = listOf(
                        object : Thumbnails.Thumbnail {
                            override val url: String = "https://lh3.googleusercontent.com/KOKGjZzdUwohXTSWgyWBsEW6DshMwILAhse7HZIYqD5HFCf46lD1fefj6ZQUGJ6pNBP22KzsKzs6Jbo=w544-h544-l90-rj"
                            override val width: Short = 100
                            override val height: Short = 100
                        }
                    )
                    override val album: Runs.Run? = null
                    override val isExplicit: Boolean = false

                    override fun shareUrl(host: String): String = ""
                }
            )

        }
    }

    SongPages( items )
}
