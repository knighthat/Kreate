package app.kreate.di

import app.kreate.util.getConfigDir
import okio.FileSystem
import okio.Path
import org.koin.core.scope.Scope


private const val ACTIVE_PROFILE_EXTENSION = "ActiveProfile"

internal actual fun Scope.getProfilePath(): Path {
    val config = getConfigDir()
    val profile = FileSystem.SYSTEM
        .list( config )
        .firstOrNull { child ->
            child.name.endsWith( ".$ACTIVE_PROFILE_EXTENSION", true )
        }
        ?.name
        ?.split( "." )[0]
        ?: "default"

    return config.resolve( profile )
}