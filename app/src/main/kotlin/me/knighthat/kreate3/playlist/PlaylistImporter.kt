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

package me.knighthat.kreate3.playlist

import android.content.Context
import android.content.res.Resources
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.net.toUri
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import me.knighthat.kreate3.R
import me.knighthat.kreate3.database.model.Album
import me.knighthat.kreate3.database.model.Artist
import me.knighthat.kreate3.database.model.Song
import me.knighthat.kreate3.database.repository.ImportedCredits
import me.knighthat.kreate3.database.repository.PlaylistImportRepository
import me.knighthat.kreate3.database.repository.RemoteCatalogRepository
import me.knighthat.kreate3.database.repository.RemoteSong
import me.knighthat.kreate3.database.repository.SongReplacement
import me.knighthat.kreate3.model.Origin
import me.knighthat.kreate3.notification.AppChannel
import me.knighthat.kreate3.notification.Message
import me.knighthat.kreate3.notification.MessageDuration
import me.knighthat.kreate3.provider.opensubsonic.OpenSubsonicProvider
import me.knighthat.kreate3.provider.youtube.SongLookup
import me.knighthat.kreate3.provider.youtube.YouTubeException
import me.knighthat.kreate3.provider.youtube.YouTubeProvider
import me.knighthat.kreate3.source.youtube.BatchSearches
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * A song to put in an imported playlist, as a file has it.
 *
 * @property id the song's id at its [origin], empty when the file has none
 * @property origin where the song is from, `null` when the file names a source this app doesn't
 * know
 * @property title the song's title, `null` when the file has none
 * @property artists the file's text for its artists, `null` when it has none
 * @property duration its length, `null` when unknown
 * @property thumbnail address of its cover, `null` when the file has none
 * @property artistIds ids of the credited artists at the song's origin, in the order of the names
 * in [artists]; empty when the file has none
 * @property albumId id of its album at the song's origin, `null` when the file has none
 * @property albumName `null` when the file has none
 * @property albumYear `null` when unknown
 * @property albumPosition its place on the album, `null` when unknown
 * @property fromOldApp whether the file is the old Kreate app's, which doesn't name sources
 */
data class PlaylistSong(
    val id: String,
    val origin: Origin?,
    val title: String? = null,
    val artists: String? = null,
    val duration: Duration? = null,
    val thumbnail: String? = null,
    val artistIds: List<String> = emptyList(),
    val albumId: String? = null,
    val albumName: String? = null,
    val albumYear: Int? = null,
    val albumPosition: Int? = null,
    val fromOldApp: Boolean = false,
)

/** A playlist to make out of a file: its [name] and its [songs] in order. */
data class PlaylistToImport(val name: String, val songs: List<PlaylistSong>)

/** What an import of a playlist file is doing, in order. */
enum class ImportStep {
    /** Reading the file. */
    READING,

    /** Waiting for the user to say whether to look its YouTube songs up again. */
    WAITING,

    /** Looking its YouTube songs up again. */
    LOOKING_UP,

    /** Searching for the new uploads of YouTube songs the lookup didn't know. */
    SEARCHING,

    /** Storing the songs and making the playlist. */
    SAVING,
}

/**
 * A playlist of a file being imported that isn't made yet, see [PlaylistImporter.importing].
 *
 * @property key tells it from the others, also from one with the same [name]; a new placeholder
 * has a larger one than any before it
 * @property name the name the file gives it; the file's own name while it is read
 * @property step what its import is doing
 */
data class ImportingPlaylist(val key: Long, val name: String, val step: ImportStep)

/** Why a song of a file wasn't imported. */
enum class ImportSkip {
    /** The file gives no id. */
    NO_ID,

    /** The file names no source, or one this app doesn't know. */
    UNKNOWN_SOURCE,

    /** A device song the library doesn't have. */
    NOT_ON_DEVICE,

    /** An OpenSubsonic song not stored, whose cover isn't of the server set now. */
    NOT_ON_SERVER,

    /** Its id is stored for a song of another source. */
    ID_TAKEN,

    /** It is in the same playlist already. */
    DUPLICATE,

    /**
     * A YouTube song looked up again that YouTube's answer left out, with no song found published
     * in its place.
     */
    GONE,
}

