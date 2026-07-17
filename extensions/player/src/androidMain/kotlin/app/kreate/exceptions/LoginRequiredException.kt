package app.kreate.exceptions

import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi


@UnstableApi
class LoginRequiredException(
    message: String? = null,
    cause: Throwable? = null
): PlaybackException(message, cause, ERROR_CODE_REMOTE_ERROR)
