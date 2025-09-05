package app.kreate.android.service

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSink
import androidx.media3.datasource.cache.CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR
import app.kreate.android.di.PlayerModule
import kotlinx.serialization.ExperimentalSerializationApi
import me.knighthat.impl.DownloadHelperImpl

private const val CHUNK_LENGTH = 128 * 1024L     // 128Kb

//<editor-fold defaultstate="collapsed" desc="Data source factories">
@OptIn(ExperimentalSerializationApi::class)
@UnstableApi
fun PlayerModule.createDataSourceFactory(
    context: Context,
    cache: Cache,
    downloadCache: Cache
): DataSource.Factory =
    ResolvingDataSource.Factory(
        PlayerModule.dataSourceFactoryFrom( downloadCache )
            .setCacheWriteDataSinkFactory( null )
            .setFlags( FLAG_IGNORE_CACHE_ON_ERROR )
                       .setUpstreamDataSourceFactory(
                           PlayerModule.dataSourceFactoryFrom( cache )
                                          .setUpstreamDataSourceFactory(
                                              PlayerModule.providesKtorUpstreamDataSourceFactory()
                                          )
                                          .setCacheWriteDataSinkFactory(
                                              CacheDataSink.Factory()
                                                           .setCache( cache )
                                                           .setFragmentSize( CHUNK_LENGTH )
                                          )
                                          .setFlags( FLAG_IGNORE_CACHE_ON_ERROR )
                       ),
        PlayerModule.resolver( context, cache, downloadCache )
    )

@OptIn(ExperimentalSerializationApi::class)
@UnstableApi
fun DownloadHelperImpl.createDataSourceFactory( context: Context ): DataSource.Factory =
    ResolvingDataSource.Factory(
        PlayerModule.dataSourceFactoryFrom( downloadCache )
                       .setUpstreamDataSourceFactory(
                           PlayerModule.providesKtorUpstreamDataSourceFactory()
                       )
                       .setCacheWriteDataSinkFactory( null ),
        PlayerModule.resolver( context, downloadCache )
    )
//</editor-fold>
