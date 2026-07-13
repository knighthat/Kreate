package it.fast4x.rimusic.ui.screens.settings

import app.kreate.gateway.innertube.YouTube
import org.koin.java.KoinJavaComponent.get

fun isYouTubeSyncEnabled(): Boolean {
    return isYouTubeLoggedIn() && app.kreate.preferences.Preferences.YOUTUBE_PLAYLISTS_SYNC.value
}

fun isYouTubeLoggedIn(): Boolean = get<YouTube>(YouTube::class.java).isLoggedIn()





