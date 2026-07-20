@file:androidx.media3.common.util.UnstableApi

package app.kreate.di

import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.scheduler.Requirements
import org.koin.dsl.module
import java.util.concurrent.Executors


private const val MAX_PARALLEL_DOWNLOADS = 3

val downloadModule = module {
    single {
        DownloadManager(
            get(),
            // persists download state → survives crashes
            StandaloneDatabaseProvider(get()),
            get(CacheType.DOWNLOAD),
            get<ResolvingDataSource.Factory>(DOWNLOAD_DATA_SOURCE),
            // Always larger than maxParallelDownloads by 1 for the orchestrator
            Executors.newFixedThreadPool(MAX_PARALLEL_DOWNLOADS + 1),
        ).apply {
            maxParallelDownloads = MAX_PARALLEL_DOWNLOADS
            minRetryCount = 2
            requirements = Requirements(Requirements.NETWORK)
        }
    }
}