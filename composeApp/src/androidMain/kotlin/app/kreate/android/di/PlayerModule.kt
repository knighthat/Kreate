package app.kreate.android.di

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.Cache
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioOffloadSupportProvider
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink.DefaultAudioProcessorChain
import androidx.media3.exoplayer.audio.SilenceSkippingAudioProcessor
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.extractor.DefaultExtractorsFactory
import app.kreate.android.Preferences
import app.kreate.android.service.createDataSourceFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import it.fast4x.rimusic.isHandleAudioFocusEnabled
import it.fast4x.rimusic.utils.isAtLeastAndroid10
import kotlinx.serialization.ExperimentalSerializationApi
import java.io.IOException
import javax.inject.Named
import javax.inject.Singleton


@Module
@InstallIn(SingletonComponent::class)
object PlayerModule {

    @Provides
    @OptIn(UnstableApi::class)
    @kotlin.OptIn(ExperimentalSerializationApi::class)
    @Singleton
    fun providesPlayer(
        @ApplicationContext context: Context,
        @Named("cache") cache: Cache,
        @Named("downloadCache") downloadCache: Cache
    ): ExoPlayer {
        val datasourceFactory = DefaultMediaSourceFactory(
            createDataSourceFactory( context, cache, downloadCache ),
            DefaultExtractorsFactory()
        ).setLoadErrorHandlingPolicy(
            object : DefaultLoadErrorHandlingPolicy() {
                override fun isEligibleForFallback(exception: IOException) = true
            }
        )
        val renderFactory = object : DefaultRenderersFactory(context) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): AudioSink {
                val minimumSilenceDuration: Long = Preferences.AUDIO_SKIP_SILENCE_LENGTH
                                                              .value
                                                              .coerceIn( 1_000L..2_000_000L )

                return DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(enableFloatOutput)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .setAudioOffloadSupportProvider(
                        DefaultAudioOffloadSupportProvider(context)
                    )
                    .setAudioProcessorChain(
                        DefaultAudioProcessorChain(
                            arrayOf(),
                            SilenceSkippingAudioProcessor(
                                /* minimumSilenceDurationUs = */ minimumSilenceDuration,
                                /* silenceRetentionRatio = */ 0.01f,
                                /* maxSilenceToKeepDurationUs = */ minimumSilenceDuration,
                                /* minVolumeToKeepPercentageWhenMuting = */ 0,
                                /* silenceThresholdLevel = */ 256
                            ),
                            SonicAudioProcessor()
                        )
                    )
                    .build()
                    .apply {
                        if (isAtLeastAndroid10) setOffloadMode(AudioSink.OFFLOAD_MODE_DISABLED)
                    }
            }
        }

        return ExoPlayer.Builder(context)
            .setMediaSourceFactory( datasourceFactory )
            .setRenderersFactory( renderFactory )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                isHandleAudioFocusEnabled()
            )
            .setUsePlatformDiagnostics(false)
            .build()
    }
}