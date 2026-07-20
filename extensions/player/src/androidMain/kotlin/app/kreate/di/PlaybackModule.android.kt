@file:androidx.media3.common.util.UnstableApi

package app.kreate.di

import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSink
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import app.kreate.internal.player.ErrorHandlingPolicy
import app.kreate.internal.player.PlayerImpl
import app.kreate.player.Player
import app.kreate.preferences.Preferences
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.module
import androidx.media3.common.Player as MediaPlayer


const val CHUNK_LENGTH = 512 * 1024L     // 512KB

actual val playbackModule: Module = module {
    // FIXME: This is technically usable but not recommended,
    //  new instance should be created on each injection.
    //  subscribers should use [PlaybackService]'s player instead of injecting
    //  an instance from Koin.
    // TODO: Convert this into factory
    single<ExoPlayer> {
        //<editor-fold desc="DataSource">
        val dataSource = DefaultMediaSourceFactory(
            // At the bottom of the stack, it's download cache
            get<CacheDataSource.Factory>(CacheType.DOWNLOAD)
                // Read-only cache, player doesn't get to write anything in here
                .setCacheWriteDataSinkFactory( null )
                .setUpstreamDataSourceFactory(
                    // Next up is regular cache
                    get<CacheDataSource.Factory>(CacheType.CACHE)
                        // The final upstream handles 2 cases, local files and remote files
                        .setUpstreamDataSourceFactory( get<ResolvingDataSource.Factory>(PLAYBACK_DATA_SOURCE) )
                        // Player is allowed to write chunks into this storage.
                        .setCacheWriteDataSinkFactory(
                            CacheDataSink.Factory()
                                .setCache( get(CacheType.CACHE) )
                                // Chunks are small so recovery can work better
                                .setFragmentSize( CHUNK_LENGTH )
                                // Bigger than default buffer size to avoid
                                // constant write to disk, but small enough
                                // to avoid data loss if app crashes
                                .setBufferSize( 64 * 1024 )     // 64KiB
                        )
                )
        )
        dataSource.setLoadErrorHandlingPolicy( ErrorHandlingPolicy())
        //</editor-fold>
        //<editor-fold desc="Audio handlers">
        val handleAudioFocus = Preferences.AUDIO_SMART_PAUSE_DURING_CALLS.value
        val audioAttributes = AudioAttributes.Builder()
            .setUsage( C.USAGE_MEDIA )
            .setContentType( C.AUDIO_CONTENT_TYPE_MUSIC )
            .build()
        //</editor-fold>

        ExoPlayer.Builder( get() )
            .setMediaSourceFactory( dataSource )
            .setHandleAudioBecomingNoisy( true )
            .setWakeMode( C.WAKE_MODE_NETWORK )
            .setAudioAttributes( audioAttributes, handleAudioFocus )
            .setUsePlatformDiagnostics( false )
            .build()
    } bind MediaPlayer::class

    single<Player> { PlayerImpl(get()) }
}
