package app.kreate.android.service.player

import androidx.media3.common.MediaItem
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface StatefulPlayer : ExoPlayer {

    val currentMediaItemState: StateFlow<MediaItem?>
    val currentTimelineState: StateFlow<Timeline>
    val currentWindowState: StateFlow<Timeline.Window?>
}