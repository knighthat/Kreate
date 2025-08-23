package it.fast4x.rimusic

import android.content.Context
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
fun showSearchIconInNav() = Preferences.SHOW_SEARCH_IN_NAVIGATION_BAR.value

@Composable
fun showStatsIconInNav() = Preferences.SHOW_STATS_IN_NAVIGATION_BAR.value

@Composable
fun binder() = LocalPlayerServiceBinder.current?.service

fun appContext(): Context = Dependencies.application.applicationContext
fun context(): Context = Dependencies.application

fun ytAccountName() = Preferences.YOUTUBE_ACCOUNT_NAME.value
fun ytAccountThumbnail() = Preferences.YOUTUBE_ACCOUNT_AVATAR.value
fun isVideoEnabled() = Preferences.PLAYER_ACTION_TOGGLE_VIDEO.value

fun isConnectionMeteredEnabled() = Preferences.IS_CONNECTION_METERED.value
fun isAutoSyncEnabled() = Preferences.AUTO_SYNC.value
fun isHandleAudioFocusEnabled() = Preferences.AUDIO_SMART_PAUSE_DURING_CALLS.value
fun isBassBoostEnabled() = Preferences.AUDIO_BASS_BOOSTED.value
fun isDebugModeEnabled() = Preferences.RUNTIME_LOG.value