package it.fast4x.rimusic.utils

import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SnapshotMutationPolicy
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.core.content.edit
import app.kreate.database.models.Song
import app.kreate.di.PrefType
import co.touchlab.kermit.Logger
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.requests.HomePage
import kotlinx.serialization.json.Json
import org.koin.compose.koinInject

const val quickPicsTrendingSongKey = "quickPicsTrendingSong"
const val quickPicsRelatedPageKey = "quickPicsRelatedPage"
const val quickPicsDiscoverPageKey = "quickPicsDiscoverPage"
const val quickPicsHomePageKey = "quickPicsHomePage"

inline fun <reified T : Enum<T>> SharedPreferences.getEnum(
    key: String,
    defaultValue: T
): T =
    getString(key, null)?.let {
        try {
            enumValueOf<T>(it)
        } catch (e: IllegalArgumentException) {
            null
        }
    } ?: defaultValue

inline fun <reified T : Enum<T>> SharedPreferences.Editor.putEnum(
    key: String,
    value: T
): SharedPreferences.Editor =
    putString(key, value.name)

@Composable
fun rememberPreference(key: String, defaultValue: Song?): MutableState<Song?> {
    val preferences: SharedPreferences = koinInject(PrefType.DEFAULT)
    val json = Json.encodeToString(defaultValue)
    return remember {
        mutableStatePreferenceOf(
            try {
                preferences.getString(key, json)
                    ?.let { Json.decodeFromString<Song>(it) }
            } catch (e: Exception) {
                Logger.e("RememberPreference RelatedPage Error", e, "LegacyPreferences" )
                null
            }
        ) {
            preferences.edit { putString(
                key,
                Json.encodeToString(it)
            ) }
        }
    }
}

@Composable
fun rememberPreference(key: String, defaultValue: Innertube.DiscoverPage?): MutableState<Innertube.DiscoverPage?> {
    val preferences: SharedPreferences = koinInject(PrefType.DEFAULT)
    val json = Json.encodeToString(defaultValue)
    return remember {
        mutableStatePreferenceOf(
            try {
                preferences.getString(key, json)
                    ?.let { Json.decodeFromString<Innertube.DiscoverPage>(it) }
            } catch (e: Exception) {
                Logger.e("RememberPreference DiscoverPage Error", e, "LegacyPreferences" )
                null
            }
        ) {
            preferences.edit { putString(
                key,
                Json.encodeToString(it)
            ) }
        }
    }
}

@Composable
fun rememberPreference(key: String, defaultValue: Innertube.ChartsPage?): MutableState<Innertube.ChartsPage?> {
    val preferences: SharedPreferences = koinInject(PrefType.DEFAULT)
    val json = Json.encodeToString(defaultValue)
    return remember {
        mutableStatePreferenceOf(
            try {
                preferences.getString(key, json)
                    ?.let { Json.decodeFromString<Innertube.ChartsPage>(it) }
            } catch (e: Exception) {
                Logger.e("RememberPreference ChartsPage Error", e, "LegacyPreferences" )
                null
            }
        ) {
            preferences.edit { putString(
                key,
                Json.encodeToString(it)
            ) }
        }
    }
}

@Composable
fun rememberPreference(key: String, defaultValue: Innertube.RelatedPage?): MutableState<Innertube.RelatedPage?> {
    val preferences: SharedPreferences = koinInject(PrefType.DEFAULT)
    val json = Json.encodeToString(defaultValue)
    return remember {
        mutableStatePreferenceOf(
            try {
                preferences.getString(key, json)
                    ?.let { Json.decodeFromString<Innertube.RelatedPage>(it) }
            } catch (e: Exception) {
                Logger.e("RememberPreference RelatedPage Error", e, "LegacyPreferences" )
                null
            }
        ) {
            preferences.edit { putString(
                key,
                Json.encodeToString(it)
            ) }
        }
    }
}

@Composable
fun rememberPreference(key: String, defaultValue: HomePage?): MutableState<HomePage?> {
    val preferences: SharedPreferences = koinInject(PrefType.DEFAULT)
    val json = Json.encodeToString(defaultValue)
    return remember {
        mutableStatePreferenceOf(
            try {
                preferences.getString(key, json)
                    ?.let { Json.decodeFromString<HomePage>(it) }
            } catch (e: Exception) {
                Logger.e("RememberPreference HomePage Error", e, "LegacyPreferences" )
                null
            }
        ) {
            preferences.edit { putString(
                key,
                Json.encodeToString(it)
            ) }
        }
    }
}


inline fun <T> mutableStatePreferenceOf(
    value: T,
    crossinline onStructuralInequality: (newValue: T) -> Unit
) =
    mutableStateOf(
        value = value,
        policy = object : SnapshotMutationPolicy<T> {
            override fun equivalent(a: T, b: T): Boolean {
                val areEquals = a == b
                if (!areEquals) onStructuralInequality(b)
                return areEquals
            }
        })