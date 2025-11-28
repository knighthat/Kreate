package me.knighthat.kreate.util

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths


@get:Throws(IllegalStateException::class)
private val OPERATING_SYSTEM by lazy {
    val osName = System.getProperty( "os.name" )
    when {
        osName.contains( "win", true ) -> OperatingSystem.WINDOWS
        osName.contains( "mac", true ) -> OperatingSystem.MACOS
        osName.contains( "linux", true )
                || osName.contains( "unix", true ) -> OperatingSystem.UNIX
        else -> throw IllegalStateException("Unknown OS: $osName")
    }
}

/**
 * Example: C:\Users\user\AppData\Roaming\Kreate
 */
private fun getWindowsConfigPath(): Path {
    val roamingPath = System.getenv( "APPDATA" )

    return Paths.get( roamingPath, "Kreate" )
}

/**
 * Example: C:\Users\user\AppData\Roaming\Kreate
 */
private fun getWindowsCachePath(): Path {
    val roamingPath = System.getenv( "LOCALAPPDATA" )

    return Paths.get( roamingPath, "Kreate" )
}

/**
 * Example: /home/user/.config/Kreate
 *
 * Or whatever `XDG_CONFIG_HOME` is set to
 */
private fun getLinuxXdgConfigPath(): Path {
    val homeDir = System.getProperty( "user.home" )
    val configDir = System.getenv( "XDG_CONFIG_HOME" )
        ?: Paths.get( homeDir, ".config" ).toString()

    return Paths.get( configDir, "Kreate" )
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

    return Paths.get( configDir, "Kreate" )
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

    return Paths.get( configDir, "Kreate" )
}

/**
 * Example: /Users/user/Library/Application Support/Kreate
 */
private fun getMacOsConfigPath(): Path {
    val homeDir = System.getProperty( "user.home" )
    val libraryBase = Paths.get( homeDir, "Library" )

    return Paths.get( libraryBase.toString(), "Application Support", "Kreate" )
}

/**
 * Example: /Users/user/Library/Application Support/Kreate
 */
private fun getMacOsCachePath(): Path {
    val homeDir = System.getProperty( "user.home" )
    val libraryBase = Paths.get( homeDir, "Library" )

    return Paths.get( libraryBase.toString(), "Caches", "Kreate" )
}

@Throws(IllegalStateException::class)
actual fun getConfigDir(): File {
    val osPath = when( OPERATING_SYSTEM ) {
        OperatingSystem.WINDOWS -> getWindowsConfigPath()
        OperatingSystem.MACOS   -> getMacOsConfigPath()
        OperatingSystem.UNIX    -> getLinuxXdgConfigPath()
    }

    // Make sure path exists
    val dir = Files.createDirectories( osPath )
    return dir.toFile()
}

@Throws(IllegalStateException::class)
actual fun getDataDir(): File {
    return if( OPERATING_SYSTEM === OperatingSystem.UNIX ) {
        val path = getLinuxXdgDataPath()
        // Make sure path exists
        val dir = Files.createDirectories( path )
        dir.toFile()
    } else
        getConfigDir()
}

@Throws(IllegalStateException::class)
actual fun getCacheDir(): File {
    val osPath = when( OPERATING_SYSTEM ) {
        OperatingSystem.WINDOWS -> getWindowsCachePath()
        OperatingSystem.MACOS   -> getMacOsCachePath()
        OperatingSystem.UNIX    -> getLinuxXdgCachePath()
    }

    // Make sure path exists
    val dir = Files.createDirectories( osPath )
    return dir.toFile()
}

private enum class OperatingSystem {

    WINDOWS,
    MACOS,
    UNIX;       // Including Linux
}