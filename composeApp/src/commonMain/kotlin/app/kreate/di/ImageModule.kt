package app.kreate.di

import app.kreate.util.getCacheDir
import coil3.BitmapImage
import coil3.PlatformContext
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import kotlinx.coroutines.Dispatchers
import org.koin.core.scope.Scope
import org.koin.dsl.module


const val THUMBNAIL_SIZE = 900;

expect fun Scope.getCacheSize(): Long

expect fun Scope.getPlatformContext(): PlatformContext

expect fun Scope.getAppIcon(): BitmapImage

val imageModule = module {
    single {
        val size = getCacheSize().coerceAtLeast( 1L )

        DiskCache.Builder()
                 .directory(
                     getCacheDir().resolve( "coil3" )
                 )
                 .maxSizeBytes( size )
                 .cleanupCoroutineContext( Dispatchers.IO )
                 .build()
    }
    single {
        MemoryCache.Builder()
                   .maxSizePercent( getPlatformContext() )
                   .build()
    }
}