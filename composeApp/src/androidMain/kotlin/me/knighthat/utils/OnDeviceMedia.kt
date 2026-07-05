package me.knighthat.utils

import android.content.ContentUris
import android.content.Context
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.core.net.toUri
import app.kreate.constant.SortOrder
import app.kreate.database.Database
import app.kreate.database.models.Format
import app.kreate.database.models.Song
import app.kreate.preferences.Preferences
import app.kreate.util.isDocumentTree
import it.fast4x.rimusic.enums.OnDeviceSongSortBy
import it.fast4x.rimusic.utils.isAtLeastAndroid10
import it.fast4x.rimusic.utils.isAtLeastAndroid11
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import java.io.File
import java.util.StringJoiner
import kotlin.time.Duration.Companion.milliseconds

val PROJECTION by lazy {
    var projection = arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.DISPLAY_NAME,
        MediaStore.Audio.Media.DURATION,
        MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.ALBUM_ID,
        if (isAtLeastAndroid10) {
            MediaStore.Audio.Media.RELATIVE_PATH
        } else {
            MediaStore.Audio.Media.DATA
        },
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.IS_MUSIC,
        MediaStore.Audio.Media.MIME_TYPE,
        MediaStore.Audio.Media.DATE_MODIFIED,
        MediaStore.Audio.Media.SIZE,
    )
    if ( isAtLeastAndroid11 )
        projection += MediaStore.Audio.Media.BITRATE

    return@lazy projection
}
val PRO = buildList {
    add( MediaStore.Audio.Media._ID )
    add( MediaStore.Audio.Media.DISPLAY_NAME )
    add( MediaStore.Audio.Media.DURATION )
    add( MediaStore.Audio.Media.ARTIST )
    add( MediaStore.Audio.Media.ALBUM_ID )
    if (isAtLeastAndroid10) {
        add( MediaStore.Audio.Media.RELATIVE_PATH )
    } else {
        add( MediaStore.Audio.Media.DATA )
    }
    add( MediaStore.Audio.Media.TITLE )
    add( MediaStore.Audio.Media.IS_MUSIC )
    add( MediaStore.Audio.Media.MIME_TYPE )
    add( MediaStore.Audio.Media.DATE_MODIFIED )
    add( MediaStore.Audio.Media.SIZE )
    if ( isAtLeastAndroid11 )
        add( MediaStore.Audio.Media.BITRATE )
}.toTypedArray()

val ALBUM_URI = "content://media/external/audio/albumart".toUri()

private fun blacklistedPaths(): Set<String> =
    Preferences.BLACKLISTED_FOLDERS
               .value
               .mapNotNull {
                   val uri = it.toUri()
                   val docId = if( uri.isDocumentTree ) DocumentsContract.getTreeDocumentId( uri ) else return@mapNotNull null
                   val parts = docId.split(":")
                   if ( parts.size < 2 )
                       // Skip if not a valid (corrupted) path
                       return@mapNotNull null
                   val rootType = parts[0]
                   val relativePath = parts[1]
                   val base = if( rootType.equals("primary", ignoreCase = true) ) {
                       // Map "primary" to the actual internal storage base path
                       Environment.getExternalStorageDirectory().absolutePath
                   } else {
                       // Handle SD Cards (e.g., "/storage/1234-ABCD/Music")
                       "/storage/$rootType"
                   }
                   if( relativePath.isEmpty() ) base else "$base/$relativePath"
               }
                .toSet()

