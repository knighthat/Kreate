package it.fast4x.rimusic

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import app.kreate.android.Preferences
import it.fast4x.rimusic.ui.styling.LocalAppearance

@Composable
fun typography() = LocalAppearance.current.typography

@Composable
@ReadOnlyComposable
fun colorPalette() = LocalAppearance.current.colorPalette

@Composable
fun thumbnailShape() = LocalAppearance.current.thumbnailShape

@Composable
fun showSearchIconInNav() = app.kreate.preferences.Preferences.SHOW_SEARCH_IN_NAVIGATION_BAR.value

@Composable
fun showStatsIconInNav() = app.kreate.preferences.Preferences.SHOW_STATS_IN_NAVIGATION_BAR.value

fun ytAccountName() = Preferences.YOUTUBE_ACCOUNT_NAME.value
fun ytAccountThumbnail() = Preferences.YOUTUBE_ACCOUNT_AVATAR.value
fun isVideoEnabled() = app.kreate.preferences.Preferences.PLAYER_ACTION_TOGGLE_VIDEO.value

fun isConnectionMeteredEnabled() = app.kreate.preferences.Preferences.IS_CONNECTION_METERED.value
fun isAutoSyncEnabled() = app.kreate.preferences.Preferences.AUTO_SYNC.value
fun isHandleAudioFocusEnabled() = app.kreate.preferences.Preferences.AUDIO_SMART_PAUSE_DURING_CALLS.value
fun isBassBoostEnabled() = app.kreate.preferences.Preferences.AUDIO_BASS_BOOSTED.value
fun isDebugModeEnabled() = app.kreate.preferences.Preferences.RUNTIME_LOG.value