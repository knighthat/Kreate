package it.fast4x.rimusic.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SnapshotMutationPolicy
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.edit
import app.kreate.database.models.Song
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.requests.HomePage
import kotlinx.serialization.json.Json
import timber.log.Timber

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

val Context.preferences: SharedPreferences
    get() = getSharedPreferences("preferences", Context.MODE_PRIVATE)

@Composable
fun rememberPreference(key: String, defaultValue: Song?): MutableState<Song?> {
    val context = LocalContext.current
    val json = Json.encodeToString(defaultValue)
    return remember {
        mutableStatePreferenceOf(
            try {
                context.preferences.getString(key, json)
                    ?.let { Json.decodeFromString<Song>(it) }
            } catch (e: Exception) {
                Timber.e("RememberPreference RelatedPage Error: ${ e.stackTraceToString() }")
                null
            }
        ) {
            context.preferences.edit { putString(
                key,
                Json.encodeToString(it)
            ) }
        }
    }
}

@Composable
fun rememberPreference(key: String, defaultValue: Innertube.DiscoverPage?): MutableState<Innertube.DiscoverPage?> {
    val context = LocalContext.current
    val json = Json.encodeToString(defaultValue)
    return remember {
        mutableStatePreferenceOf(
            try {
                context.preferences.getString(key, json)
                    ?.let { Json.decodeFromString<Innertube.DiscoverPage>(it) }
            } catch (e: Exception) {
                Timber.e("RememberPreference DiscoverPage Error: ${ e.stackTraceToString() }")
                null
            }
        ) {
            context.preferences.edit { putString(
                key,
                Json.encodeToString(it)
            ) }
        }
    }
}

@Composable
fun rememberPreference(key: String, defaultValue: Innertube.ChartsPage?): MutableState<Innertube.ChartsPage?> {
    val context = LocalContext.current
    val json = Json.encodeToString(defaultValue)
    return remember {
        mutableStatePreferenceOf(
            try {
                context.preferences.getString(key, json)
                    ?.let { Json.decodeFromString<Innertube.ChartsPage>(it) }
            } catch (e: Exception) {
                Timber.e("RememberPreference ChartsPage Error: ${ e.stackTraceToString() }")
                null
            }
        ) {
            context.preferences.edit { putString(
                key,
                Json.encodeToString(it)
            ) }
        }
    }
}

@Composable
fun rememberPreference(key: String, defaultValue: Innertube.RelatedPage?): MutableState<Innertube.RelatedPage?> {
    val context = LocalContext.current
    val json = Json.encodeToString(defaultValue)
    return remember {
        mutableStatePreferenceOf(
            try {
                context.preferences.getString(key, json)
                    ?.let { Json.decodeFromString<Innertube.RelatedPage>(it) }
            } catch (e: Exception) {
                Timber.e("RememberPreference RelatedPage Error: ${ e.stackTraceToString() }")
                null
            }
        ) {
            context.preferences.edit { putString(
                key,
                Json.encodeToString(it)
            ) }
        }
    }
}

@Composable
fun rememberPreference(key: String, defaultValue: HomePage?): MutableState<HomePage?> {
    val context = LocalContext.current
    val json = Json.encodeToString(defaultValue)
    return remember {
        mutableStatePreferenceOf(
            try {
                context.preferences.getString(key, json)
                    ?.let { Json.decodeFromString<HomePage>(it) }
            } catch (e: Exception) {
                Timber.e("RememberPreference HomePage Error: ${ e.stackTraceToString() }")
                null
            }
        ) {
            context.preferences.edit { putString(
                key,
                Json.encodeToString(it)
            ) }
        }
    }
}

fun clearPreference(context: Context, key: String): Unit {
    try {
        context.preferences.edit { remove(key) }
    } catch (e: Exception) {
        Timber.e("ClearPreference Error: ${e.stackTraceToString()}")
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