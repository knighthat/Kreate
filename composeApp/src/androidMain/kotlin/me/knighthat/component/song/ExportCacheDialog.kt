package me.knighthat.component.song

import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.CacheSpan
import app.kreate.android.R
import app.kreate.database.models.Song
import it.fast4x.rimusic.Database
import it.fast4x.rimusic.appContext
import it.fast4x.rimusic.service.modern.PlayerServiceModern
import it.fast4x.rimusic.ui.components.tab.toolbar.Descriptive
import it.fast4x.rimusic.ui.components.tab.toolbar.MenuIcon
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import me.knighthat.component.ExportToFileDialog
import me.knighthat.utils.Toaster

class ExportCacheDialog(
    activeState: MutableState<Boolean>,
    valueState: MutableState<TextFieldValue>,
    launcher: ManagedActivityResultLauncher<String, Uri?>,
    private val getSong: () -> Song
) : ExportToFileDialog(valueState, activeState, launcher), MenuIcon, Descriptive {

    companion object {
        @UnstableApi
        private fun onExport(
            uri: Uri,
            binder: PlayerServiceModern.Binder ,
            song: Song
        ) = CoroutineScope( Dispatchers.IO ).launch {       // Run in background to prevent UI thread from freezing due to large file.
            val contentLength =  Database.formatTable.findContentLengthOf( song.id ).first()

            val isCached = binder.cache.isCached( song.id, 0, contentLength )
            val isDownloaded = binder.downloadCache.isCached( song.id, 0, contentLength )

            if( !isCached && !isDownloaded ) {
                Toaster.i( R.string.song_must_be_cached_or_downloaded_to_export )

                try {
                    // Attempt to delete created file
                    DocumentsContract.deleteDocument( appContext().contentResolver, uri )
                } catch ( _: Exception ) {}

                return@launch
            }

            val dataInBytes =
                (if( isCached ) binder.cache else binder.downloadCache).getCachedSpans( song.id )
                                                                       .mapNotNull( CacheSpan::file )
                                                                       .flatMap { it.readBytes().asList() }
                                                                       .toByteArray()

            appContext().contentResolver
                        .openOutputStream( uri )
                        ?.use { outStream ->
                            outStream.write( dataInBytes )
                        }

            Toaster.done()
        }

        @UnstableApi
        @Composable
        operator fun invoke(
            binder: PlayerServiceModern.Binder?,
            getSong: () -> Song
        ): ExportCacheDialog = ExportCacheDialog(
            remember { mutableStateOf(false) },
            remember( getSong().title ) {
                mutableStateOf( TextFieldValue("${getSong().title} - ${getSong().cleanArtistsText()}") )
            },
            rememberLauncherForActivityResult(
                ActivityResultContracts.CreateDocument( "audio/mp4" )
            ) { uri ->
                // [uri] must be non-null (meaning path exists) in or
                uri ?: return@rememberLauncherForActivityResult
                // Same thing with binder
                binder ?: return@rememberLauncherForActivityResult

                onExport( uri, binder, getSong() )
            },
            getSong
        )
    }

    override val extension: String = "m4a"
    override val iconId: Int = R.drawable.export_outline
    override val messageId: Int = R.string.info_export_cached_or_downloaded_song
    override val dialogTitle: String
        @Composable
        get() = stringResource( R.string.title_name_your_export )
    override val menuIconTitle: String
        @Composable
        get() = stringResource( R.string.export_cached )

    override fun onShortClick() = showDialog()

    override fun defaultFileName(): String =
        with( getSong() ) { "$title - ${cleanArtistsText()}" }
}