/**
 * What an import did.
 *
 * @property rows how many songs the file listed
 * @property playlists how many playlists were made
 * @property imported how many songs went into them
 * @property skipped how many songs were left out, for each reason
 * @property notLookedUp why the YouTube songs the user asked to look up went in as the file has
 * them instead, `null` when they were looked up or not asked to be
 */
data class PlaylistImportReport(
    val rows: Int,
    val playlists: Int,
    val imported: Int,
    val skipped: Map<ImportSkip, Int>,
    val notLookedUp: NotLookedUp? = null,
)

/** Why a lookup the user asked for didn't happen, see [PlaylistImporter.importPlaylists]. */
enum class NotLookedUp {
    /** YouTube refused it, which one removed or private song does. */
    REFUSED,

    /** YouTube couldn't be reached, like when the connection dropped. */
    UNREACHABLE,
}

/**
 * Makes the active user's local playlists out of playlist files:
 *
 * - YouTube songs are stored from the file's title, length and cover, like a live song added
 *   to a playlist, with the file's album and artists when it has their ids (this app's files do,
 *   the old app's don't: their artists come with the song's next fetch). One stored already is
 *   kept as it is.
 * - Or, when the user asks to look them up again and YouTube answers, its answer decides (see
 *   [lookUpYouTube]): a song it plays goes in with its current details, one it doesn't play as
 *   the file has it. One missing from its answer is searched for as the song YouTube
 *   published in its place, like a song that no longer plays (see [replacementOf]): found, it
 *   goes in instead and takes the old song's stored rows; else it is left out.
 *   Songs already stored keep their rows.
 * - Device songs go in when the library has them: the same id with the same title, else the one
 *   song of the device with that exact title (ends trimmed), the closest in length when several
 *   have it. Others are skipped, the scan stores device songs. A song of the old app's file goes
 *   in only by its id (with the same title when the file has one).
 * - OpenSubsonic songs go in when stored, or when their cover is an address of the server set
 *   now, which shows they are from that server; they are stored from the file then, album and
 *   artists like YouTube songs.
 * - A song in the same playlist twice goes in once. A playlist with nothing to hold isn't made.
 *
 * Every playlist made is new, also when one has the same name.
 */
