package app.kreate.util

import okio.FileSystem
import okio.Path
import okio.Path.Companion.toOkioPath
import okio.Path.Companion.toPath
import java.nio.file.Paths


private val fileSystem: FileSystem
    get() = FileSystem.SYSTEM

@get:Throws(IllegalStateException::class)
val OPERATING_SYSTEM by lazy {
    val osName = System.getProperty( "os.name" )
    when {
        osName.contains( "win", true ) -> OperatingSystem.WINDOWS
        osName.contains( "mac", true ) -> OperatingSystem.MACOS
        osName.contains( "linux", true )
                || osName.contains( "unix", true ) -> OperatingSystem.UNIX
        else -> throw IllegalStateException("Unknown OS: $osName")
    }
}

private fun getEnvToPath( name: String ) = System.getenv( name ).toPath()

/**
 * Example: C:\Users\user\AppData\Roaming\Kreate
 */
private fun getWindowsConfigPath(): Path = getEnvToPath( "APPDATA" ).resolve( "Kreate" )

/**
 * Example: C:\Users\user\AppData\Roaming\Kreate
 */
private fun getWindowsCachePath(): Path = getEnvToPath( "LOCALAPPDATA" ).resolve( "Kreate" )

/**
 * Example: /home/user/.config/Kreate
 *
 * Or whatever `XDG_CONFIG_HOME` is set to
 */
private fun getLinuxXdgConfigPath(): Path {
    val homeDir = System.getProperty( "user.home" )
    val configDir = System.getenv( "XDG_CONFIG_HOME" )
        ?: Paths.get( homeDir, ".config" ).toString()

    return configDir.toPath().resolve( "Kreate" )
}

/**
 * Example: /home/user/.local/share/Kreate
 *
 * Or whatever `XDG_DATA_HOME` is set to
 */
private fun getLinuxXdgDataPath(): Path {
    val homeDir = System.getProperty( "user.home" )
    val configDir = System.getenv( "XDG_DATA_HOME" )
        ?: Paths.get( homeDir, ".local/share" ).toString()

    return configDir.toPath().resolve( "Kreate" )
}

/**
 * Example: /home/user/.local/share/Kreate
 *
 * Or whatever `XDG_DATA_HOME` is set to
 */
private fun getLinuxXdgCachePath(): Path {
    val homeDir = System.getProperty( "user.home" )
    val configDir = System.getenv( "XDG_CACHE_HOME" )
        ?: Paths.get( homeDir, ".cache" ).toString()

    return configDir.toPath().resolve( "Kreate" )
}

/**
 * Example: /Users/user/Library/Application Support/Kreate
 */
private fun getMacOsConfigPath(): Path {
    val homeDir = System.getProperty( "user.home" )
    val libraryBase = Paths.get( homeDir, "Library" ).toOkioPath()

    return libraryBase.resolve( "Application Support" ).resolve( "Kreate" )
}

/**
 * Example: /Users/user/Library/Application Support/Kreate
 */
private fun getMacOsCachePath(): Path {
    val homeDir = System.getProperty( "user.home" )
    val libraryBase = Paths.get( homeDir, "Library" ).toOkioPath()

    return libraryBase.resolve( "Caches" ).resolve( "Kreate" )
}

@Throws(IllegalStateException::class)
actual fun getConfigDir(): Path {
    val osPath = when( OPERATING_SYSTEM ) {
        OperatingSystem.WINDOWS -> getWindowsConfigPath()
        OperatingSystem.MACOS   -> getMacOsConfigPath()
        OperatingSystem.UNIX    -> getLinuxXdgConfigPath()
    }

    // Make sure path exists
    fileSystem.createDirectories( osPath )
    return osPath
}

@Throws(IllegalStateException::class)
actual fun getDataDir(): Path {
    return if( OPERATING_SYSTEM === OperatingSystem.UNIX ) {
        val path = getLinuxXdgDataPath()
        // Make sure path exists
        fileSystem.createDirectories( path )
        path
    } else
        getConfigDir()
}

@Throws(IllegalStateException::class)
actual fun getCacheDir(): Path {
    val osPath = when( OPERATING_SYSTEM ) {
        OperatingSystem.WINDOWS -> getWindowsCachePath()
        OperatingSystem.MACOS   -> getMacOsCachePath()
        OperatingSystem.UNIX    -> getLinuxXdgCachePath()
    }

    // Make sure path exists
    fileSystem.createDirectories( osPath )
    return osPath
}

enum class OperatingSystem {

    WINDOWS,
    MACOS,
    UNIX;       // Including Linux
}

actual fun getExternalCacheDir(): Path = getCacheDir()