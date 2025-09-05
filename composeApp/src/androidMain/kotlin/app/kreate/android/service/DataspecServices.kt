package app.kreate.android.service

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSink
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR
import app.kreate.android.di.PlayerModule
import kotlinx.serialization.ExperimentalSerializationApi
import me.knighthat.impl.DownloadHelperImpl

private const val CHUNK_LENGTH = 128 * 1024L     // 128Kb

@UnstableApi
private fun upstreamDatasourceFactory( context: Context ): DataSource.Factory =
    DefaultDataSource.Factory( context, KtorHttpDatasource.Factory(NetworkService.client ) )

//<editor-fold defaultstate="collapsed" desc="Data source factories">
@OptIn(ExperimentalSerializationApi::class)
@UnstableApi
fun PlayerModule.createDataSourceFactory(
    context: Context,
    cache: androidx.media3.datasource.cache.Cache,
    downloadCache: androidx.media3.datasource.cache.Cache
): DataSource.Factory =
    ResolvingDataSource.Factory(
        CacheDataSource.Factory()
                       .setCache( downloadCache )
                       .setUpstreamDataSourceFactory(
                           CacheDataSource.Factory()
                                          .setCache( cache )
                                          .setUpstreamDataSourceFactory(
                                              upstreamDatasourceFactory( context )
                                          )
                                          .setCacheWriteDataSinkFactory(
                                              CacheDataSink.Factory()
                                                           .setCache( cache )
                                                           .setFragmentSize( CHUNK_LENGTH )
                                          )
                                          .setFlags( FLAG_IGNORE_CACHE_ON_ERROR )
                       )
                       .setCacheWriteDataSinkFactory( null )
                       .setFlags( FLAG_IGNORE_CACHE_ON_ERROR ),
        PlayerModule.resolver( context, cache, downloadCache )
    )

@OptIn(ExperimentalSerializationApi::class)
@UnstableApi
fun DownloadHelperImpl.createDataSourceFactory( context: Context ): DataSource.Factory =
    ResolvingDataSource.Factory(
        CacheDataSource.Factory()
                       .setCache( downloadCache )
                       .apply {
                           setUpstreamDataSourceFactory(
                               upstreamDatasourceFactory( context )
                           )
                           setCacheWriteDataSinkFactory( null )
                       },
        PlayerModule.resolver( context, downloadCache )
    )
//</editor-fold>
