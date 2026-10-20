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

package me.knighthat.kreate3.lyrics

import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler
import java.io.StringReader
import javax.xml.parsers.SAXParserFactory
import kotlin.time.Duration

private const val TTM = "http://www.w3.org/ns/ttml#metadata"
private const val ITUNES = "http://music.apple.com/lyric-ttml-internal"

private val SPACES = Regex("""\s+""")

/**
 * Reads TTML like Apple Music's: a `<p begin end>` per line, a `<span begin end>` per word or
 * syllable, with the spaces between words as text between the spans, and backing vocals in a
 * `<span ttm:role="x-bg">` of the line. `itunes:timing` on `<tt>` says how it is timed: `Word`,
 * `Line` (the lines hold their text) or `None`.
 *
 * Backing vocals become lines of their own, placed by the time they start like the others.
 *
 * @return `null` when it has no line
 */
internal fun parseTtml(text: String): LyricsContent? {
    val factory = SAXParserFactory.newInstance().apply { isNamespaceAware = true }
    val parser = factory.newSAXParser()
    // Answers come from servers, they may not make the parser fetch anything
    runCatching {
        parser.xmlReader.setFeature("http://xml.org/sax/features/external-general-entities", false)
        parser.xmlReader.setFeature(
            "http://xml.org/sax/features/external-parameter-entities",
            false
        )
    }

    val handler = TtmlHandler()
    parser.parse(InputSource(StringReader(text)), handler)
    if (handler.lines.isEmpty()) return null

    return if (handler.timing.equals("none", ignoreCase = true))
        PlainLyrics(handler.lines.filterNot { it.background }.joinToString("\n") { it.text })
    else
        SyncedLyrics(handler.lines.withEnds())
}

private class TtmlHandler : DefaultHandler() {

    /** What a `<span>` is: a timed word, a group of backing vocals or neither. */
    private class Span(val timed: Boolean, val background: Boolean)

    /** Words of a line or of its backing vocals, and the text it has besides them. */
    private class Part {
        val words = mutableListOf<MutableWord>()
        val text = StringBuilder()
    }

    private class MutableWord(val start: Duration, val end: Duration?) {
        val text = StringBuilder()
    }

    val lines = mutableListOf<LyricsLine>()
    var timing: String? = null

    private var lineStart: Duration? = null
    private var lineEnd: Duration? = null
    private var main: Part? = null
    private var background: Part? = null
    private val spans = ArrayDeque<Span>()
    private var word: MutableWord? = null

    override fun startElement(
        uri: String,
        localName: String,
        qName: String,
        attributes: Attributes
    ) {
        when (localName.ifEmpty { qName.substringAfter(':') }) {
            "tt" -> timing = attributes.getValue(ITUNES, "timing")
            "p" -> {
                lineStart = attributes.getValue("begin")?.let(::parseClock)
                lineEnd = attributes.getValue("end")?.let(::parseClock)
                main = Part()
                background = null
            }

            "span" -> {
                if (main == null) return
                val inBackground = spans.lastOrNull()?.background == true ||
                        attributes.getValue(TTM, "role") == "x-bg"
                val start = attributes.getValue("begin")?.let(::parseClock)
                spans.addLast(Span(start != null, inBackground))
                if (start != null) {
                    val part =
                        if (inBackground) background ?: Part().also { background = it } else main!!
                    word = MutableWord(start, attributes.getValue("end")?.let(::parseClock))
                        .also { part.words += it }
                }
            }
        }
    }

    override fun characters(ch: CharArray, start: Int, length: Int) {
        val line = main ?: return
        val text = String(ch, start, length)
        word?.let {
            it.text.append(text)
            return
        }

        val part = if (spans.lastOrNull()?.background == true) background ?: line else line
        // The space between two words belongs to the first, text outside words is a line's own
        if (text.isBlank() && part.words.isNotEmpty())
            part.words.last().text.append(' ')
        else
            part.text.append(text)
    }

    override fun endElement(uri: String, localName: String, qName: String) {
        when (localName.ifEmpty { qName.substringAfter(':') }) {
            "span" -> if (main != null && spans.removeLastOrNull()?.timed == true) word = null
            "p" -> {
                main?.toLine(lineStart, lineEnd, background = false)?.let { lines += it }
                background?.toLine(null, null, background = true)?.let { lines += it }
                main = null
                background = null
                spans.clear()
                word = null
            }
        }
    }

    private fun Part.toLine(start: Duration?, end: Duration?, background: Boolean): LyricsLine? {
        val timed = words.mapIndexedNotNull { i, word ->
            var spoken = word.text.toString().replace(SPACES, " ")
            if (i == 0) spoken = spoken.trimStart()
            spoken.takeIf { it.isNotEmpty() }?.let { LyricsWord(word.start, word.end, it) }
        }
        val lineText = if (timed.isEmpty())
            text.toString().replace(SPACES, " ").trim()
        else
            timed.joinToString("") { it.text }.trim()
        val lineStart = start ?: timed.firstOrNull()?.start ?: return null
        val lineEnd = end ?: timed.lastOrNull()?.end
        if (lineText.isEmpty() && background) return null
        return LyricsLine(lineStart, lineEnd, lineText, timed, background)
    }
}
