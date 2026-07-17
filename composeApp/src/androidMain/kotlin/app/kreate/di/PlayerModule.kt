@file:androidx.media3.common.util.UnstableApi

package app.kreate.di

import app.kreate.android.service.DownloadHelper
import app.kreate.android.service.player.VolumeObserver
import me.knighthat.impl.DownloadHelperImpl
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module


val playerModule = module {
    singleOf( ::VolumeObserver )
    singleOf( ::DownloadHelperImpl ) bind DownloadHelper::class
}