package it.fast4x.rimusic.ui.screens.home

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import app.kreate.android.Preferences
import app.kreate.android.R
import app.kreate.android.themed.rimusic.screen.home.HomeSongsScreen
import it.fast4x.compose.persist.PersistMapCleanup
import it.fast4x.rimusic.enums.NavRoutes
import it.fast4x.rimusic.models.toUiMood
import it.fast4x.rimusic.ui.components.Skeleton


@ExperimentalMaterial3Api
@ExperimentalTextApi
@ExperimentalFoundationApi
@ExperimentalAnimationApi
@ExperimentalComposeUiApi
@UnstableApi
@Composable
fun HomeScreen(
    navController: NavController,
    onPlaylistUrl: (String) -> Unit,
    miniPlayer: @Composable () -> Unit = {}
) {
    val saveableStateHolder = rememberSaveableStateHolder()

    PersistMapCleanup("home/")

    val (tabIndex, onTabChanged) = Preferences.HOME_TAB_INDEX

    Skeleton(
        navController,
        tabIndex,
        onTabChanged,
        miniPlayer,
        navBarContent = { Item ->
            if ( Preferences.QUICK_PICKS_PAGE.value )
                Item(0, stringResource(R.string.quick_picks), R.drawable.sparkles)
            Item(1, stringResource(R.string.songs), R.drawable.musical_notes)
            Item(2, stringResource(R.string.artists), R.drawable.people)
            Item(3, stringResource(R.string.albums), R.drawable.album)
            Item(4, stringResource(R.string.playlists), R.drawable.library)
        }
    ) { currentTabIndex ->
        saveableStateHolder.SaveableStateProvider(key = currentTabIndex) {
            when (currentTabIndex) {
                0 -> HomeQuickPicks(
                    onAlbumClick = {
                        NavRoutes.YT_ALBUM.navigateHere( navController, it )
                    },
                    onArtistClick = {
                        NavRoutes.YT_ARTIST.navigateHere( navController, it )
                    },
                    onPlaylistClick = {
                        NavRoutes.YT_PLAYLIST.navigateHere( navController, it )
                    },
                    onSearchClick = {
                        NavRoutes.search.navigateHere( navController )
                    },
                    onMoodClick = { mood ->
                        navController.currentBackStackEntry?.savedStateHandle?.set("mood", mood.toUiMood())
                        NavRoutes.mood.navigateHere( navController )
                    },
                    onSettingsClick = {
                        NavRoutes.settings.navigateHere( navController )
                    },
                    navController = navController

                )

                1 -> HomeSongsScreen( navController )

                2 -> HomeArtists(
                    onArtistClick = {
                        NavRoutes.YT_ARTIST.navigateHere( navController, it.id )
                    },
                    onSearchClick = {
                        NavRoutes.search.navigateHere( navController )
                    },
                    onSettingsClick = {
                        NavRoutes.settings.navigateHere( navController )
                    }
                )

                3 -> HomeAlbums(
                    navController = navController,
                    onAlbumClick = {
                        NavRoutes.YT_ALBUM.navigateHere( navController, it.id )
                    },
                    onSearchClick = {
                        NavRoutes.search.navigateHere( navController )
                    },
                    onSettingsClick = {
                        NavRoutes.settings.navigateHere( navController )
                    }
                )

                4 -> HomeLibrary(
                    onPlaylistClick = {
                        NavRoutes.localPlaylist.navigateHere( navController, it.id )
                    },
                    onSearchClick = {
                        NavRoutes.search.navigateHere( navController )
                    },
                    onSettingsClick = {
                        NavRoutes.settings.navigateHere( navController )
                    }

                )
            }
        }
    }
}
