package app.kreate.di

import app.kreate.android.Preferences
import app.kreate.android.drawable.AppIcon
import coil3.BitmapImage
import coil3.PlatformContext
import coil3.asImage
import org.koin.core.scope.Scope


actual fun Scope.getCacheSize(): Long = Preferences.IMAGE_CACHE_SIZE.value

actual fun Scope.getPlatformContext(): PlatformContext = get()

actual fun Scope.getAppIcon(): BitmapImage = AppIcon.bitmap( get(), THUMBNAIL_SIZE ).asImage()