class PlaylistImporter(
    private val context: Context,
    private val repository: PlaylistImportRepository,
    private val openSubsonic: OpenSubsonicProvider,
    private val youTube: YouTubeProvider,
    private val catalog: RemoteCatalogRepository,
    private val searches: BatchSearches,
) {

    private val nextKey = AtomicLong()
    private val _importing = MutableStateFlow<List<ImportingPlaylist>>(emptyList())

    /**
     * The playlists of the imports going on that aren't made yet, the latest import's first: one
     * named after the file while it is read ([startImport]), then one per playlist in the order of
     * the file. One leaves once it is made or left out, all of them when the import ends.
     */
    val importing: StateFlow<List<ImportingPlaylist>> = _importing.asStateFlow()

    /**
     * Starts showing the import of the file at [uri] in [importing], named after the file, at
     * [ImportStep.READING]. Close the task when the import ends, however it ends.
     */
    suspend fun startImport(uri: Uri): ImportTask {
        val name = withContext(Dispatchers.IO) { fileName(uri) }
            ?: context.getString(R.string.status_unknown_file)
        return ImportTask(_importing, nextKey, listOf(name), ImportStep.READING)
    }

    /**
     * The playlists of the CSV file at [uri], see [parsePlaylistCsv]. Songs without a playlist
     * name go to one named after the file.
     *
     * @throws InvalidHeaderException when the file isn't a playlist CSV
     * @throws java.io.IOException when the file can't be read
     */
    suspend fun readCsv(uri: Uri): List<PlaylistToImport> = withContext(Dispatchers.IO) {
        val rows = context.contentResolver.openInputStream(uri)
            ?.bufferedReader()
            ?.use(::parsePlaylistCsv)
            ?: throw java.io.FileNotFoundException("Can't open $uri")
        groupPlaylistRows(rows, fileName(uri))
    }

    /**
     * Looks the YouTube songs of [playlists] up again: in requests of up to a thousand, each sent
     * once more with a visitor id when it had none, one refused refusing all (see
     * [YouTubeProvider.lookUpSongs]). Every song YouTube plays is stored like a played one, a song
     * already stored keeps its row. A song YouTube doesn't play (taken down or published again) is
     * left out of the answer and goes in as the file has it: searching for its new upload is put
     * off. A song missing from YouTube's answer is searched for with [replacementOf], paced by
     * [BatchSearches].
     *
     * @return the song each YouTube id goes in as, `null` for one left out; `null` when YouTube
     * refused the lookup (nothing is stored then)
     * @throws me.knighthat.kreate3.provider.youtube.YouTubeException when YouTube can't be reached
     */
    private suspend fun lookUpYouTube(
        playlists: List<PlaylistToImport>,
        task: ImportTask,
    ): Map<String, RemoteSong?>? {
        val songs = playlists.asSequence()
            .flatMap { it.songs }
            .filter { it.origin == Origin.YOUTUBE && it.id.isNotEmpty() }
            .distinctBy { it.id }
            .toList()
        if (songs.isEmpty()) return emptyMap()

        task.step(ImportStep.LOOKING_UP)
        val lookups = youTube.lookUpSongs(songs.map { it.id }) ?: run {
            Logger.w(tag = TAG) { "YouTube refused to look up ${songs.size} songs, they go in as the file has them" }
            return null
        }
        val found = songs.mapNotNull { song ->
            when (val lookup = lookups[song.id]) {
                is SongLookup.Found -> song.id to lookup.song
                SongLookup.Unplayable -> null
                null -> song.id to null
            }
        }.toMap(HashMap())
        Logger.i(tag = TAG) {
            "Looked up ${songs.size} YouTube songs: ${found.count { it.value != null }} play, " +
                    "${songs.size - found.size} don't, ${found.count { it.value == null }} unknown"
        }

        val missing = songs.filter { it.id in found && found[it.id] == null }
        if (missing.isNotEmpty()) task.step(ImportStep.SEARCHING)
        // One search each, paced: there can be thousands
        searches.searchAll(missing, ::replacementOf).forEachIndexed { index, inPlace ->
            val id = missing[index].id
            when (inPlace) {
                null -> {}
                InPlace.Downloaded -> found.remove(id)
                is InPlace.Replaced -> found[id] = inPlace.song
            }
        }

        found.values.filterNotNull().distinctBy { it.song.id }.forEach { remote ->
            if (!catalog.addToLibrary(remote.song.id)) catalog.savePlayedSong(remote)
        }
        return found
    }

    /**
     * The song YouTube published in place of [song], which its lookup left out, found with
     * [YouTubeProvider.replacementOf] from the file's title and length (artists only search and
     * break ties), the search a song that no longer plays gets. A song without a title or length
     * can't be matched. A replacement
     * found takes what the library keeps of [song] (`RemoteCatalogRepository.replaceSong`), like
     * on play; a failure to move it is logged and the replacement still goes in.
     *
     * @return `null` when none is found or YouTube can't be reached for it
     */
    private suspend fun replacementOf(song: PlaylistSong): InPlace? {
        val title = song.title
        val duration = song.duration
        if (title == null || duration == null) {
            Logger.i(tag = TAG) {
                "${song.id} has no ${if (title == null) "title" else "length"} to match, it's gone"
            }
            return null
        }
        val found = try {
            youTube.replacementOf(
                songId = song.id,
                title = title,
                artists = song.artists,
                artistIds = song.artistIds,
                duration = duration,
            )
        } catch (e: YouTubeException) {
            Logger.w(e, TAG) { "Couldn't search for the song in place of ${song.id}" }
            null
        } ?: return null

        val newId = found.song.song.id
        try {
            when (catalog.replaceSong(song.id, found.song)) {
                SongReplacement.MOVED ->
                    Logger.i(tag = TAG) { "What was kept of ${song.id} is $newId's now" }

                SongReplacement.DOWNLOADED -> {
                    Logger.i(tag = TAG) { "${song.id} is downloaded, it stays in place of $newId" }
                    return InPlace.Downloaded
                }

                SongReplacement.NOT_STORED ->
                    Logger.i(tag = TAG) { "${song.id} isn't stored, $newId goes in in its place" }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.w(e, TAG) { "Couldn't move what was kept of ${song.id} to $newId" }
        }
        return InPlace.Replaced(found.song)
    }

    /** What [replacementOf] found for a song its lookup left out. */
    private sealed interface InPlace {

        /** [song] is published in its place and goes in instead. */
        data class Replaced(val song: RemoteSong) : InPlace

        /** A user has it downloaded: it is kept and goes in as it is. */
        data object Downloaded : InPlace
    }

    /**
     * Makes a local playlist of each of [playlists]. The entry point for every kind of playlist
     * file: read it into [PlaylistToImport]s, then call this. Until each is made or left out, it
     * is in [importing], through [task], which this closes.
     *
     * @param lookUp whether to look the YouTube songs up again first, see [lookUpYouTube]. When
     * YouTube refuses it or can't be reached, the songs go in as the file has them and the report
     * says why ([PlaylistImportReport.notLookedUp]).
     */
    suspend fun importPlaylists(
        playlists: List<PlaylistToImport>,
        lookUp: Boolean = false,
        task: ImportTask = ImportTask(
            _importing,
            nextKey,
            playlists.map { it.name },
            ImportStep.SAVING
        ),
    ): PlaylistImportReport = task.use {
        task.playlists(playlists.map { it.name })
        makePlaylists(playlists, lookUp, task)
    }

    /** [importPlaylists], telling [task] what it does and each playlist made or left out. */
    private suspend fun makePlaylists(
        playlists: List<PlaylistToImport>,
        lookUp: Boolean,
        task: ImportTask,
    ): PlaylistImportReport {
        val skipped = sortedMapOf<ImportSkip, Int>()
        fun skip(reason: ImportSkip) = skipped.merge(reason, 1, Int::plus)
        var notLookedUp: NotLookedUp? = null
        val looked = if (!lookUp)
            null
        else try {
            lookUpYouTube(playlists, task) ?: null.also { notLookedUp = NotLookedUp.REFUSED }
        } catch (e: YouTubeException) {
            // Refusals come back as null, only an unreachable YouTube throws
            Logger.w(
                e,
                TAG
            ) { "Couldn't reach YouTube to look songs up, they go in as the file has them" }
            notLookedUp = NotLookedUp.UNREACHABLE
            null
        }

        task.step(ImportStep.SAVING)
        val origins = playlists.flatMap { it.songs }.mapNotNullTo(HashSet()) { it.origin }
        val local =
            if (Origin.LOCAL in origins) repository.storedSongs(Origin.LOCAL) else emptyList()
        val openSubsonicIds = if (Origin.OPEN_SUBSONIC in origins)
            repository.storedSongs(Origin.OPEN_SUBSONIC).mapTo(HashSet()) { it.id }
        else
            emptySet()

        val localMatcher = withContext(Dispatchers.Default) { LocalMatcher(local) }
        var made = 0
        var imported = 0
        for ((index, playlist) in playlists.withIndex()) {
            val credits = HashMap<String, ImportedCredits>()
            val songs = withContext(Dispatchers.Default) {
                val picked = LinkedHashMap<String, Song>()
                for (song in playlist.songs) {
                    val origin = song.origin
                    val kept = when {
                        song.id.isEmpty() -> null.also { skip(ImportSkip.NO_ID) }
                        origin == null -> null.also { skip(ImportSkip.UNKNOWN_SOURCE) }
                        origin == Origin.YOUTUBE && looked != null && song.id in looked ->
                            looked[song.id]?.song ?: null.also { skip(ImportSkip.GONE) }

                        origin == Origin.LOCAL -> (if (song.fromOldApp) localMatcher.matchId(song) else localMatcher.match(
                            song
                        ))
                            ?: null.also { skip(ImportSkip.NOT_ON_DEVICE) }

                        origin == Origin.OPEN_SUBSONIC &&
                                song.id !in openSubsonicIds &&
                                song.thumbnail?.toUri()
                                    ?.let(openSubsonic::isCoverOfServer) != true ->
                            null.also { skip(ImportSkip.NOT_ON_SERVER) }

                        else -> song.toModel(origin).also { model ->
                            song.credits(origin)?.let { credits[model.id] = it }
                        }
                    } ?: continue

                    if (kept.id in picked)
                        skip(ImportSkip.DUPLICATE)
                    else
                        picked[kept.id] = kept
                }
                picked.values.toList()
            }
            if (songs.isEmpty()) {
                task.done(index)
                continue
            }

            val result = repository.importPlaylist(playlist.name, songs, credits)
            task.done(index)
            val byId = songs.associateBy { it.id }
            result.leftOut.forEach {
                skip(if (byId[it]?.origin == Origin.LOCAL) ImportSkip.NOT_ON_DEVICE else ImportSkip.ID_TAKEN)
            }
            if (result.playlistId != null) made++
            imported += result.added
        }

        val rows = playlists.sumOf { it.songs.size }
        Logger.i(tag = TAG) { "Imported $imported of $rows songs into $made playlists, skipped $skipped" }
        return PlaylistImportReport(rows, made, imported, skipped, notLookedUp)
    }

    /** The file's name without its extension, `null` when the provider doesn't say. */
    private fun fileName(uri: Uri): String? =
        context.contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
            }
            ?.substringBeforeLast('.')
            ?.trim()
            ?.takeIf(String::isNotEmpty)

    private fun PlaylistSong.toModel(origin: Origin) = Song(
        id = id,
        origin = origin,
        name = title,
        thumbnail = thumbnail?.toUri(),
        duration = duration,
        albumPosition = albumPosition,
    )

    /**
     * The album and artists the file gives the song, `null` for none. The artists' names pair
     * with their ids by place; when their counts differ (a name holding the separator) the
     * artists are stored without names, their next fetch names them.
     */
    private fun PlaylistSong.credits(origin: Origin): ImportedCredits? {
        val album = albumId?.let { Album(it, origin, name = albumName, year = albumYear) }
        val names = artists?.split(PlaylistCsvColumns.ARTIST_SEPARATOR)?.map(String::trim)
        val paired = names?.size == artistIds.size
        val credited = artistIds.mapIndexed { index, artistId ->
            Artist(
                artistId,
                origin,
                name = if (paired) names[index].takeIf(String::isNotEmpty) else null,
            )
        }
        return if (album == null && credited.isEmpty())
            null
        else
            ImportedCredits(album, credited)
    }

    private companion object {
        const val TAG = "PlaylistImport"
    }
}

