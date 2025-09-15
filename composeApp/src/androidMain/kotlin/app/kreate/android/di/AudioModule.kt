package app.kreate.android.di

import androidx.media3.exoplayer.ExoPlayer
import app.kreate.android.service.player.VolumeFader
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton


@Module
@InstallIn(SingletonComponent::class)
object AudioModule {

    @Provides
    @Singleton
    fun providesVolumeFader( player: ExoPlayer ): VolumeFader = VolumeFader(player)
}