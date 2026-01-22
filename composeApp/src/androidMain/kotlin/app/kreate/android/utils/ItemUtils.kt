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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import app.kreate.android.themed.rimusic.component.album.AlbumItem
import app.kreate.android.themed.rimusic.component.artist.ArtistItem
import app.kreate.android.themed.rimusic.component.playlist.PlaylistItem
import app.kreate.android.themed.rimusic.component.song.SongItem
import app.kreate.android.utils.innertube.toMediaItem
import it.fast4x.innertube.Innertube
import it.fast4x.rimusic.LocalPlayerServiceBinder
import it.fast4x.rimusic.isVideoEnabled
import it.fast4x.rimusic.thumbnailShape
import it.fast4x.rimusic.ui.styling.LocalAppearance
import it.fast4x.rimusic.utils.asMediaItem
import it.fast4x.rimusic.utils.forcePlay
import it.fast4x.rimusic.utils.playVideo
import it.fast4x.rimusic.utils.shimmerEffect
import me.knighthat.innertube.model.InnertubeAlbum
import me.knighthat.innertube.model.InnertubeArtist
import me.knighthat.innertube.model.InnertubeItem
import me.knighthat.innertube.model.InnertubePlaylist
import me.knighthat.innertube.model.InnertubeSong


object ItemUtils {

    const val COLUMN_SPACING = 10

    @Composable
    fun ThumbnailPlaceholder(
        sizeDp: DpSize,
        modifier: Modifier = Modifier
    ) =
        Box( modifier.size( sizeDp ).clip(thumbnailShape() ).shimmerEffect() )

    @JvmName("OldInnertubeLazyRowItem")
    @UnstableApi
    @Composable
    fun LazyRowItem(
        navController: NavController,
        innertubeItems: List<Innertube.Item>,
        currentlyPlaying: String?,
        modifier: Modifier = Modifier
    ) {
        val context = LocalContext.current
        val binder = LocalPlayerServiceBinder.current ?: return
        val hapticFeedback = LocalHapticFeedback.current
        val appearance = LocalAppearance.current
        val songItemValues = remember( appearance ) {
            SongItem.Values.from( appearance )
        }
        val albumItemValues = remember( appearance ) {
            AlbumItem.Values.from( appearance )
        }
        val artistItemValues = remember( appearance ) {
            ArtistItem.Values.from( appearance )
        }
        val playlistItemValues = remember( appearance ) {
            PlaylistItem.Values.from( appearance )
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(COLUMN_SPACING.dp ),
            modifier = modifier
        ) {
            items(
                items = innertubeItems,
                key = Innertube.Item::key
            ) { childItem ->
                when ( childItem ) {
                    is Innertube.SongItem -> SongItem.Render(
                        innertubeSong = childItem,
                        context = context,
                        binder = binder,
                        hapticFeedback = hapticFeedback,
                        values = songItemValues,
                        isPlaying = childItem.key == currentlyPlaying,
                        navController = navController,
                        onClick = {
                            binder.player.forcePlay( childItem.asMediaItem )
                        }
                    )

                    is Innertube.VideoItem -> {
                        println("Innertube homePage VideoItem: ${childItem.info?.name}")
                        SongItem.Render(
                            innertubeVideo = childItem,
                            hapticFeedback = hapticFeedback,
                            isPlaying = currentlyPlaying == childItem.key,
                            values = songItemValues,
                            thumbnailSizeDp = SongItem.thumbnailSize(),
                            onClick = {
                                binder.stopRadio()
                                if ( isVideoEnabled() )
                                    binder.player.playVideo( childItem.asMediaItem )
                                else
                                    binder.player.forcePlay( childItem.asMediaItem )
                            }
                        )
                    }

                    is Innertube.AlbumItem -> AlbumItem.Vertical(
                        innertubeAlbum = childItem,
                        values = albumItemValues,
                        navController = navController
                    )

                    is Innertube.ArtistItem -> ArtistItem.Render(
                        innertubeArtist = childItem,
                        values = artistItemValues,
                        navController = navController
                    )

                    is Innertube.PlaylistItem -> PlaylistItem.Vertical(
                        innertubePlaylist = childItem,
                        values = playlistItemValues,
                        navController = navController
                    )
                }
            }
        }
    }

    @UnstableApi
    @Composable
    fun LazyRowItem(
        navController: NavController,
        innertubeItems: List<InnertubeItem>,
        currentlyPlaying: String?,
        modifier: Modifier = Modifier
    ) {
        val context = LocalContext.current
        val binder = LocalPlayerServiceBinder.current ?: return
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
                        context = context,
                        binder = binder,
                        hapticFeedback = hapticFeedback,
                        values = songIV,
                        isPlaying = item.id == currentlyPlaying,
                        navController = navController,
                        onClick = {
                            binder.player.forcePlay( item.toMediaItem )
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