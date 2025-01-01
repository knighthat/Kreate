package it.fast4x.rimusic.ui.screens.album

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastDistinctBy
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import it.fast4x.compose.persist.PersistMapCleanup
import it.fast4x.compose.persist.persist
import it.fast4x.compose.persist.persistList
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.models.bodies.BrowseBody
import it.fast4x.innertube.requests.albumPage
import it.fast4x.rimusic.*
import it.fast4x.rimusic.enums.*
import it.fast4x.rimusic.models.Album
import it.fast4x.rimusic.models.SongAlbumMap
import it.fast4x.rimusic.ui.components.navigation.header.AppHeader
import it.fast4x.rimusic.utils.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.Contract


@ExperimentalMaterialApi
@ExperimentalTextApi
@ExperimentalFoundationApi
@ExperimentalAnimationApi
@ExperimentalComposeUiApi
@UnstableApi
@Composable
fun AlbumScreen(
    navController: NavController,
    browseId: String,
    modifier: Modifier = Modifier,
    miniPlayer: @Composable () -> Unit = {}
) {
    PersistMapCleanup( tagPrefix = "album/$browseId/" )
    PersistMapCleanup( tagPrefix = "album/$browseId/alternatives" )

    // Essentials
    val saveableStateHolder = rememberSaveableStateHolder()

    // Settings
    val transitionEffect by rememberPreference(transitionEffectKey, TransitionEffect.Scale)
    val playerPosition by rememberPreference(playerPositionKey, PlayerPosition.Bottom)

    var album by persist<Album?>("album/$browseId")
    LaunchedEffect(Unit) {
        Database.album( browseId )
            .flowOn( Dispatchers.IO )
            .distinctUntilChanged()
            .collect { album = it }
    }

    var alternatives by persistList<Innertube.AlbumItem>( "album/$browseId/alternatives" )
    var description by rememberSaveable { mutableStateOf("") }
    LaunchedEffect( Unit ) {
        withContext( Dispatchers.IO ) {

            /**
             * Get fetched version of this data unless the
             * current data is modified by the user (annotated by
             * [MODIFIED_PREFIX] prefix.
             */
            @Contract("!null,!null->!null")
            fun getUpdated( current: String?, new: String? ): String? =
                if( current?.startsWith( MODIFIED_PREFIX, true ) == true )
                    current
                else
                    new

            val browseBody = BrowseBody(browseId = browseId)
            Innertube.albumPage( browseBody )
                ?.onSuccess { online ->
                    val authorsText = online.authors?.joinToString("") { it.name ?: "" }

                    Database.asyncTransaction {
                        upsert(Album(
                            id = browseId,
                            title = getUpdated( album?.title, online.title ),
                            thumbnailUrl = getUpdated( album?.thumbnailUrl, online.thumbnail?.url ),
                            year = online.year,
                            authorsText = getUpdated( album?.authorsText, authorsText ),
                            shareUrl = online.url,
                            timestamp = album?.timestamp ?: System.currentTimeMillis(),
                            bookmarkedAt = album?.bookmarkedAt
                        ))

                        online.songsPage
                            ?.items
                            ?.fastDistinctBy { it.key }       // Experimental, revert to `distinctBy` if needed
                            ?.map( Innertube.SongItem::asMediaItem )
                            ?.onEach( Database::insert )
                            ?.mapIndexed { position, mediaItem ->
                                SongAlbumMap(
                                    songId = mediaItem.mediaId,
                                    albumId = browseId,
                                    position = position
                                )
                            }
                            ?.forEach( this::upsert )
                    }

                    alternatives = online.otherVersions ?: emptyList()
                    description = online.description ?: ""
                }
        }
    }

    val thumbnailPainter = rememberAsyncImagePainter(
        model = ImageRequest.Builder( appContext() )
            .data( album?.thumbnailUrl?.resize(1200, 1200) )
            .diskCacheKey( browseId )
            .build(),
    )

    androidx.compose.material3.Scaffold(
        modifier = Modifier,
        containerColor = colorPalette().background0,
        topBar = {
            if( UiType.RiMusic.isCurrent() )
                AppHeader( navController ).Draw()
        }
    ) {
        Box(
            modifier = Modifier.padding(it)
                .fillMaxSize()
        ) {

            Row(
                modifier = Modifier.fillMaxSize()
                    .background( colorPalette().background0 )
            ) {
                val topPadding = if ( UiType.ViMusic.isCurrent() ) 30.dp else 0.dp

                AnimatedContent(
                    targetState = 0,
                    transitionSpec = {
                        when (transitionEffect) {
                            TransitionEffect.None -> EnterTransition.None togetherWith ExitTransition.None
                            TransitionEffect.Expand -> expandIn(
                                animationSpec = tween(
                                    350,
                                    easing = LinearOutSlowInEasing
                                ), expandFrom = Alignment.BottomStart
                            ).togetherWith(
                                shrinkOut(
                                    animationSpec = tween(
                                        350,
                                        easing = FastOutSlowInEasing
                                    ), shrinkTowards = Alignment.CenterStart
                                )
                            )

                            TransitionEffect.Fade -> fadeIn(animationSpec = tween(350)).togetherWith(
                                fadeOut(animationSpec = tween(350))
                            )

                            TransitionEffect.Scale -> scaleIn(animationSpec = tween(350)).togetherWith(
                                scaleOut(animationSpec = tween(350))
                            )

                            TransitionEffect.SlideHorizontal, TransitionEffect.SlideVertical -> {
                                val slideDirection = when (targetState > initialState) {
                                    true -> {
                                        if (transitionEffect == TransitionEffect.SlideHorizontal)
                                            AnimatedContentTransitionScope.SlideDirection.Left
                                        else AnimatedContentTransitionScope.SlideDirection.Up
                                    }

                                    false -> {
                                        if (transitionEffect == TransitionEffect.SlideHorizontal)
                                            AnimatedContentTransitionScope.SlideDirection.Right
                                        else AnimatedContentTransitionScope.SlideDirection.Down
                                    }
                                }

                                val animationSpec = spring(
                                    dampingRatio = 0.9f,
                                    stiffness = Spring.StiffnessLow,
                                    visibilityThreshold = IntOffset.VisibilityThreshold
                                )

                                slideIntoContainer(
                                    slideDirection,
                                    animationSpec
                                ) togetherWith
                                        slideOutOfContainer(slideDirection, animationSpec)
                            }
                        }
                    },
                    label = "",
                    modifier = Modifier.fillMaxHeight()
                        .padding( top = topPadding )
                ) { currentTabIndex ->
                    saveableStateHolder.SaveableStateProvider(key = currentTabIndex) {
                        when (currentTabIndex) {
                            0 -> AlbumDetails(
                                navController = navController,
                                browseId = browseId,
                                album = album,
                                thumbnailPainter = thumbnailPainter,
                                alternatives = alternatives,
                                description = description,
                                onSearchClick = {
                                    navController.navigate(NavRoutes.search.name)
                                },
                                onSettingsClick = {
                                    navController.navigate(NavRoutes.settings.name)
                                }
                            )
                        }
                    }
                }
            }

            //**
            Box(
                modifier = modifier.padding( vertical = 5.dp ).align(
                    if( playerPosition == PlayerPosition.Top ) Alignment.TopCenter else Alignment.BottomCenter
                )
            ) {
                miniPlayer.invoke()
            }
        }
    }
}
