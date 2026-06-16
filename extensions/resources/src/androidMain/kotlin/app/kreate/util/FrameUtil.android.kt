package app.kreate.util


actual suspend fun awaitFrame(): Long = kotlinx.coroutines.android.awaitFrame()