package app.kreate.util

import okio.FileSystem
import okio.IOException
import okio.Path
import okio.SYSTEM
import java.io.File


private val fileSystem: FileSystem
    get() = FileSystem.SYSTEM

/**
 * @return location to store config files such as settings
 */
expect fun getConfigDir(): Path

/**
 * @return location to store app's persistent
 */
expect fun getDataDir(): Path

/**
 * @return location to store temporary data.
 */
expect fun getCacheDir(): Path

/**
 * @return user accessible cache location
 */
expect fun getExternalCacheDir(): Path

/**
 * @return location to store logs as app runs. Can be wiped by the OS
 *
 * @throws IOException if destination isn't a directory
 */
fun getRuntimeLogDir(): Path {
    val dir = getExternalCacheDir().resolve( "logs" )
    val metadata = fileSystem.metadataOrNull( dir )

    if( metadata == null )
        fileSystem.createDirectories( dir )
    else if( !metadata.isDirectory )
        throw IOException("${fileSystem.canonicalize(dir)} isn't a directory!")

    return dir
}

/**
 * @return location to store crash logs reliably
 *
 * @throws IOException if destination isn't a directory
 */
fun getCrashLogDir(): Path {
    val dir = getDataDir().resolve( "crashlogs" )
    val metadata = fileSystem.metadataOrNull( dir )

    if( metadata == null )
        fileSystem.createDirectories( dir )
    else if( !metadata.isDirectory )
        throw IOException("${fileSystem.canonicalize(dir)} isn't a directory!")

    return dir
}