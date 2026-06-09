package app.kreate.android.themed.common.component

import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import app.kreate.android.R
import app.kreate.android.constant.MenuPage
import app.kreate.android.themed.common.component.menu.MenuButton
import app.kreate.android.themed.rimusic.component.playlist.PlaylistItem
import app.kreate.android.themed.rimusic.component.song.SongItem
import app.kreate.database.models.PlaylistPreview
import app.kreate.util.scrollingText
import it.fast4x.rimusic.Database
import it.fast4x.rimusic.enums.MenuStyle
import it.fast4x.rimusic.ui.components.navigation.header.TabToolBar
import it.fast4x.rimusic.ui.components.themed.IconButton
import it.fast4x.rimusic.ui.styling.LocalAppearance
import it.fast4x.rimusic.ui.styling.favoritesIcon
import it.fast4x.rimusic.utils.asMediaItem
import it.fast4x.rimusic.utils.asSong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import me.knighthat.component.dialog.Dialog
import me.knighthat.sync.YouTubeSync


@OptIn(ExperimentalMaterial3Api::class)
class BottomMenu {

    companion object {
        private const val BUTTON_ICON_SIZE = 24
    }

    // Used to keep track of paths
    private val backStack = mutableStateListOf<MenuPage>()

    var isVisible by mutableStateOf( false )
        private set

    @Suppress("UNCHECKED_CAST")
    private fun onButtonClick( button: MenuButton<*>, screen: MenuPage ) {
        if( screen is MenuPage.SongMenu )
            (button as MenuButton<MediaItem>).onClick( this, screen.song )
        else if( screen is MenuPage.LocalPlaylist )
            (button as MenuButton<PlaylistPreview>).onClick( this, screen.playlist )
    }

    /**
     * Opens bottom menu if not visible, or
     * switch to [page] if one is already visible
     *
     * @param page next destination
     * @param reset whether to clear backstack and make [page] the first destination
     */
    fun show( page: MenuPage, reset: Boolean = false ) {
        if( reset )
            backStack.clear()
        backStack.add( page )

        this.isVisible = true
    }

