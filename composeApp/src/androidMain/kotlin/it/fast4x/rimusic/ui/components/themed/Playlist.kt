package it.fast4x.rimusic.ui.components.themed

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.kreate.android.R
import coil.compose.AsyncImage
import it.fast4x.rimusic.Database
import it.fast4x.rimusic.cleanPrefix
import it.fast4x.rimusic.colorPalette
import it.fast4x.rimusic.models.Playlist
import it.fast4x.rimusic.ui.items.PlaylistItem
import it.fast4x.rimusic.utils.thumbnail
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

@Composable
fun Playlist(
    playlist: Playlist,
    songCount: Int,
    thumbnailSizePx: Int,
    thumbnailSizeDp: Dp,
    modifier: Modifier = Modifier,
    alternative: Boolean = false,
    showName: Boolean = true,
    disableScrollingText: Boolean,
    thumbnailUrl: String? = null,
) {
    val thumbnails by remember {
        Database.songPlaylistMapTable
                .sortSongsByPlayTime( playlist.id )
                .distinctUntilChanged()
                .map { list ->
                    list.takeLast( 4 ).map {
                        it.thumbnailUrl.thumbnail( thumbnailSizePx / 2 )
                    }
                }
    }.collectAsState( emptyList(), Dispatchers.IO )

    PlaylistItem(
        thumbnailContent = {
            if (thumbnailUrl != null) {
                AsyncImage(
                    model = thumbnailUrl.thumbnail(thumbnailSizePx),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else if (thumbnails.toSet().size == 1) {
                AsyncImage(
                    model = thumbnails.first().thumbnail(thumbnailSizePx),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    //modifier = it KOTLIN 2
                )
            } else {
                Box(
                    modifier = Modifier // KOTLIN 2
                        .fillMaxSize()
                ) {
                    listOf(
                        Alignment.TopStart,
                        Alignment.TopEnd,
                        Alignment.BottomStart,
                        Alignment.BottomEnd
                    ).forEachIndexed { index, alignment ->
                        AsyncImage(
                            model = thumbnails.getOrNull(index),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .align(alignment)
                                .size(thumbnailSizeDp / 2)
                        )
                    }
                }
            }
            if ( playlist.browseId?.isNotEmpty() == true ) {
                Image(
                    painter = painterResource(R.drawable.ytmusic),
                    colorFilter = ColorFilter.tint(if (playlist.isYoutubePlaylist) Color.Red.copy(0.75f).compositeOver(
                        Color.White) else colorPalette().text),
                    modifier = Modifier
                        .size(40.dp)
                        .padding(all = 5.dp),
                    contentDescription = "Background Image",
                    contentScale = ContentScale.Fit
                )
            }
            if (playlist.isYoutubePlaylist && !playlist.isEditable){
                Image(
                    painter = painterResource(R.drawable.locked),
                    colorFilter = ColorFilter.tint(Color.Red),
                    modifier = Modifier
                        .padding(all = 5.dp)
                        .background(colorPalette().text, CircleShape)
                        .padding(all = 5.dp)
                        .size(18.dp)
                        .align(Alignment.BottomStart),
                    contentDescription = "Background Image",
                    contentScale = ContentScale.Fit
                )
            }
        },
        songCount = songCount,
        name = cleanPrefix(playlist.name),
        channelName = null,
        thumbnailSizeDp = thumbnailSizeDp,
        modifier = modifier,
        alternative = alternative,
        showName = showName,
        disableScrollingText = disableScrollingText
    )
}