/**
 * The placeholders of one import in [PlaylistImporter.importing]: one for the file while it is
 * read, then one per playlist ([playlists]). Each leaves once its playlist is made or left out
 * ([done]), the rest on [close]. Called from one coroutine at a time.
 */
class ImportTask internal constructor(
    private val importing: MutableStateFlow<List<ImportingPlaylist>>,
    private val nextKey: AtomicLong,
    names: List<String>,
    step: ImportStep,
) : AutoCloseable {

    @Volatile
    private var keys: List<Long> = emptyList()

    @Volatile
    private var names: List<String> = names

    init {
        val entries = names.map { ImportingPlaylist(nextKey.getAndIncrement(), it, step) }
        keys = entries.map { it.key }
        importing.update { entries + it }
    }

    /**
     * Shows [names], one placeholder each in the order of the file, in place of the ones
     * shown, at the step of the first. The first keeps its key, the others are new. Nothing
     * changes when they are shown already.
     */
    fun playlists(names: List<String>) {
        if (names == this.names) return
        this.names = names
        val old = keys
        val newKeys = names.indices.map { i ->
            if (i == 0 && old.isNotEmpty()) old[0] else nextKey.getAndIncrement()
        }
        keys = newKeys
        importing.update { list ->
            val at = list.indexOfFirst { it.key in old }
            val step = list.getOrNull(at)?.step ?: ImportStep.SAVING
            val rest = list.filter { it.key !in old }
            val entries = names.mapIndexed { i, name -> ImportingPlaylist(newKeys[i], name, step) }
            if (at < 0)
                entries + rest
            else
                rest.take(at) + entries + rest.drop(at)
        }
    }

    /** Shows [step] on every placeholder still shown. */
    fun step(step: ImportStep) {
        val current = keys
        importing.update { list ->
            list.map { if (it.key in current) it.copy(step = step) else it }
        }
    }

    /** Takes away the placeholder of the playlist at [index], it was made or left out. */
    fun done(index: Int) {
        val key = keys.getOrNull(index) ?: return
        importing.update { list -> list.filter { it.key != key } }
    }

    /** Takes away the placeholders still shown. */
    override fun close() {
        val current = keys
        importing.update { list -> list.filter { it.key !in current } }
    }
}

