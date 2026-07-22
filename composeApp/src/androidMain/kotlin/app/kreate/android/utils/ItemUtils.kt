package app.kreate.android.utils

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import app.kreate.android.LocalBottomMenu
import app.kreate.android.constant.MenuPage
import app.kreate.android.themed.common.component.BottomMenu
import app.kreate.android.themed.rimusic.component.album.AlbumItem
import app.kreate.android.themed.rimusic.component.artist.ArtistItem
import app.kreate.android.themed.rimusic.component.playlist.PlaylistItem
import app.kreate.android.themed.rimusic.component.song.SongItem
import app.kreate.android.utils.innertube.toMediaItem
import app.kreate.gateway.innertube.models.InnertubeAlbum
import app.kreate.gateway.innertube.models.InnertubeArtist
import app.kreate.gateway.innertube.models.InnertubeItem
import app.kreate.gateway.innertube.models.InnertubePlaylist
import app.kreate.gateway.innertube.models.InnertubeSong
import app.kreate.player.Player
import it.fast4x.rimusic.thumbnailShape
import it.fast4x.rimusic.ui.styling.LocalAppearance
import it.fast4x.rimusic.utils.shimmerEffect
import org.koin.compose.koinInject


object ItemUtils {

    const val COLUMN_SPACING = 10

    @Composable
    fun ThumbnailPlaceholder(
        sizeDp: DpSize,
        modifier: Modifier = Modifier
    ) =
        Box( modifier.size( sizeDp ).clip(thumbnailShape() ).shimmerEffect() )

    @UnstableApi
    @Composable
    fun LazyRowItem(
        navController: NavController,
        innertubeItems: List<InnertubeItem>,
        currentlyPlaying: String?,
        modifier: Modifier = Modifier,
        player: Player = koinInject(),
        menu: BottomMenu = LocalBottomMenu.current
    ) {
        val hapticFeedback = LocalHapticFeedback.current
        val appearance = LocalAppearance.current
        val (songIV, albumIV, artistIV, playlistIV) = remember( appearance ) {
            Quadruple(
                SongItem.Values.from( appearance ),
                AlbumItem.Values.from( appearance ),
                ArtistItem.Values.from( appearance ),
                PlaylistItem.Values.from( appearance )
            )
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(COLUMN_SPACING.dp ),
            modifier = modifier
        ) {
            items(
                items = innertubeItems,
                key = System::identityHashCode
            ) { item ->
                when( item ) {
                    is InnertubeSong -> SongItem.Render(
                        innertubeSong = item,
                        hapticFeedback = hapticFeedback,
                        values = songIV,
                        isPlaying = item.id == currentlyPlaying,
                        onClick = {
                            player.play( item.toMediaItem )
                        },
                        onLongClick = {
                            val page = MenuPage.Song(item.toMediaItem)
                            menu.show( page, true )
                        }
                    )

                    is InnertubeAlbum -> AlbumItem.Vertical(
                        innertubeAlbum = item,
                        values = albumIV,
                        navController = navController
                    )

                    is InnertubeArtist -> ArtistItem.Render(
                        innertubeArtist = item,
                        values = artistIV,
                        navController = navController
                    )

                    is InnertubePlaylist -> PlaylistItem.Vertical(
                        innertubePlaylist = item,
                        values = playlistIV,
                        navController = navController
                    )
                }
            }
        }
    }

    @Composable
    fun PlaceholderRowItem(
        modifier: Modifier = Modifier,
        itemContent: @Composable LazyItemScope.(index: Int) -> Unit
    ) =
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(COLUMN_SPACING.dp ),
            userScrollEnabled = false,
            modifier = modifier
        ) {
            items( 10, itemContent =  itemContent )
        }

    private data class Quadruple<T1, T2, T3, T4>(val t1: T1, val t2: T2, val t3: T3, val t4: T4)
}