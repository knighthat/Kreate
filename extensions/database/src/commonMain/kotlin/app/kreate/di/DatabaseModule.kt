package app.kreate.di

import androidx.annotation.VisibleForTesting
import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import app.kreate.database.repositories.AlbumTable
import app.kreate.database.repositories.ArtistTable
import app.kreate.database.repositories.EventTable
import app.kreate.database.repositories.FormatTable
import app.kreate.database.repositories.LyricsTable
import app.kreate.database.repositories.PlaylistTable
import app.kreate.database.repositories.QueuedMediaItemTable
import app.kreate.database.repositories.SearchQueryTable
import app.kreate.database.repositories.SongAlbumMapTable
import app.kreate.database.repositories.SongArtistMapTable
import app.kreate.database.repositories.SongPlaylistMapTable
import app.kreate.database.repositories.SongTable
import app.kreate.internal.database.AbstractRoomDatabase
import app.kreate.internal.database.callbacks.ClearGhostMaps
import app.kreate.internal.database.callbacks.EnableFeatures
import app.kreate.internal.database.migrations.From10To11Migration
import app.kreate.internal.database.migrations.From14To15Migration
import app.kreate.internal.database.migrations.From22To23Migration
import app.kreate.internal.database.migrations.From23To24Migration
import app.kreate.internal.database.migrations.From24To25Migration
import app.kreate.internal.database.migrations.From25To26Migration
import app.kreate.internal.database.migrations.From26To27Migration
import app.kreate.internal.database.migrations.From27To28Migration
import app.kreate.internal.database.migrations.From28To29Migration
import app.kreate.internal.database.migrations.From29To30Migration
import app.kreate.internal.database.migrations.From30To31Migration
import app.kreate.internal.database.migrations.From34To35Migration
import app.kreate.internal.database.migrations.From35To36Migration
import app.kreate.internal.database.migrations.From8To9Migration
import kotlinx.coroutines.Dispatchers
import org.koin.core.scope.Scope
import org.koin.dsl.bind
import org.koin.dsl.module


expect val DATABASE_FILENAME: String

val databaseModule = module {
    // RoomDatabase is the source of truth, so only one instance can be created at a time
    single {
        getDatabaseBuilder()
            .loadConfig()
            .addCallback( ClearGhostMaps() )
            .addCallback( EnableFeatures() )
            .apply {
                dbCallbacks.forEach( ::addCallback )
            }
            .build()
    } bind RoomDatabase::class

    factory<AlbumTable> { get<AbstractRoomDatabase>().albumTable }
    factory<ArtistTable> { get<AbstractRoomDatabase>().artistTable }
    factory<EventTable> { get<AbstractRoomDatabase>().eventTable }
    factory<FormatTable> { get<AbstractRoomDatabase>().formatTable }
    factory<LyricsTable> { get<AbstractRoomDatabase>().lyricsTable }
    factory<PlaylistTable> { get<AbstractRoomDatabase>().playlistTable }
    factory<QueuedMediaItemTable> { get<AbstractRoomDatabase>().queueTable }
    factory<SearchQueryTable> { get<AbstractRoomDatabase>().searchQueryTable }
    factory<SongAlbumMapTable> { get<AbstractRoomDatabase>().songAlbumMapTable }
    factory<SongArtistMapTable> { get<AbstractRoomDatabase>().songArtistMapTable }
    factory<SongPlaylistMapTable> { get<AbstractRoomDatabase>().songPlaylistMapTable }
    factory<SongTable> { get<AbstractRoomDatabase>().songTable }
}

val dbCallbacks = mutableListOf<RoomDatabase.Callback>()

internal expect fun Scope.getDatabaseBuilder(): RoomDatabase.Builder<AbstractRoomDatabase>

@VisibleForTesting
internal fun RoomDatabase.Builder<AbstractRoomDatabase>.loadConfig(): RoomDatabase.Builder<AbstractRoomDatabase> =
    this.setDriver( BundledSQLiteDriver() )
        .setQueryCoroutineContext( Dispatchers.IO )
        .addMigrations(
            From8To9Migration(),
            From10To11Migration(),
            From14To15Migration(),
            From22To23Migration(),
            From23To24Migration(),
            From24To25Migration(),
            From25To26Migration(),
            From26To27Migration(),
            From27To28Migration(),
            From28To29Migration(),
            From29To30Migration(),
            From30To31Migration(),
            From34To35Migration(),
            From35To36Migration()
        )
