package app.kreate.util

import kotlinx.coroutines.delay


actual suspend fun awaitFrame(): Long {
    delay( 100 )
    return 100L
}