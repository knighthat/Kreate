package me.knighthat.kreate.di

import androidx.room.Room
import me.knighthat.kreate.database.UserDatabase
import org.koin.dsl.module
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.pathString


/**
 * Example: C:\Users\user\AppData\Roaming\Kreate
 */
private fun getWindowsDataPath(): Path {
    val roamingPath = System.getenv( "APPDATA" )

    return Paths.get( roamingPath, "Kreate" )
}

/**
 * Example: /home/user/.config/Kreate
 *
 * Or whatever `XDG_CONFIG_HOME` is set to
 */
private fun getLinuxXdgDataPath(): Path {
    val homeDir = System.getProperty( "user.home" )
    val configDir = System.getenv("XDG_CONFIG_HOME") ?:Paths.get( homeDir, ".config" ).toString()

    return Paths.get( configDir, "Kreate" )
}

/**
 * Example: /Users/user/Library/Application Support/Kreate
 */
private fun getMacOsDataPath(): Path {
    val homeDir = System.getProperty( "user.home" )
    val libraryBase = Paths.get( homeDir, "Library" )

    return Paths.get( libraryBase.toString(), "Application Support", "Kreate" )
}

actual val platformDatabaseModule = module {
    single {
        val osName = System.getProperty( "os.name" )
        val dataPath = when {
            osName.contains( "win", true ) -> getWindowsDataPath()
            osName.contains( "mac", true ) -> getMacOsDataPath()
            osName.contains( "linux", true )
                    ||osName.contains( "unix", true ) -> getLinuxXdgDataPath()
            else -> throw IllegalStateException("Unknown OS: $osName")
        }

        // Make sure path exists
        val dbDir = Files.createDirectories( dataPath )
        val dbFile = dbDir.resolve( UserDatabase.FILENAME )

        Room.databaseBuilder<UserDatabase>(
            name = dbFile.toAbsolutePath().pathString,
        )
    }
}