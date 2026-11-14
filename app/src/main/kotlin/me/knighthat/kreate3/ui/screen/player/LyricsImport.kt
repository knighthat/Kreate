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

package me.knighthat.kreate3.ui.screen.player

import android.content.ContentResolver
import android.content.res.Resources
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.OpenDocument
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.media3.common.MediaItem
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.knighthat.kreate3.R
import me.knighthat.kreate3.database.model.Lyrics
import me.knighthat.kreate3.database.repository.SongRepository
import me.knighthat.kreate3.lyrics.parseLyrics
import me.knighthat.kreate3.model.LyricsProvider
import me.knighthat.kreate3.model.LyricsSync
import me.knighthat.kreate3.notification.Message
import me.knighthat.kreate3.notification.MessageDuration
import me.knighthat.kreate3.notification.Notifier
import me.knighthat.kreate3.playback.songTitle
import me.knighthat.kreate3.ui.screen.detail.itemName
import org.koin.compose.koinInject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.util.Locale

private const val TAG = "LyricsImport"

/** The largest file read as lyrics: TTML of a long song timed per syllable is a few hundred KB. */
internal const val MAX_LYRICS_FILE_BYTES = 2 * 1024 * 1024

/** Every file shows in the picker: LRC files have no type of their own, most apps give none. */
private val LYRICS_FILE_TYPES = arrayOf("*/*")

/** Why a file can't be lyrics, with what the user is told. */
internal enum class LyricsFileProblem(@get:StringRes val messageRes: Int) {
    UNREADABLE(R.string.error_lyrics_file_unreadable),
    TOO_LARGE(R.string.error_lyrics_file_too_large),
    NOT_TEXT(R.string.error_lyrics_file_not_text),
    EMPTY(R.string.error_lyrics_file_empty),
}

internal class LyricsFileException(val problem: LyricsFileProblem) : Exception(problem.name)

/**
 * The text of a lyrics file. A byte order mark tells UTF-8 and UTF-16; without one the text is
 * UTF-8 when it reads as such, else in the usual legacy encoding of the device's language (old LRC
 * files often are, like GBK or Windows-1252), see [legacyCharset]. The mark isn't part of the
 * text.
 *
 * @throws LyricsFileException [LyricsFileProblem.NOT_TEXT] for a file that isn't text, like a song
 * or a picture picked by mistake
 */
internal fun decodeLyricsFile(bytes: ByteArray, locale: Locale = Locale.getDefault()): String {
    val text = when {
        bytes.startsWith(0xEF, 0xBB, 0xBF) -> String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        bytes.startsWith(0xFF, 0xFE) -> String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
        bytes.startsWith(0xFE, 0xFF) -> String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
        else -> strictly(bytes, Charsets.UTF_8)
            ?: strictly(bytes, legacyCharset(locale))
            ?: String(bytes, WESTERN)
    }
    // Text never has NUL characters, other files are full of them
    if ('\u0000' in text) throw LyricsFileException(LyricsFileProblem.NOT_TEXT)
    return text
}

private fun ByteArray.startsWith(vararg mark: Int): Boolean =
    size >= mark.size && mark.indices.all { this[it] == mark[it].toByte() }

/** Windows' encoding of Western languages, it reads any byte. */
private val WESTERN: Charset = Charset.forName("windows-1252")

/**
 * The encoding text in the language of [locale] was saved in before UTF-8, by Windows mostly.
 * Text can't tell its encoding, guessing from the bytes alone mixes up Chinese, Japanese and
 * Korean.
 */
internal fun legacyCharset(locale: Locale): Charset {
    val name = when (locale.language) {
        "zh" -> if (locale.script == "Hant" || locale.country in setOf(
                "TW",
                "HK",
                "MO"
            )
        ) "Big5" else "GB18030"

        "ja" -> "Shift_JIS"
        "ko" -> "EUC-KR"
        "ru", "uk", "be", "bg", "sr", "mk" -> "windows-1251"
        "pl", "cs", "sk", "hu", "ro", "hr", "sl" -> "windows-1250"
        "el" -> "windows-1253"
        "tr" -> "windows-1254"
        "vi" -> "windows-1258"
        else -> null
    }
    return name?.let { runCatching { Charset.forName(it) }.getOrNull() } ?: WESTERN
}

/** [bytes] read as [charset], `null` when they aren't text in it. */
private fun strictly(bytes: ByteArray, charset: Charset): String? = try {
    charset.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(bytes))
        .toString()
} catch (_: CharacterCodingException) {
    null
}

/**
 * Reads [bytes] of a file as lyrics.
 *
 * @return the text as it is in the file, without its outer blank lines, and how finely it is
 * timed: plain text, LRC (per line), enhanced LRC and TTML (per word) are told apart like the
 * lyrics of sources, see [parseLyrics]
 * @throws LyricsFileException when it isn't text or has no words
 */
