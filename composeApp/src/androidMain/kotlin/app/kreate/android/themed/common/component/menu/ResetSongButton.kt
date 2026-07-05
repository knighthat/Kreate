package app.kreate.android.themed.common.component.menu

import androidx.annotation.OptIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastAny
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.Cache
import app.kreate.android.R
import app.kreate.android.themed.common.component.BottomMenu
import app.kreate.android.utils.innertube.CURRENT_LOCALE
import app.kreate.database.Database
import app.kreate.di.CacheType
import app.kreate.di.clearCachedStreamUrlOf
import co.touchlab.kermit.Logger
import it.fast4x.rimusic.colorPalette
import it.fast4x.rimusic.typography
import it.fast4x.rimusic.ui.components.tab.toolbar.MenuIcon
import it.fast4x.rimusic.utils.asSong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.knighthat.component.dialog.CheckboxDialog
import me.knighthat.component.dialog.ConfirmDialog
import me.knighthat.innertube.Innertube
import me.knighthat.innertube.model.InnertubeSong
import me.knighthat.utils.Toaster
import org.jetbrains.annotations.Contract
import org.koin.core.component.KoinComponent
import org.koin.core.component.get


class ResetSongButton : MenuButton<MediaItem>(), ConfirmDialog, KoinComponent {

    companion object {

        private const val TITLE_CHECKBOX_ID = "title"
        private const val AUTHORS_CHECKBOX_ID = "authors"
        private const val THUMBNAIL_CHECKBOX_ID = "thumbnail"
        private const val PLAYTIME_CHECKBOX_ID = "playtime"
        private const val CACHE_CHECKBOX_ID = "cache"
    }

    private lateinit var song: MediaItem

    val items: MutableList<CheckboxDialog.Item> = ArrayList()
    override val iconId: Int = R.drawable.refresh_circle
    override val tooltipMessageId: Int = R.string.info_open_reset_dialog
    override val dialogTitle: String
        @Composable
        get() = title

    override var isActive: Boolean by mutableStateOf( false )

    override fun onClick( menu: BottomMenu, item: MediaItem ) {
        song = item
        showDialog()
    }

    @Composable
    override fun DialogBody() =
        Column(
            Modifier.fillMaxWidth( .8f )
        ) {
            items.forEach { it.ToolBarButton() }
        }

    @OptIn(UnstableApi::class)
    override fun onConfirm() {
        CoroutineScope( Dispatchers.IO ).launch {
            if( !::song.isInitialized ) return@launch
            var song = this@ResetSongButton.song.asSong

            val fetchIds = arrayOf(TITLE_CHECKBOX_ID, AUTHORS_CHECKBOX_ID, THUMBNAIL_CHECKBOX_ID)
            if( items.fastAny { it.id in fetchIds && it.selected } ) {
                var innertubeSong: InnertubeSong? = null
                Innertube.songBasicInfo( song.id, CURRENT_LOCALE )
                         .onSuccess { innertubeSong = it }
                         .onFailure { err ->
                             Logger.e( "", err, "ResetSongDialog" )
                             Toaster.e( R.string.error_failed_to_fetch_songs_info )
                         }

                @Contract("_,null->null")
                fun <T> getProperty( itemId: String, result: T? ): T? =
                    if ( items.first { it.id == itemId }.selected ) result else null

                val title = getProperty( TITLE_CHECKBOX_ID, innertubeSong?.name )
                val authors = getProperty( AUTHORS_CHECKBOX_ID, innertubeSong?.artistsText )
                val thumbnailUrl = getProperty( THUMBNAIL_CHECKBOX_ID, innertubeSong?.thumbnails?.firstOrNull()?.url )

                song = song.copy(
                    title = title ?: song.title,
                    artistsText = authors ?: song.artistsText,
                    thumbnailUrl = thumbnailUrl ?: song.thumbnailUrl,
                )
            }

            if( items.first { it.id == PLAYTIME_CHECKBOX_ID }.selected )
                song = song.copy( totalPlayTimeMs = 0L )

            Database.asyncTransaction {
                if( items.first { it.id == CACHE_CHECKBOX_ID }.selected ) {
                    clearCachedStreamUrlOf( song.id )

                    get<Cache>(CacheType.CACHE).removeResource( song.id )
                    // FIXME: This is unsafe, use [DownloadService.sendRemoveDownload] instead
                    get<Cache>(CacheType.DOWNLOAD).removeResource( song.id )
                    formatTable.deleteBySongId( song.id )
                    formatTable.updateContentLengthOf( song.id )
                }

                songTable.updateReplace( song )

                Toaster.done()
            }
        }

        hideDialog()
    }

    abstract class Item: MenuIcon {

        companion object {
            val SELECT_ALL: Item by lazy {
                object: Item() {
                    override val id: String = "select_all"
                    override val menuIconTitle: String
                        @Composable
                        get() = "All"

                    override fun onShortClick() {
                        // Disable uncheck
                        if( selected ) return
                        super.onShortClick()
                    }
                }
            }
        }

        abstract val id: String
        override val iconId: Int = -1

        var selected: Boolean by mutableStateOf(false)

        override fun onShortClick() {
            if( selected )
                SELECT_ALL.selected = false

            selected = !selected
        }

        @Composable
        override fun ToolBarButton() {
            LaunchedEffect( SELECT_ALL.selected ) {
                if( SELECT_ALL.selected )
                    selected = true
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding( vertical = 3.dp )
                                   .clickable(
                                       interactionSource = remember { MutableInteractionSource() },
                                       indication = null,
                                       onClick = ::onShortClick
                                   )
            ) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = null,
                    modifier = Modifier.size( 20.dp ),
                    colors = CheckboxDefaults.colors(
                        checkedColor = colorPalette().accent,
                        uncheckedColor = colorPalette().textDisabled,
                        checkmarkColor = colorPalette().onAccent,
                        disabledIndeterminateColor = Color.Transparent
                    )
                )

                Spacer( Modifier.width( 5.dp ) )

                BasicText(
                    text = menuIconTitle,
                    maxLines = 1,
                    style = typography().xs.copy( color = colorPalette().text )
                )
            }
        }
    }
}