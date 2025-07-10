package it.fast4x.rimusic.ui.screens.newreleases

import android.annotation.SuppressLint
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import app.kreate.android.Preferences
import app.kreate.android.R
import it.fast4x.compose.persist.persist
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.requests.discoverPage
import it.fast4x.rimusic.colorPalette
import it.fast4x.rimusic.enums.NavRoutes
import it.fast4x.rimusic.enums.NavigationBarPosition
import it.fast4x.rimusic.ui.components.themed.HeaderWithIcon
import it.fast4x.rimusic.ui.items.AlbumItem
import it.fast4x.rimusic.ui.styling.Dimensions
import it.fast4x.rimusic.ui.styling.px

@ExperimentalTextApi
@UnstableApi
@SuppressLint("SuspiciousIndentation")
@ExperimentalFoundationApi
@ExperimentalAnimationApi
@Composable
fun NewAlbums(
    navController: NavController
) {
    var discoverPage by persist<Result<Innertube.DiscoverPage>>("home/discoveryAlbums")
    LaunchedEffect(Unit) {
        discoverPage = Innertube.discoverPage()
    }

    val thumbnailSizeDp = Dimensions.thumbnails.album + 24.dp
    val thumbnailSizePx = thumbnailSizeDp.px

    val navigationBarPosition by Preferences.NAVIGATION_BAR_POSITION

    val showSearchTab by Preferences.SHOW_SEARCH_IN_NAVIGATION_BAR

    val lazyGridState = rememberLazyGridState()

    val disableScrollingText by Preferences.SCROLLING_TEXT_DISABLED


    Column(
        modifier = Modifier
            .background(colorPalette().background0)
            //.fillMaxSize()
            .fillMaxHeight()
            .fillMaxWidth(
                if (navigationBarPosition == NavigationBarPosition.Left ||
                    navigationBarPosition == NavigationBarPosition.Top ||
                    navigationBarPosition == NavigationBarPosition.Bottom
                ) 1f
                else Dimensions.contentWidthRightBar
            )
    ) {

        /***************/
        discoverPage?.getOrNull()?.let { page ->
            LazyVerticalGrid(
                state = lazyGridState,
                columns = GridCells.Adaptive(Dimensions.thumbnails.album + 24.dp),
                //contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
                modifier = Modifier
                    .background(colorPalette().background0)
                //.fillMaxSize()
            ) {
                item(
                    key = "header",
                    contentType = 0,
                    span = { GridItemSpan(maxLineSpan) }
                ) {
                    HeaderWithIcon(
                        title = stringResource(R.string.new_albums),
                        iconId = R.drawable.search,
                        enabled = true,
                        showIcon = !showSearchTab,
                        modifier = Modifier,
                        onClick = {}
                    )

                }

                items(
                    items = page.newReleaseAlbums.distinct(),
                    key = { it.key }) {
                    AlbumItem(
                        album = it,
                        thumbnailSizePx = thumbnailSizePx,
                        thumbnailSizeDp = thumbnailSizeDp,
                        alternative = true,
                        modifier = Modifier.clickable(onClick = {
                            NavRoutes.YT_ALBUM.navigateHere( navController, it.key )
                        }),
                        disableScrollingText = disableScrollingText
                    )
                }
                item(
                    key = "footer",
                    contentType = 0,
                ) {
                    Spacer(modifier = Modifier.height(Dimensions.bottomSpacer))
                }
            }

        }
        /***************/


    }

}
