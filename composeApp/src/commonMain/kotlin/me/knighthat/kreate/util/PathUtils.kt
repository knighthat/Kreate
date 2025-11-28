package me.knighthat.kreate.util

import java.io.File
import java.io.IOException


/**
 * @return location to store config files such as settings
 */
expect fun getConfigDir(): File

/**
 * @return location to store app's persistent
 */
expect fun getDataDir(): File

/**
 * @return location to store temporary data.
 */
expect fun getCacheDir(): File

/**
 * @return location to store logs as app runs. Can be wiped by the OS
 *
 * @throws IOException if destination isn't a directory
 */
@Throws(IOException::class)
fun getRuntimeLogDir(): File {
    val dir = getCacheDir().resolve( "logs" )

    if( !dir.exists() )
        dir.mkdirs()
    else if( !dir.isDirectory )
        throw IOException( "${dir.absolutePath} isn't a directory!" )

    return dir
}

/**
 * @return location to store crash logs reliably
 *
 * @throws IOException if destination isn't a directory
 */
@Throws(IOException::class)
fun getCrashLogDir(): File {
    val dir = getDataDir().resolve( "crashlogs" )

    if( !dir.exists() )
        dir.mkdirs()
    else if( !dir.isDirectory )
        throw IOException( "${dir.absolutePath} isn't a directory!" )

    return dir
}