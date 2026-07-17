package app.kreate.exceptions

import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi


@UnstableApi
class NoInternetException(
    cause: Throwable? = null
): PlaybackException(null, cause, ERROR_CODE_IO_NETWORK_CONNECTION_FAILED)