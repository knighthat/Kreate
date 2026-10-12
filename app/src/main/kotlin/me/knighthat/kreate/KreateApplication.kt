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

package me.knighthat.kreate

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.ktor3.KtorNetworkFetcherFactory
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import me.knighthat.kreate.database.ActiveUser
import me.knighthat.kreate.database.databaseModule
import me.knighthat.kreate.media.LocalCoverFetcher
import me.knighthat.kreate.media.LocalCoverKeyer
import me.knighthat.kreate.preferences.Preferences
import me.knighthat.kreate.preferences.preferencesModule
import me.knighthat.kreate.screen.setupCompletedFile
import me.knighthat.kreate.ui.screen.KreateAppViewModel
import okio.Path.Companion.toOkioPath
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.bind
import org.koin.dsl.module

class KreateApplication : Application(), SingletonImageLoader.Factory {

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(KtorNetworkFetcherFactory(httpClient = { get<HttpClient>() }))
                add(LocalCoverKeyer())
                add(LocalCoverFetcher.Factory())
            }
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, Preferences.IMAGE_MEMORY_CACHE_PERCENT.value)
                    .build()
            }
            .diskCache {
                val directory = cacheDir.resolve("coil3_image_cache").toOkioPath()

                DiskCache.Builder()
                    .directory(directory)
                    .maxSizeBytes(Preferences.IMAGE_DISK_CACHE.value)
                    .build()
            }
            .build()

    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidContext(this@KreateApplication)

            modules(
                module {
                    viewModelOf(::KreateAppViewModel)

                    // Lives as long as the process; use for work that must outlive any screen
                    single { CoroutineScope(SupervisorJob() + Dispatchers.Default) }

                    // Creates the database users of the profiles, and tells the database who is active
                    val isSetup = setupCompletedFile().exists()
                    single(createdAtStart = isSetup) {
                        ProfileUserSync(
                            get(),
                            get(),
                            get()
                        )
                    } bind ActiveUser::class
                },
                databaseModule,
                preferencesModule,
                networkModule,
            )
        }
    }
}