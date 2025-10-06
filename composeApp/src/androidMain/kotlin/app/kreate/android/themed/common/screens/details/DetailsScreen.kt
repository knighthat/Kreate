package app.kreate.android.themed.common.screens.details

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import app.kreate.android.R
import app.kreate.coil3.ImageFactory
import it.fast4x.rimusic.ui.components.Skeleton
import it.fast4x.rimusic.ui.styling.ColorPalette
import it.fast4x.rimusic.ui.styling.Dimensions
import it.fast4x.rimusic.ui.styling.LocalAppearance
import it.fast4x.rimusic.ui.styling.Typography
import me.knighthat.innertube.model.InnertubeSongDetails


@Composable
private fun BoxWithConstraintsScope.Thumbnail(
    thumbnailUrl: String,
    shape: Shape
) {
    ImageFactory.AsyncImage(
        thumbnailUrl = thumbnailUrl,
        modifier = Modifier.clip( shape )
                           .matchParentSize(),
        contentScale = ContentScale.FillWidth
    )

    // Gradient overlay (from 2/3 height to bottom)
    val heightPx = with( LocalDensity.current ) {
        maxHeight.toPx()
    }
    Box(
        Modifier.matchParentSize()
                .clip( shape )
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black),
                        startY = heightPx * 1 / 3,
                        endY = heightPx
                    )
                )
    )
}

@Composable
private fun BoxScope.Artist(
    details: InnertubeSongDetails,
    colorPalette: ColorPalette,
    typography: Typography,
    modifier: Modifier = Modifier
) =
    Column(
        verticalArrangement = Arrangement.spacedBy( 10.dp ),
        modifier = modifier.align( Alignment.BottomStart )
                           .padding( 16.dp )
    ) {
        BasicText(
            text = details.title,
            style = typography.xl.copy( colorPalette.text ),
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy( 5.dp)
        ) {
            ImageFactory.AsyncImage(
                thumbnailUrl = details.artist.thumbnails.first().url,
                modifier = Modifier.size( 40.dp )
                                   .clip(CircleShape )
            )

            Column {
                BasicText(
                    text = details.artist.name,
                    style = typography.s.copy( colorPalette.textSecondary )
                )
                BasicText(
                    text = details.artist.longNumSubscribers.orEmpty(),
                    style = typography.xs.copy( colorPalette.textDisabled )
                )
            }
        }
    }

@Composable
private fun LazyItemScope.Description(
    details: InnertubeSongDetails,
    colorPalette: ColorPalette,
    typography: Typography,
    shape: Shape,
    modifier: Modifier = Modifier
) =
    Column(
        verticalArrangement = Arrangement.spacedBy( 20.dp ),
        modifier = modifier.fillMaxWidth( .9f )
                           .animateItem()
                           .background( colorPalette.background1, shape )
                           .padding( 10.dp )
    ) {
        BasicText(
            text = stringResource( R.string.word_description ),
            style = typography.m.copy( colorPalette.text )
        )

        BasicText(
            text = details.description,
            style = typography.xs.copy( colorPalette.textSecondary )
        )
    }

@Composable
fun SongDetailsScreen( 
    navController: NavController,
    songId: String,
    viewModel: SongDetailsViewModel = viewModel(
        factory = SongDetailsViewModel.Factory(songId)
    )
) =
    Skeleton( 
        navController = navController,
        navBarContent =  { item ->
            item(0, "", 0)
        }
    ) {
        val (colorPalette, typography) = LocalAppearance.current
        val shape = RoundedCornerShape(15.dp)

        LazyColumn(
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues( bottom = Dimensions.bottomSpacer ),
            verticalArrangement = Arrangement.spacedBy( 5.dp ),
            modifier = Modifier.fillMaxSize()
        ) {
            val details = viewModel.songDetails ?: return@LazyColumn

            item {
                val shape = remember {
                    RoundedCornerShape(15.dp, 15.dp, 0.dp, 0.dp)
                }
                BoxWithConstraints(
                    Modifier.fillMaxWidth( .9f )
                            .aspectRatio( 16f / 9 )
                            .animateItem()
                            .background( colorPalette.background1, shape )
                ) {
                    viewModel.songBasicInfo?.thumbnails?.firstOrNull()?.url?.also {
                        Thumbnail( it, shape )
                    }

                    Artist( details, colorPalette, typography )
                }
            }

            item {
                Description( details, colorPalette, typography, shape )
            }
        }
    }