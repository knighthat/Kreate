package it.fast4x.rimusic.service

import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi


const val ERROR_CODE_RESPONSE_PARSING_ERROR = 2100

@UnstableApi
class PlayableFormatNotFoundException : PlaybackException(null, null, ERROR_CODE_REMOTE_ERROR)
@UnstableApi
class UnplayableException(
    message: String? = null,
    cause: Throwable? = null
): PlaybackException(message, cause, ERROR_CODE_REMOTE_ERROR)

@UnstableApi
class LoginRequiredException(
    message: String? = null,
    cause: Throwable? = null
): PlaybackException(message, cause, ERROR_CODE_REMOTE_ERROR)

@UnstableApi
class VideoIdMismatchException : PlaybackException(null, null, ERROR_CODE_REMOTE_ERROR)
@UnstableApi
class PlayableFormatNonSupported : PlaybackException(null, null, ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED)

@UnstableApi
class NoInternetException(
    cause: Throwable? = null
): PlaybackException(null, cause, ERROR_CODE_IO_NETWORK_CONNECTION_FAILED)

@UnstableApi
class TimeoutException : PlaybackException(null, null, ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT)

@UnstableApi
class UnknownException(
    message: String? = null,
    cause: Throwable? = null
): PlaybackException(message, cause, ERROR_CODE_REMOTE_ERROR)

@UnstableApi
class FakeException : PlaybackException(null, null, ERROR_CODE_IO_NETWORK_CONNECTION_FAILED)

@UnstableApi
class MissingDecipherKeyException(key: String)
    : PlaybackException("missing \"$key\" key in signatureCipher", null, ERROR_CODE_RESPONSE_PARSING_ERROR)