/** Finds a file's device song among the device songs of the library, see [PlaylistImporter]. */
internal class LocalMatcher(songs: List<Song>) {

    private val byId = songs.associateBy { it.id }
    private val byTitle = songs.filter { it.name != null }.groupBy { it.name!!.trim() }

    /** The library's device song with [song]'s id, when the file has no title or the same one. */
    fun matchId(song: PlaylistSong): Song? {
        val title = song.title?.trim()
        return byId[song.id]?.takeIf { title == null || it.name?.trim() == title }
    }

    /** [matchId], else the song with the same title, see [PlaylistImporter]. */
    fun match(song: PlaylistSong): Song? {
        matchId(song)?.let { return it }
        val title = song.title?.trim() ?: return null

        val candidates = byTitle[title] ?: return null
        if (candidates.size == 1) return candidates.single()

        val length = song.duration ?: return null
        return candidates
            .filter { it.duration != null }
            .minByOrNull { (it.duration!! - length).absoluteValue }
            ?.takeIf { (it.duration!! - length).absoluteValue <= MATCH_LENGTH_SLACK }
    }

    private companion object {
        /** Lengths are whole seconds in files, and tags and the media index may differ a little. */
        val MATCH_LENGTH_SLACK = 2.seconds
    }
}

/**
 * What to tell the user about [report]: how many songs went into how many playlists, then a line
 * for each reason songs were skipped.
 */
