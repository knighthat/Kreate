package it.fast4x.rimusic.utils

import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import app.kreate.android.R
import co.touchlab.kermit.Logger
import it.fast4x.rimusic.enums.LogType
import me.knighthat.utils.Toaster
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

@RequiresApi(Build.VERSION_CODES.O)
fun moveDir(src: Path, dest: Path): Boolean {
    if (src.toFile().isDirectory) {
        for (file in src.toFile().listFiles()!!) {
            moveDir(file.toPath(), dest.resolve(src.relativize(file.toPath())))
        }
    }
    return try {
        Files.move(src, dest, StandardCopyOption.REPLACE_EXISTING)
        true
    } catch (e: IOException) {
        Logger.e("", e, "FileUtils" )
        false
    }
}

@RequiresApi(Build.VERSION_CODES.O)
fun copyDir(src: Path, dest: Path) {
    val sources = Files.walk(src).toList()
    for (source in sources) {
        Files.copy(source, dest.resolve(src.relativize(source)),
            StandardCopyOption.REPLACE_EXISTING
        )
    }
}

fun saveImageToInternalStorage(context: Context, imageUri: Uri, dirPath: String, thumbnailName: String): Uri? {
    try {
        // Open input stream from the URI
        val inputStream: InputStream? = context.contentResolver.openInputStream(imageUri)

        // Create a new file in the app's internal storage
        if ( !createDirIfNotExists(context, dirPath)) {
            Logger.e( tag = "FileUtils" ) { "Failed to create directory: $dirPath" }
            return null
        }
        val outputFile = File(context.filesDir, "$dirPath/$thumbnailName")

        val outputStream = FileOutputStream(outputFile)

        // Copy the data from the input stream to the output stream (internal storage)
        inputStream?.copyTo(outputStream)

        // Close the streams
        inputStream?.close()
        outputStream.flush()
        outputStream.close()

        // Return the URI to the saved file in internal storage
        return Uri.fromFile(outputFile)
    } catch (e: IOException) {
        Logger.e("", e, "FileUtils" )
        return null
    }
}

fun checkFileExists(context: Context, filePath: String): String? {
    val file = File(context.filesDir, filePath)

    return if (file.exists()) {
        file.toURI().toString()
    } else {
        null
    }
}

fun deleteFileIfExists(context: Context, filePath: String): Boolean {
    val file = File(context.filesDir, filePath)

    return if (file.exists()) {
        file.delete()
    } else {
        false
    }
}

fun createDirIfNotExists(context: Context, dirPath: String): Boolean {
    val directory = File(context.filesDir, dirPath)

    return if (!directory.exists()) {
        directory.mkdirs()
    } else {
        true
    }
}

/*
fun tryMoveDir() {
    val from = File("/path/to/src")
    val to = File("/path/to/dest")
    val success = moveDir(from.toPath(), to.toPath())
    if (success) {
        println("File Moved Successfully")
    } else {
        println("File Moved Failed")
    }
}
 */

/*
fun tryCopyDir() {
    val from = File("/var/kotlin/")
    val to = File("/var/bak/kotlin/")
    try {
        copyDir(from.toPath(), to.toPath())
        println("Copying succeeded.")
    } catch (ex: IOException) {
        ex.printStackTrace()
    }
}
 */

fun loadAppLog(context: Context, type: LogType): String? {
    val file = File(context.filesDir.resolve("logs"),
        when (type) {
            LogType.Default ->  "RiMusic_log.txt"
            LogType.Crash ->    "RiMusic_crash_log.txt"
        }
    )
    if (file.exists()) {
        Toaster.s( R.string.value_copied )
        return file.readText()
    } else
        Toaster.w( R.string.no_log_available )
    return null
}

fun saveFileToInternalStorage(context: Context, fileName: String, fileContent: String) {
    try {
        val file = File(context.filesDir.resolve("logs"), fileName)
        file.writeText(fileContent)
    } catch (e: IOException) {
        Logger.e( e, "FileUtils" ) { "Failed to save file $fileName to internal storage" }
    }
}