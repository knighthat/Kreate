package app.kreate.android.di

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import app.kreate.android.service.DownloadHelper
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import me.knighthat.impl.DownloadHelperImpl
import javax.inject.Singleton


@Module
@InstallIn(SingletonComponent::class)
abstract class DownloadModule {

    @Binds
    @Singleton
    @OptIn(UnstableApi::class)
    abstract fun providesDownloadHelper( impl: DownloadHelperImpl ): DownloadHelper
}