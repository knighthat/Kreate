package app.kreate.android.themed.rimusic.screen.player

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import app.kreate.android.Preferences
import app.kreate.android.R
import app.kreate.android.coil3.ImageFactory
import app.kreate.android.utils.scrollingText
import app.kreate.android.viewmodel.player.ActionBarNextSongsViewModel
import app.kreate.util.cleanPrefix
import it.fast4x.rimusic.enums.ColorPaletteMode
import it.fast4x.rimusic.enums.SongsNumber
import it.fast4x.rimusic.ui.styling.LocalAppearance
import it.fast4x.rimusic.utils.addNext
import it.fast4x.rimusic.utils.playAtIndex
import it.fast4x.rimusic.utils.semiBold
import kotlinx.coroutines.CoroutineScope
import me.knighthat.utils.Toaster
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf


@OptIn(UnstableApi::class)
@Composable
fun NextSongsInQueue(
    modifier: Modifier = Modifier
) {
    //<editor-fold desc="View model">
    val coroutine: CoroutineScope = rememberCoroutineScope()
    val viewModel: ActionBarNextSongsViewModel = koinViewModel { parametersOf(coroutine) }
    //</editor-fold>
    val (colorPalette, typography) = LocalAppearance.current
    val transparentBackground by Preferences.PLAYER_TRANSPARENT_ACTIONS_BAR

    LaunchedEffect( viewModel.queue ) {
        viewModel.scrollToNext()
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly,
        modifier = modifier.background(
                               colorPalette.background2.copy(
                                   alpha = if (transparentBackground) 0.0f else 0.3f
                               )
                           )
                           .padding(horizontal = 12.dp)
                           .fillMaxWidth()
    ) {
        //<editor-fold desc="Relative position indicator">
        val icon = if( viewModel.pager.currentPage > viewModel.currentIndex )
            Icons.AutoMirrored.Default.KeyboardArrowLeft
        else if( viewModel.pager.currentPage == viewModel.currentIndex )
            Icons.Default.PlayArrow
        else
            Icons.AutoMirrored.Default.KeyboardArrowRight

        IconButton(
            onClick = {
                viewModel.scrollTo( viewModel.currentIndex )
            },
            modifier = Modifier.size( ACTION_BUTTON_SIZE.dp )
        ) {
            Icon(
                imageVector = icon,
                tint = colorPalette.accent,
                // TODO: localize
                contentDescription = "Go to currently playing song"
            )
        }
        //</editor-fold>

        HorizontalPager(
            state = viewModel.pager,
            pageSize = viewModel.viewPort,
            pageSpacing = 10.dp,
            modifier = Modifier.weight(1f)
        ) { index ->
            val mediaItem = viewModel.queue[index]

            Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.combinedClickable(
                    onClick = {
                        viewModel.player.playAtIndex(index)
                    },
                    onLongClick = {
                        if ( index < viewModel.queue.size ) {
                            viewModel.player.addNext( mediaItem )
                            Toaster.s( R.string.addednext )
                        }
                    }
                )
            ) {
                val showAlbumCover by Preferences.PLAYER_SHOW_NEXT_IN_QUEUE_THUMBNAIL
                if ( showAlbumCover )
                    Box( Modifier.align(Alignment.CenterVertically) ) {
                        ImageFactory.AsyncImage(
                            thumbnailUrl = mediaItem.mediaMetadata
                                                    .artworkUri
                                                    .toString(),
                            contentDescription = "song_pos_$index",
                            modifier = Modifier.padding( end = 5.dp )
                                               .clip( RoundedCornerShape(5.dp) )
                                               .size( 30.dp )
                        )
                    }

                Column(
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.height( 40.dp )
                                       .fillMaxWidth()
                ) {
                    val colorPaletteMode by Preferences.THEME_MODE
                    val textOutline by Preferences.TEXT_OUTLINE

                    //<editor-fold defaultstate="collapsed" desc="Title">
                    Box {
                        val titleText by remember {
                            derivedStateOf {
                                cleanPrefix( mediaItem.mediaMetadata.title.toString() )
                            }
                        }

                        BasicText(
                            text = titleText,
                            style = TextStyle(
                                color = colorPalette.text,
                                fontSize = typography.xxxs.semiBold.fontSize,
                            ),
                            maxLines = 1,
                            modifier = Modifier.scrollingText()
                        )
                        BasicText(
                            text = titleText,
                            style = TextStyle(
                                drawStyle = Stroke(
                                    width = 0.25f,
                                    join = StrokeJoin.Round
                                ),
                                color = if (!textOutline) Color.Transparent
                                else if (colorPaletteMode == ColorPaletteMode.Light || (colorPaletteMode == ColorPaletteMode.System && (!isSystemInDarkTheme()))) Color.White.copy(
                                    0.65f
                                )
                                else Color.Black,
                                fontSize = typography.xxxs.semiBold.fontSize,
                            ),
                            maxLines = 1,
                            modifier = Modifier.scrollingText()
                        )
                    }
                    //</editor-fold>
                    //<editor-fold defaultstate="collapsed" desc="Artists">
                    Box {
                        val artistsText by remember {
                            derivedStateOf {
                                cleanPrefix( mediaItem.mediaMetadata.artist.toString() )
                            }
                        }

                        BasicText(
                            text = artistsText,
                            style = TextStyle(
                                color = colorPalette.text,
                                fontSize = typography.xxxs.semiBold.fontSize,
                            ),
                            maxLines = 1,
                            modifier = Modifier.scrollingText()
                        )
                        BasicText(
                            text = artistsText,
                            style = TextStyle(
                                drawStyle = Stroke(
                                    width = 0.25f,
                                    join = StrokeJoin.Round
                                ),
                                color =
                                    if ( !textOutline )
                                        Color.Transparent
                                    else if (
                                        colorPaletteMode == ColorPaletteMode.Light
                                        || (colorPaletteMode == ColorPaletteMode.System && !isSystemInDarkTheme())
                                    )
                                        Color.White.copy( 0.65f )
                                    else
                                        Color.Black,
                                fontSize = typography.xxxs.semiBold.fontSize,
                            ),
                            maxLines = 1,
                            modifier = Modifier.scrollingText()
                        )
                    }
                    //</editor-fold>
                }
            }
        }

        if ( viewModel.numSongsToShow == SongsNumber.`1` )
            IconButton(
                onClick = {
                    val nextIndex = viewModel.currentIndex + 1
                    viewModel.player.removeMediaItem( nextIndex )
                },
                modifier = Modifier.weight( .07f )
                                   .size( ACTION_BUTTON_SIZE.dp )
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    tint = Color.White,
                    // TODO: localize
                    contentDescription = "Delete from queue"
                )
            }
    }
}