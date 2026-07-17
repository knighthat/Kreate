package app.kreate.exceptions

import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi


@UnstableApi
class MissingDecipherKeyException(
    key: String
) : PlaybackException("missing \"$key\" key in signatureCipher", null, 2100)