package app.kreate.preferences

import app.kreate.android.utils.innertube.getSystemCountryCode
import app.kreate.preferences.Preferences.Companion.preferences
import app.kreate.preferences.Preferences.EnumPref
import app.kreate.preferences.Preferences.Key
import app.kreate.preferences.Preferences.StringPref
import it.fast4x.rimusic.enums.OnDeviceSongSortBy
import it.fast4x.rimusic.enums.QueueLoopType


val Preferences.Companion.HOME_ON_DEVICE_SONGS_SORT_BY by lazy {
    EnumPref(preferences, Key.HOME_ON_DEVICE_SONGS_SORT_BY, OnDeviceSongSortBy.Title, OnDeviceSongSortBy::entries)
}
val Preferences.Companion.QUEUE_LOOP_TYPE by lazy {
    EnumPref(preferences, Key.QUEUE_LOOP_TYPE, QueueLoopType.Default, QueueLoopType::entries)
}
val Preferences.Companion.APP_REGION by lazy {
    StringPref(preferences, Key.APP_REGION,  getSystemCountryCode())
}