internal suspend fun readLyricsFile(bytes: ByteArray): Pair<String, LyricsSync> {
    val text = withContext(Dispatchers.Default) { decodeLyricsFile(bytes).trim() }
    val content = parseLyrics(text)
    if (content.text.isBlank()) throw LyricsFileException(LyricsFileProblem.EMPTY)
    return text to content.sync
}

/**
 * At most [MAX_LYRICS_FILE_BYTES] of [input].
 *
 * @throws LyricsFileException [LyricsFileProblem.TOO_LARGE] when it has more
 */
internal fun readBounded(input: InputStream): ByteArray {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(8 * 1024)
    while (true) {
        val read = input.read(buffer)
        if (read < 0) break
        if (out.size() + read > MAX_LYRICS_FILE_BYTES)
            throw LyricsFileException(LyricsFileProblem.TOO_LARGE)
        out.write(buffer, 0, read)
    }
    return out.toByteArray()
}

/**
 * Stores the lyrics in the file at [uri] as [songId]'s from [LyricsProvider.FILE], replacing the
 * ones imported before. The file's name is their credit, shown under them.
 *
 * @return how finely they are timed
 * @throws LyricsFileException when the file can't be read as lyrics
 */
internal suspend fun importLyricsFile(
    resolver: ContentResolver,
    uri: Uri,
    songId: String,
    songs: SongRepository,
): LyricsSync {
    val (name, bytes) = withContext(Dispatchers.IO) {
        val bytes = try {
            resolver.openInputStream(uri)?.use(::readBounded)
        } catch (e: LyricsFileException) {
            throw e
        } catch (e: Exception) {
            Logger.w(e, TAG) { "Couldn't read the lyrics file" }
            null
        } ?: throw LyricsFileException(LyricsFileProblem.UNREADABLE)
        displayName(resolver, uri) to bytes
    }
    val (text, sync) = readLyricsFile(bytes)
    songs.saveLyrics(Lyrics(songId, text, sync, LyricsProvider.FILE, name))
    return sync
}

/** The file's name as the app that gave it says, `null` when it can't be asked. */
private fun displayName(resolver: ContentResolver, uri: Uri): String? = try {
    resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
} catch (e: Exception) {
    null
}

/**
 * Picking a file to import as [item]'s lyrics, see [importLyricsFile]. Once stored they show on
 * the page in place of the ones of any source, [controls] is told to load them. The user is told
 * how it went.
 *
 * @param scope stores the lyrics, the page may be gone by then
 * @return opens the file picker
 */
@Composable
internal fun rememberLyricsImport(
    item: MediaItem,
    controls: LyricsControls,
    songs: SongRepository = koinInject(),
    notifier: Notifier = koinInject(),
    scope: CoroutineScope = koinInject(),
): () -> Unit {
    val resolver = LocalContext.current.contentResolver
    val resources = LocalResources.current
    val launcher = rememberLauncherForActivityResult(OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val songName = resources.itemName(item.songTitle(), R.string.snackbar_this_song)
        scope.launch {
            val message = try {
                importLyricsFile(resolver, uri, item.mediaId, songs)
                withContext(Dispatchers.Main) {
                    // The file's lyrics, even when another source was picked from the bar
                    controls.request = null
                    controls.sync = null
                    controls.reload()
                }
                Message(resources.getString(R.string.snackbar_lyrics_imported, songName))
            } catch (e: CancellationException) {
                throw e
            } catch (e: LyricsFileException) {
                Message(resources.getString(e.problem.messageRes), MessageDuration.LONG)
            } catch (e: Exception) {
                // Like a song that isn't stored, its lyrics have nothing to belong to
                Logger.w(e, TAG) { "Couldn't store the lyrics imported for ${item.mediaId}" }
                Message(resources.getString(R.string.error_lyrics_import), MessageDuration.LONG)
            }
            // The file picker just closed, the screen may not show messages yet
            notifier.awaitScreen()
            notifier.post(message)
        }
    }
    return { launcher.launch(LYRICS_FILE_TYPES) }
}

/**
 * Removes the lyrics imported for [item], the page goes back to the ones of the sources. The user
 * is told how it went.
 */
internal fun removeImportedLyrics(
    item: MediaItem,
    controls: LyricsControls,
    resources: Resources,
    songs: SongRepository,
    notifier: Notifier,
    scope: CoroutineScope,
) {
    val songName = resources.itemName(item.songTitle(), R.string.snackbar_this_song)
    scope.launch {
        val message = try {
            songs.deleteLyrics(item.mediaId, LyricsProvider.FILE)
            withContext(Dispatchers.Main) {
                if (controls.request?.provider == LyricsProvider.FILE) controls.request = null
                controls.reload()
            }
            resources.getString(R.string.snackbar_imported_lyrics_removed, songName)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.w(e, TAG) { "Couldn't remove the lyrics imported for ${item.mediaId}" }
            resources.getString(R.string.error_remove_imported_lyrics)
        }
        notifier.post(message)
    }
}
