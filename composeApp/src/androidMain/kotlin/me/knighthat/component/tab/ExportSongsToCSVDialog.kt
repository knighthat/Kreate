package me.knighthat.component.tab

import android.net.Uri
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.core.net.toFile
import app.kreate.android.BuildConfig
import app.kreate.android.R
import com.github.doyaaaaaken.kotlincsv.dsl.csvWriter
import it.fast4x.rimusic.appContext
import it.fast4x.rimusic.models.Song
import it.fast4x.rimusic.ui.components.tab.toolbar.Descriptive
import it.fast4x.rimusic.ui.components.tab.toolbar.MenuIcon
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.knighthat.component.ExportToFileDialog
import me.knighthat.utils.TimeDateUtils
import me.knighthat.utils.csv.SongCSV
import java.io.OutputStream

/**
 * Create a custom CSV file with replicable information of
 * provided songs. Can be used to share between users.
 *
 * This is the template of CSV file:
 *
 * | PlaylistBrowseId | PlaylistName | MediaId | Title | Artists | Duration | ThumbnailUrl |
 * | --- | --- | --- | --- | --- | --- | --- |
 * | 1 | Awesome Playlist | dQw4w9WgXcQ | A song | Anonymous | 212 | https://... |
 * | 2 | Summer | fNFzfwLM72c | Lovely | Anonymous | 250 | https://... |
 * | 3 | New Songs | kPa7bsKwL-c | DWAS | Anonymous | 253 | https://... |
 */
class ExportSongsToCSVDialog private constructor(
    valueState: MutableState<TextFieldValue>,
    activeState: MutableState<Boolean>,
    launcher: ManagedActivityResultLauncher<String, Uri?>
): ExportToFileDialog(valueState, activeState, launcher), MenuIcon, Descriptive {

    companion object {
        private fun writeToCsvFile( outputStream: OutputStream, songs: List<SongCSV> ): Unit =
            csvWriter().open( outputStream ) {
                writeRow(       // Write down needed sections
                    "PlaylistBrowseId",
                    "PlaylistName",
                    "MediaId",
                    "Title",
                    "Artists",
                    "Duration",
                    "ThumbnailUrl"
                )
                flush()

                songs.forEach { it.write( this ) }
                close()
            }

        @Composable
        operator fun invoke(
            playlistName: String,
            songs: () -> List<Song>,
            playlistBrowseId: String = ""
        ) = ExportSongsToCSVDialog(
            // [playlistName] is an mutable object. Therefore,
            // if it was changed externally, this "remembered"
            // state must be updated as well.
            remember( playlistName ) {
                mutableStateOf( TextFieldValue(playlistName) )
            },
            rememberSaveable { mutableStateOf(false) },
            rememberLauncherForActivityResult(
                ActivityResultContracts.CreateDocument( "text/csv" )
            ) { uri ->
                // [uri] must be non-null (meaning path exists) in order to work
                uri ?: return@rememberLauncherForActivityResult

                // Run in background to prevent UI thread
                // from freezing due to large file.
                CoroutineScope( Dispatchers.IO ).launch {
                    val songsToWrite = songs().map {
                        SongCSV(
                            playlistBrowseId = playlistBrowseId,
                            playlistName = playlistName.ifBlank { uri.toFile().nameWithoutExtension },
                            song = it
                        )
                    }

                    appContext().contentResolver
                                .openOutputStream( uri )
                                ?.use { outStream ->         // Use [use] because it closes stream on exit
                                    writeToCsvFile( outStream, songsToWrite )
                                }
                }
            }
        )
    }

    override val extension: String = "csv"
    override val iconId: Int = R.drawable.export_outline
    override val messageId: Int = R.string.export_playlist
    override val dialogTitle: String
        @Composable
        get() = stringResource( R.string.enter_the_playlist_name )
    override val menuIconTitle: String
        @Composable
        get() = stringResource( messageId )

    override fun onShortClick() = showDialog()

    override fun defaultFileName(): String =
        "${BuildConfig.APP_NAME}_playlist_${TimeDateUtils.localizedDateNoDelimiter()}_${TimeDateUtils.timeNoDelimiter()}"
}