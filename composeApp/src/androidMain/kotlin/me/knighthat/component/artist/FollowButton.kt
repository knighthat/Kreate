package me.knighthat.component.artist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.kreate.android.R
import app.kreate.database.models.Artist
import it.fast4x.innertube.YtMusic
import it.fast4x.rimusic.Database
import it.fast4x.rimusic.appContext
import it.fast4x.rimusic.colorPalette
import it.fast4x.rimusic.typography
import it.fast4x.rimusic.ui.components.navigation.header.TabToolBar
import it.fast4x.rimusic.ui.components.tab.toolbar.Button
import it.fast4x.rimusic.ui.components.tab.toolbar.Descriptive
import it.fast4x.rimusic.ui.screens.settings.isYouTubeSyncEnabled
import it.fast4x.rimusic.utils.isNetworkConnected
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.knighthat.utils.Toaster

class FollowButton private constructor(
    private val getArtist: () -> Artist?
): Button, Descriptive {

    companion object {
        @Composable
        operator fun invoke( getArtist: () -> Artist? ): FollowButton =
            FollowButton(getArtist)
    }

    override val messageId: Int = R.string.follow

    override fun onShortClick() {
        if ( isYouTubeSyncEnabled() && !isNetworkConnected( appContext() ) ){
            Toaster.noInternet()
        } else {
            val artist = getArtist() ?: return

            Database.asyncTransaction {
                artistTable.toggleFollow( artist.id )
            }

            CoroutineScope( Dispatchers.IO ).launch {
                if( !isYouTubeSyncEnabled() ) return@launch

                if ( artist.bookmarkedAt != null )
                    YtMusic.unsubscribeChannel( artist.id )
                else
                    YtMusic.subscribeChannel( artist.id )
            }
        }
    }

    @Composable
    override fun ToolBarButton() {
        val artist = getArtist() ?: return
        val isFollowed by remember {
            Database.artistTable
                    .isFollowing( artist.id )
        }.collectAsState( false, Dispatchers.IO )
        val colorPalette = colorPalette()

        val buttonProps: Triple<Int, Color, Color> = remember( isFollowed ) {
            val text = if( isFollowed ) R.string.following else R.string.follow
            val background = if( isFollowed ) colorPalette.accent else colorPalette.background2
            val foreground = if( isFollowed ) colorPalette.onAccent else colorPalette.text

            Triple(text, background, foreground)
        }

        Box(
            modifier = Modifier.requiredSize(
                                   width = 100.dp,
                                   height = TabToolBar.TOOLBAR_ICON_SIZE
                               )
                               .clip( RoundedCornerShape(50) )
                               .background( buttonProps.second )
                               .clickable( onClick = ::onShortClick ),
            contentAlignment = Alignment.Center
        ) {
            BasicText(
                text = stringResource( buttonProps.first ),
                style = typography().s.copy( color = buttonProps.third ),
            )
        }
    }
}