fun importMessage(resources: Resources, report: PlaylistImportReport): Message {
    val lines = ArrayList<String>()
    lines += when {
        report.rows == 0 -> resources.getString(R.string.snackbar_playlist_import_empty)
        report.imported == 0 -> resources.getString(R.string.snackbar_playlist_import_nothing)
        else -> resources.getString(
            R.string.snackbar_playlists_imported,
            resources.getQuantityString(R.plurals.song, report.imported, report.imported),
            resources.getQuantityString(R.plurals.playlist, report.playlists, report.playlists),
        )
    }
    report.notLookedUp?.let { reason ->
        lines += resources.getString(
            when (reason) {
                NotLookedUp.REFUSED -> R.string.snackbar_playlist_import_not_looked_up
                NotLookedUp.UNREACHABLE -> R.string.snackbar_playlist_import_not_looked_up_unreachable
            },
            resources.getString(R.string.source_ytm),
        )
    }
    report.skipped.forEach { (reason, count) ->
        lines += resources.getString(
            R.string.snackbar_playlist_import_skipped,
            resources.getQuantityString(R.plurals.song, count, count),
            reason.text(resources),
        )
    }
    return Message(
        text = lines.joinToString("\n"),
        duration = MessageDuration.LONG,
        dismissable = true,
        channel = AppChannel.OTHER,
    )
}

/** What to tell the user when an import failed with [error]. */
fun importFailedMessage(resources: Resources, error: Throwable): Message {
    // Which columns are wrong only helps a bug report, the log has them
    val text = if (error is InvalidHeaderException)
        resources.getString(R.string.error_import_playlists_bad_columns)
    else
        resources.getString(
            R.string.error_import_playlists,
            error.localizedMessage ?: resources.getString(R.string.error_reason_unknown),
        )
    return Message(
        text = text,
        duration = MessageDuration.LONG,
        dismissable = true,
        channel = AppChannel.OTHER,
    )
}

private fun ImportSkip.text(resources: Resources): String = when (this) {
    ImportSkip.NO_ID -> resources.getString(R.string.error_reason_import_no_id)
    ImportSkip.UNKNOWN_SOURCE -> resources.getString(R.string.error_reason_import_unknown_source)
    ImportSkip.NOT_ON_DEVICE -> resources.getString(R.string.error_reason_import_not_on_device)
    ImportSkip.NOT_ON_SERVER -> resources.getString(
        R.string.error_reason_import_not_on_server,
        resources.getString(R.string.source_opensubsonic),
    )

    ImportSkip.ID_TAKEN -> resources.getString(R.string.error_reason_import_id_taken)
    ImportSkip.DUPLICATE -> resources.getString(R.string.error_reason_import_duplicate)
    ImportSkip.GONE -> resources.getString(
        R.string.error_reason_import_gone,
        resources.getString(R.string.source_ytm),
    )
}
