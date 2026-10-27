/*
 * Kreate
 * Copyright (C) 2026 KnightHat
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package me.knighthat.kreate3

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.annotation.ExperimentalCoilApi
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.NetworkFetcher
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import me.knighthat.kreate3.backup.Backup
import me.knighthat.kreate3.database.ActiveUser
import me.knighthat.kreate3.database.databaseModule
import me.knighthat.kreate3.download.downloadModule
import me.knighthat.kreate3.lyrics.LyricsSource
import me.knighthat.kreate3.lyrics.lyricsModule
import me.knighthat.kreate3.notification.DownloadNotifications
import me.knighthat.kreate3.notification.LocalScanNotifications
import me.knighthat.kreate3.notification.Notifier
import me.knighthat.kreate3.notification.SystemNotifications
import me.knighthat.kreate3.notification.createNotificationChannels
import me.knighthat.kreate3.playback.PlaybackLifecycle
import me.knighthat.kreate3.playback.playbackModule
import me.knighthat.kreate3.preferences.Preferences
import me.knighthat.kreate3.preferences.preferencesModule
import me.knighthat.kreate3.provider.opensubsonic.OpenSubsonicProvider
import me.knighthat.kreate3.provider.providerModule
import me.knighthat.kreate3.provider.youtube.YouTubeProvider
import me.knighthat.kreate3.source.CoverFiles
import me.knighthat.kreate3.source.DeviceReactions
import me.knighthat.kreate3.source.DeviceReactionsImpl
import me.knighthat.kreate3.source.SongStore
import me.knighthat.kreate3.source.SourceNetworkClient
import me.knighthat.kreate3.source.local.LocalCoverFetcher
import me.knighthat.kreate3.source.local.LocalCoverKeyer
import me.knighthat.kreate3.source.opensubsonic.OpenSubsonicCoverFetcher
import me.knighthat.kreate3.source.youtube.YouTubeLyricsSource
import me.knighthat.kreate3.ui.screen.setupCompletedFile
import okio.Path.Companion.toOkioPath
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.dsl.bind
import org.koin.dsl.module

class KreateApplication : Application(), SingletonImageLoader.Factory {

    /**
     * Starts the app over in a new process, the current one ends. The task is cleared and started
     * again the way the launcher starts it, [configure] can add to that start, like an action and
     * extras for the new [KreateActivity] to read.
     */
    internal fun restart(configure: Intent.() -> Unit = {}) {
        // The enabled launcher icon, see AppIcon
        val component = packageManager.getLaunchIntentForPackage(packageName)
            ?.component
            ?: ComponentName(this, KreateActivity::class.java)
        startActivity(Intent.makeRestartActivityTask(component).apply(configure))
        // The system queued the new start already, a fresh process picks it up
        Runtime.getRuntime().exit(0)
    }

    /**
     * [restart] once playback stopped and what the player saves is written, see
     * [PlaybackLifecycle.restartAround]. Runs in the global scope, leaving the screen doesn't stop it.
     */
    internal fun restartAfterPlayback(): Job = get<CoroutineScope>().launch {
        get<PlaybackLifecycle>().restartAround { restart() }
    }

    @OptIn(ExperimentalCoilApi::class)
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                // Before the network fetcher, server covers look like any address but need the sign in
                add(OpenSubsonicCoverFetcher.Factory(get<OpenSubsonicProvider>()))
                add(
                    NetworkFetcher.Factory(
                        networkClient = {
                            SourceNetworkClient(
                                get<HttpClient>(),
                                get<YouTubeProvider>()
                            )
                        }
                    )
                )
                add(LocalCoverKeyer())
                add(LocalCoverFetcher.Factory())
                // A downloaded cover's small copy where it is drawn small
                add(CoverFiles.Mapper(get()))
            }
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(
                        context,
                        AppRestart.readOnce(Preferences.IMAGE_MEMORY_CACHE_PERCENT)
                    )
                    .build()
            }
            .diskCache {
                val directory = cacheDir.resolve("coil3_image_cache").toOkioPath()

                DiskCache.Builder()
                    .directory(directory)
                    .maxSizeBytes(AppRestart.readOnce(Preferences.IMAGE_DISK_CACHE))
                    .build()
            }
            .build()

    override fun onCreate() {
        super.onCreate()

        // Before anything opens the files it replaces
        Backup.applyPendingRestore(this)

        // Before anything notifies, a notification on a missing channel doesn't show
        createNotificationChannels()
        // A scan's progress left by a killed process, before the work starts again
        LocalScanNotifications(this).clearStale()
        DownloadNotifications(this).clearStale()

        startKoin {
            androidContext(this@KreateApplication)

            modules(
                module {
                    // Lives as long as the process; use for work that must outlive any screen
                    single { CoroutineScope(SupervisorJob() + Dispatchers.Default) }

                    // Messages for the user, as system notifications while no screen shows them
                    single { SystemNotifications(androidContext()) }
                    single { Notifier(get()) }
                    single { LocalScanNotifications(androidContext()) }

                    // Covers kept as files: downloaded ones and the user's own
                    single { CoverFiles(androidContext()) }

                    // Creates the database users of the profiles, and tells the database who is active
                    val isSetup = setupCompletedFile().exists()
                    single(createdAtStart = isSetup) {
                        ProfileUserSync(
                            get(),
                            get(),
                            get()
                        )
                    } bind ActiveUser::class

                    // Lyrics of YouTube songs, the app knows both the lyrics and the providers
                    single { YouTubeLyricsSource(get()) } bind LyricsSource::class

                    single { Backup(get(), get(), get()) }

                    // Stores songs fetched live once the user keeps something about them
                    single { SongStore(get(), get(), get(), get()) }
                    single<DeviceReactions> { DeviceReactionsImpl(get(), get(), get(), get()) }
                    single { ProfileSwitcher(androidContext(), get(), get(), get(), get(), get()) }
                },
                databaseModule,
                preferencesModule,
                providerModule,
                lyricsModule,
                networkModule,
                playbackModule,
                downloadModule,
                viewModelModule
            )
        }

        // Covers whose file is gone, like after a backup from another device was restored
        get<CoroutineScope>().launch { get<CoverFiles>().forgetMissing(get()) }
    }
}