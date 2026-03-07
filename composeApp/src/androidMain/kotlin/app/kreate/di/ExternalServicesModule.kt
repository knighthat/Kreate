package app.kreate.di

import android.annotation.SuppressLint
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.Cache
import app.kreate.android.service.Discord
import app.kreate.android.service.DownloadHelper
import app.kreate.android.service.player.VolumeObserver
import me.knighthat.impl.DownloadHelperImpl
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module


val externalServicesModule = module {
    singleOf( ::Discord )
    singleOf( ::VolumeObserver )

    @SuppressLint("UnsafeOptInUsageError")
    single<DownloadHelper> {
        val dataSourceFactory: ResolvingDataSource.Factory = get(DatasourceType.DOWNLOADER)
        val downloadCache: Cache = get(CacheType.DOWNLOAD)

        DownloadHelperImpl(dataSourceFactory, get(), downloadCache)
    }
}