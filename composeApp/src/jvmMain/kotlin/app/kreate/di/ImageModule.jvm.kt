package app.kreate.di

import coil3.BitmapImage
import coil3.PlatformContext
import org.koin.core.scope.Scope

actual fun Scope.getCacheSize(): Long {
    TODO("Not yet implemented")
}

actual fun Scope.getPlatformContext(): PlatformContext = PlatformContext.INSTANCE

actual fun Scope.getAppIcon(): BitmapImage {
    TODO("Not yet implemented")
}