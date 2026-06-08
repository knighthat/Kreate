package app.kreate.di

import android.content.Context
import android.content.SharedPreferences
import okio.Path
import okio.Path.Companion.toOkioPath
import org.koin.core.scope.Scope
import org.koin.dsl.module
import org.koin.java.KoinJavaComponent.get


private const val PROFILE_PREFERENCES_FILENAME = "profiles"
const val ACTIVE_PROFILE_KEY = "ActiveProfile"


internal actual fun Scope.getProfilePath(): Path {
    val context: Context = get()
    val profile = getActiveProfile()

    return context.filesDir.resolve( profile ).toOkioPath()
}

val profileModule = module {
    single( PrefType.PROFILES, true ) {
        val context: Context = get()
        context.getSharedPreferences( PROFILE_PREFERENCES_FILENAME, Context.MODE_PRIVATE )
    }
}

fun getActiveProfile(): String =
    get<SharedPreferences>( SharedPreferences::class.java, PrefType.PROFILES ).getString( ACTIVE_PROFILE_KEY, "default" )!!