package app.kreate.android.utils

import androidx.media3.common.MediaItem


val MediaItem.isLocal get() = localConfiguration?.uri?.isLocalFile() ?: false
