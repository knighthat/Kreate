package app.kreate.exceptions

import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi


@UnstableApi
class PlayableFormatNotFoundException : PlaybackException("no playable format found", null, 2101)