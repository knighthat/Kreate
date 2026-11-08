/*
 * Kreate
 * Copyright (C) 2026 KnightHat
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package me.knighthat.kreate3.font

import android.content.Context
import android.graphics.Typeface
import android.os.Build
import me.knighthat.kreate3.preferences.Profile
import java.io.File
import java.io.IOException
import java.io.InputStream

/** What a font is used for, each has its own font a profile can pick. */
enum class FontSlot(internal val prefix: String) {
    /** The texts of the whole app. */
    APP("app"),

    /** The lyrics, the app's font while they have none. */
    LYRICS("lyrics"),
}

/**
 * A font a profile keeps, as its preference stores it: `<id>:<name>`. The [id] makes the file's
 * name new each time, so a font that replaces another is never drawn from a cache of the earlier
 * one, and so a preference that is set again always changes.
 *
 * @param name how the font is shown, the name of the file it came from
 */
data class SavedFont(val id: Long, val name: String) {

    /** What the preference stores. */
    val value: String get() = "$id:$name"

    companion object {

        /** The font [value] stands for, `null` for an empty or unreadable one. */
        fun parse(value: String): SavedFont? {
            val id = value.substringBefore(':', "").toLongOrNull() ?: return null
            return SavedFont(id, value.substringAfter(':'))
        }
    }
}

/**
 * Fonts the user picked, kept as files in their profile's folder under `noBackupFilesDir`
 * (`profiles/<profile id>/fonts/<slot>-<id>.font`): they go with the profile and are left out of
 * the system's and the cloud's backups; the zip a user exports has them ([backupFiles]).
 *
 * A font is [stage]d first, to look at it, and only [keep]ing it makes it the profile's.
 */
class FontFiles(context: Context) {

    private val root = context.noBackupFilesDir

    /** Where fonts wait to be looked at, emptied by every [stage]. */
    private val staging = File(context.cacheDir, "fonts")

    /** Where every profile's folder is, see [Profile.directory]. */
    private val profiles = Profile.directory(root, Profile.DEFAULT_ID).parentFile

    /** The fonts of the profile with [profileId]. */
    fun directory(profileId: String): File = File(Profile.directory(root, profileId), DIRECTORY)

    /** The file of [font], the one [slot] keeps in the profile with [profileId]. */
    fun file(profileId: String, slot: FontSlot, font: SavedFont): File =
        File(directory(profileId), "${slot.prefix}-${font.id}.font")

    /**
     * Copies the font in [input] to a file of its own, which stays until the next call, and
     * checks that it is one.
     *
     * @throws IOException when it can't be read
     * @throws IllegalArgumentException when it is no font, or too big for one
     */
    fun stage(input: InputStream): File {
        staging.deleteRecursively()
        staging.mkdirs()
        val file = File(staging, "picked.font")
        try {
            file.outputStream().use { out ->
                val copied = input.use { it.copyTo(LimitedOutput(out, MAX_SIZE)) }
                require(copied > 0) { "Empty file" }
            }
            require(Sfnt.isFont(file)) { "Not a font" }
            require(loads(file)) { "The system can't load the font" }
        } catch (e: Exception) {
            file.delete()
            throw e
        }
        return file
    }

    /**
     * Makes the [staged] font of [slot] the one of the profile with [profileId], the earlier one
     * is deleted, and so is [staged].
     *
     * @param name how the font is shown
     * @return what to store in the profile's preference
     */
    fun keep(profileId: String, slot: FontSlot, staged: File, name: String): SavedFont {
        val font = SavedFont(System.currentTimeMillis(), name)
        val target = file(profileId, slot, font)
        target.parentFile?.mkdirs()
        try {
            staged.copyTo(target, overwrite = true)
        } catch (e: Exception) {
            target.delete()
            throw e
        }
        staged.delete()
        forget(profileId, slot, except = target)
        return font
    }

    /** Deletes the font of [slot] of the profile with [profileId], other than [except]. */
    fun forget(profileId: String, slot: FontSlot, except: File? = null) {
        directory(profileId).listFiles().orEmpty()
            .filter { it != except && it.name.startsWith("${slot.prefix}-") }
            .forEach(File::delete)
    }

    /**
     * The files of every profile's fonts by their paths in a backup. A picked font can't be found
     * again.
     */
    fun backupFiles(): Map<String, File> =
        profiles?.listFiles().orEmpty().flatMap { profile ->
            File(profile, DIRECTORY).listFiles().orEmpty().filter(File::isFile)
        }.associateBy { it.relativeTo(root).invariantSeparatorsPath }
            .filterKeys(::isBackupPath)

    /**
     * Replaces the fonts of every profile with the ones in [source], laid out by their paths in a
     * backup. A restore replaces everything, the fonts of what was there are gone with it.
     */
    fun restore(source: File) {
        profiles?.listFiles().orEmpty().forEach { File(it, DIRECTORY).deleteRecursively() }
        source.walkTopDown()
            .filter(File::isFile)
            .forEach { file ->
                val path = file.relativeTo(source).invariantSeparatorsPath
                if (isBackupPath(path)) file.copyTo(File(root, path), overwrite = true)
            }
    }

    /** Whether the system loads [file] as a font, which a check of its header can't tell. */
    private fun loads(file: File): Boolean = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            Typeface.Builder(file).build() != null
        else
            Typeface.createFromFile(file) != null
    } catch (_: RuntimeException) {
        false
    }

    companion object {

        /** In each profile's folder. */
        private const val DIRECTORY = "fonts"

        /** The biggest font taken, CJK fonts reach tens of MiB. */
        const val MAX_SIZE = 100L * 1024 * 1024

        /** The path of a font in a backup: `profiles/<profile id>/fonts/<slot>-<id>.font`. */
        private val BACKUP_PATH = Regex(
            "profiles/[A-Za-z0-9_-]+/$DIRECTORY/(app|lyrics)-[0-9]+\\.font"
        )

        /** Whether [path] is a path [backupFiles] can return. */
        fun isBackupPath(path: String): Boolean = BACKUP_PATH.matches(path)
    }
}

/** Writes to [out], refusing more than [limit] bytes. */
private class LimitedOutput(
    private val out: java.io.OutputStream,
    private val limit: Long,
) : java.io.OutputStream() {

    private var written = 0L

    override fun write(b: Int) {
        count(1)
        out.write(b)
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        count(len)
        out.write(b, off, len)
    }

    private fun count(bytes: Int) {
        written += bytes
        require(written <= limit) { "Too big for a font" }
    }
}
