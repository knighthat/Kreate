package app.kreate.android.di

import android.content.Context
import androidx.media3.exoplayer.ExoPlayer
import app.kreate.android.service.player.VolumeObserver
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton


@Module
@InstallIn(SingletonComponent::class)
object AudioModule {

    @Provides
    @Singleton
    fun providesVolumeObserver(
        @ApplicationContext context: Context,
        player: ExoPlayer
    ): VolumeObserver = VolumeObserver(context, player)
}