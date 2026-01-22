package it.fast4x.rimusic.ui.screens.newreleases

import android.annotation.SuppressLint
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import app.kreate.android.Preferences
import app.kreate.android.R
import app.kreate.android.themed.rimusic.component.album.AlbumItem
import it.fast4x.compose.persist.persist
import it.fast4x.compose.persist.persistList
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.requests.discoverPageNewAlbums
import it.fast4x.rimusic.Database
import it.fast4x.rimusic.colorPalette
import it.fast4x.rimusic.enums.NavigationBarPosition
import it.fast4x.rimusic.typography
import it.fast4x.rimusic.ui.components.themed.HeaderWithIcon
import it.fast4x.rimusic.ui.styling.Dimensions
import it.fast4x.rimusic.ui.styling.LocalAppearance
import it.fast4x.rimusic.utils.center
import it.fast4x.rimusic.utils.secondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged

@ExperimentalTextApi
@UnstableApi
@SuppressLint("SuspiciousIndentation")
@ExperimentalFoundationApi
@ExperimentalAnimationApi
@Composable
fun NewAlbumsFromArtists(
    navController: NavController
) {
    var discoverPage by persist<Result<Innertube.DiscoverPageAlbums>>("home/discoveryAlbums")
    LaunchedEffect(Unit) {
        discoverPage = Innertube.discoverPageNewAlbums()
    }

    val navigationBarPosition by Preferences.NAVIGATION_BAR_POSITION
    val showSearchTab by Preferences.SHOW_SEARCH_IN_NAVIGATION_BAR

    val lazyGridState = rememberLazyGridState()

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
            val artists by remember {
                Database.artistTable
                        .sortFollowingByName()
                        .distinctUntilChanged()
            }.collectAsState( emptyList(), Dispatchers.IO )

            var newReleaseAlbumsFiltered by persistList<Innertube.AlbumItem>("discovery/newalbumsartist")
            page.newReleaseAlbums.forEach { album ->
                artists.forEach { artist ->
                    if (artist.name == album.authors?.first()?.name) {
                        newReleaseAlbumsFiltered += album
                    }
                }
            }

            val appearance = LocalAppearance.current
            val albumItemValues = remember( appearance ) {
                AlbumItem.Values.from( appearance )
            }

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
                        title = stringResource(R.string.new_albums_of_your_artists),
                        iconId = R.drawable.search,
                        enabled = true,
                        showIcon = !showSearchTab,
                        modifier = Modifier,
                        onClick = {}
                    )

                }

                if (newReleaseAlbumsFiltered.isNotEmpty()) {
                    items(
                        items = newReleaseAlbumsFiltered.distinct(),
                        key = System::identityHashCode
                    ) { album ->
                        AlbumItem.Vertical( album, albumItemValues, navController )
                    }
                } else {
                    item(
                        key = "noAlbums",
                        contentType = 0,
                    ) {
                        BasicText(
                            text = "There are no new releases for your favorite artists",
                            style = typography().s.secondary.center,
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .padding(all = 16.dp)
                        )
                    }
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