    /**
     * Make menu disappear
     */
    fun hide() {
        // Shouldn't clear the [backStack] here because it'll
        // trigger unnecessary draw commands
        this.isVisible = false
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    @OptIn(ExperimentalCoroutinesApi::class)
    @Composable
    fun BottomSheet(
        navController: NavController,
        modifier: Modifier = Modifier,
        context: Context = LocalContext.current
    ) {
        val (colorPalette, typography) = LocalAppearance.current
        val currentScreen = backStack.lastOrNull() ?: MenuPage.Empty

        ModalBottomSheet(
            onDismissRequest = ::hide,
            modifier = modifier,
            containerColor = colorPalette.background0,
            dragHandle = {
                Box(
                    Modifier.background( colorPalette.background1 )
                ) {
                    BottomSheetDefaults.DragHandle(
                        Modifier.align( Alignment.TopCenter )
                                // Move this bar above the placeholder below
                                .offset(y = (-12).dp)
                    )

                    if( currentScreen is MenuPage.SongMenu )
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = Color.Transparent,
                        ) {
                            val song = currentScreen.song.asSong
                            val songItemValues = remember {
                                SongItem.Values.from( colorPalette, typography ).copy(
                                    nowPlayingOverlayColor = Color.Transparent
                                )
                            }

                            SongItem.Structure(
                                modifier = Modifier.padding(
                                    top = 5.dp,
                                    bottom = 10.dp
                                ),
                                thumbnail = {
                                    SongItem.Thumbnail( song.cleanThumbnailUrl(), songItemValues )
                                },
                                firstLine = {
                                    SongItem.Title( song.cleanTitle(), songItemValues )
                                },
                                secondLine = {
                                    SongItem.Artists( song.cleanArtistsText(), songItemValues )
                                },
                                trailingContent = {
                                    val isLiked by remember {
                                        Database.songTable
                                                .isLiked( song.id )
                                                .distinctUntilChanged()
                                    }.collectAsState( false, Dispatchers.IO )

                                    Column(
                                        Modifier.width( TabToolBar.TOOLBAR_ICON_SIZE )
                                    ) {
                                        IconButton(
                                            icon = if ( isLiked ) R.drawable.heart else R.drawable.heart_outline,
                                            color = colorPalette.favoritesIcon,
                                            onClick = {
                                                CoroutineScope( Dispatchers.IO ).launch {
                                                    YouTubeSync.toggleSongLike( context, song.asMediaItem )
                                                }
                                            },
                                            modifier = Modifier.padding( all = 4.dp ).size( 20.dp )
                                        )

                                        if( !song.isLocal )
                                            IconButton(
                                                icon = R.drawable.share_social,
                                                color = colorPalette.text,
                                                onClick = {
                                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                                        type = "text/plain"
                                                        putExtra( Intent.EXTRA_TEXT, "https://music.youtube.com/watch?v=${song.id}" )
                                                    }

                                                    context.startActivity(
                                                        Intent.createChooser( intent, null )
                                                    )
                                                },
                                                modifier = Modifier.padding( all = 4.dp ).size( 20.dp )
                                            )
                                    }
                                }
                            )
                        }
                    else if( currentScreen is MenuPage.LocalPlaylist )
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = Color.Transparent,
                        ) {
                            ListItem(
                                leadingContent = {
                                    PlaylistItem.Thumbnail(
                                        playlist = currentScreen.playlist.playlist,
                                        // Not typo, forces this placeholder to match size with SongItem
                                        sizeDp = SongItem.thumbnailSize()
                                    )
                                },
                                headlineContent = {
                                    Text(
                                        text = currentScreen.playlist.playlist.name
                                    )
                                },
                                supportingContent = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            painter = painterResource( R.drawable.musical_notes ),
                                            // Not clickable
                                            contentDescription = null
                                        )

                                        Text(
                                            text = currentScreen.playlist.songCount.toString()
                                        )
                                    }
                                },
                                colors = ListItemDefaults.colors(
                                    containerColor = Color.Transparent,
                                    headlineColor = colorPalette.text,
                                    supportingColor = colorPalette.textSecondary,
                                )
                            )
                        }

                    HorizontalDivider(
                        Modifier.height( 1.dp )
                                .align( Alignment.BottomCenter )
                    )
                }
            }
        ) {
            SideEffect {
                if( currentScreen !is MenuPage.NavRedirect )
                    return@SideEffect

                hide()
                currentScreen.destination.navigateHere(navController, currentScreen.path)
            }

            var buttons by remember {
                mutableStateOf( emptyList<MenuButton<*>>() )
            }
            LaunchedEffect( currentScreen ) {
                buttons = currentScreen.getButtons()
            }

            //<editor-fold desc="Slide direction">
            // Keep track of the previous list size across renders
            var previousSize by remember { mutableIntStateOf(backStack.size) }
            // Determine direction BEFORE updating the stored size
            val isMovingForward = backStack.size >= previousSize
            // Keep previousSize synchronized with the current size
            SideEffect {
                previousSize = backStack.size
            }
            //</editor-fold>
            // Switch between layouts smoothly
            AnimatedContent(
                targetState = currentScreen,
                label = "MenuTransition",
                transitionSpec = {
                    if ( isMovingForward ) {
                        val enter =
                            if( backStack.size > 1 )
                                slideInHorizontally(animationSpec = tween(300)) { it } + fadeIn()
                            else
                                EnterTransition.None

                        enter togetherWith slideOutHorizontally(animationSpec = tween(300)) { -it } + fadeOut()
                    } else
                        (slideInHorizontally(animationSpec = tween(300)) { -it } + fadeIn()) togetherWith
                                slideOutHorizontally(animationSpec = tween(300)) { it } + fadeOut()
                }
            ) { screen ->

                if( app.kreate.preferences.Preferences.MENU_STYLE.value === MenuStyle.List )
                    LazyColumn {
                        items(
                            items = buttons,
                            key = { it.hashCode() }
                        ) { button ->
                            ListItem(
                                leadingContent = {
                                    Icon(
                                        painter = painterResource( button.iconId ),
                                        // Not clickable
                                        contentDescription = null,
                                        modifier = Modifier.size( BUTTON_ICON_SIZE.dp )
                                    )
                                },
                                headlineContent = {
                                    Text(
                                        text = button.title,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = TextAlign.Start,
                                        maxLines = 1,
                                        modifier = Modifier.fillMaxWidth().scrollingText()
                                    )
                                },
                                modifier = Modifier.clickable {
                                    onButtonClick( button, screen )
                                },
                                colors = ListItemDefaults.colors(
                                    containerColor = Color.Transparent,
                                    leadingIconColor = colorPalette.text,
                                    headlineColor = colorPalette.text
                                )
                            )
                        }
                    }
                else
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive( minSize = 120.dp )
                    ) {
                        items(
                            items = buttons,
                            key = { it.hashCode() },
                        ) { button ->
                            Card(
                                onClick = {
                                    onButtonClick( button, screen )
                                },
                                colors = CardDefaults.cardColors(
                                    containerColor = Color.Transparent,
                                    contentColor = colorPalette.text
                                ),
                                modifier = Modifier.aspectRatio( 1f )                   // Ensures it stays perfectly square
                                                   .semantics { role = Role.Button }    // Tells accessibility tools this is a button
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxSize().padding(8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        painter = painterResource( button.iconId ),
                                        // Not clickable
                                        contentDescription = null,
                                        modifier = Modifier.size( BUTTON_ICON_SIZE.dp )
                                    )

                                    Spacer( Modifier.height(16.dp) )

                                    // TODO: Add option to only show icon
                                    Text(
                                        text = button.title,
                                        maxLines = 1, // Keeps the text neat
                                        style = typography.s,
                                        modifier = Modifier.scrollingText()
                                    )
                                }
                            }
                        }
                    }
            }

            // Button dialog won't show up without this
            buttons.filterIsInstance<Dialog>().forEach { it.Render() }

            // If there's a previous page, go back to it.
            // Otherwise, close the menu.
            BackHandler {
                if( backStack.size > 1 )
                    backStack.removeAt( backStack.lastIndex )
                else
                    hide()
            }
        }
    }
}