fun Context.getLocalSongs(
    sortBy: OnDeviceSongSortBy,
    sortOrder: SortOrder
): Flow<Map<Song, String>> = flow {
    val results = linkedMapOf<Song, String>()

    //<editor-fold desc="Selection">
    val selectionBuilder = StringJoiner(" AND ", "(", ")")
    val selectionArgs = mutableListOf<String>()
    selectionBuilder.add( "${MediaStore.Audio.Media.IS_MUSIC} > 0" )
    blacklistedPaths().forEach { path ->
        selectionBuilder.add( "${MediaStore.MediaColumns.DATA} NOT LIKE ?" )

        // Clean up trailing slashes to ensure the wildcard matches consistently
        val cleanPath = path.removeSuffix("/")
        selectionArgs.add( "${cleanPath}%" )
    }
    val selection = selectionBuilder.toString()
    //</editor-fold>

    val uri =
        if (isAtLeastAndroid10)
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        else
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
    val order = "${sortBy.value} COLLATE NOCASE ${sortOrder.asSqlString}"

    contentResolver.query( uri, PROJECTION, selection, selectionArgs.toTypedArray(), order )?.use { cursor ->
        val idColumn = cursor.getColumnIndex( MediaStore.Audio.Media._ID )
        val nameColumn = cursor.getColumnIndex( MediaStore.Audio.Media.DISPLAY_NAME )
        val durationColumn = cursor.getColumnIndex( MediaStore.Audio.Media.DURATION )
        val artistColumn = cursor.getColumnIndex( MediaStore.Audio.Media.ARTIST )
        val albumIdColumn = cursor.getColumnIndex( MediaStore.Audio.Media.ALBUM_ID )
        val pathColumn = if (isAtLeastAndroid10) {
            cursor.getColumnIndex( MediaStore.Audio.Media.RELATIVE_PATH )
        } else {
            cursor.getColumnIndex( MediaStore.Audio.Media.DATA )
        }
        val titleColumn = cursor.getColumnIndex( MediaStore.Audio.Media.TITLE )
        val mimeTypeColumn = cursor.getColumnIndex( MediaStore.Audio.Media.MIME_TYPE )
        val bitrateColumn = if (isAtLeastAndroid11) cursor.getColumnIndex( MediaStore.Audio.Media.BITRATE ) else -1
        val fileSizeColumn = cursor.getColumnIndex( MediaStore.Audio.Media.SIZE )
        val dateModifiedColumn = cursor.getColumnIndex( MediaStore.Audio.Media.DATE_MODIFIED )

        while( cursor.moveToNext() ) {
            val relPath = cursor.getString( pathColumn ).run {
                // Before Android 10, absolute file path should look like this
                // /storage/emulated/0/Music/path/to/MySong.m4a
                // So this step is needed to remove file's name out of the path
                val file = File(this)
                if( file.isFile )
                    file.parent!!
                else
                    this
            }

            // Nullable so SongItem can display "--:--"
            // TODO apply some non-null method
            val durationText =
                cursor.getInt( durationColumn )
                    .takeIf { it > 0 }
                    ?.milliseconds
                    ?.toComponents { hrs, mins, secs, _ ->
                        if( hrs > 0 )
                            "%02d:%02d:%02d".format( hrs, mins, secs )
                        else
                            "%02d:%02d".format( mins, secs )
                    }
            val id = cursor.getLong( idColumn )
            val title = cursor.getString( titleColumn ) ?: cursor.getString( nameColumn )
            val artist = cursor.getString( artistColumn )
            val albumUri = ContentUris.withAppendedId( ALBUM_URI, cursor.getLong( albumIdColumn ) )
            val song = Song( id.toString(), title, artist, durationText, albumUri.toString(), isLocal = true )

            val mimeType = cursor.getString( mimeTypeColumn )
            val bitrate = if( isAtLeastAndroid11 ) cursor.getLong( bitrateColumn ) else 0
            val fileSize = cursor.getLong( fileSizeColumn )
            val dateModified = cursor.getLong( dateModifiedColumn )
            val format = Format( song.id, 0, mimeType, bitrate, fileSize, dateModified )

            Database.asyncTransaction {
                songTable.insertIgnore( song )
                formatTable.upsert( format )
            }

            results[song] = relPath
        }
    }

    emit( results )
}.stateIn( CoroutineScope(Dispatchers.IO), SharingStarted.Eagerly, mapOf() )
