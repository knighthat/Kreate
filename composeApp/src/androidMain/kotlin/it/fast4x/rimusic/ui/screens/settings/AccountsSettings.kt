package it.fast4x.rimusic.ui.screens.settings

import app.kreate.android.service.innertube.InnertubeProvider

fun isYouTubeSyncEnabled(): Boolean {
    return isYouTubeLoggedIn() && app.kreate.preferences.Preferences.YOUTUBE_PLAYLISTS_SYNC.value
}

fun isYouTubeLoggedIn(): Boolean =
    app.kreate.preferences.Preferences.YOUTUBE_LOGIN.value && InnertubeProvider.COOKIE_MAP.containsKey( "SAPISID" )





