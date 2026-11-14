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

// The algorithm is adapted from Metrolist's ShazamSignatureGenerator (GPL-3.0,
// https://github.com/MetrolistGroup/Metrolist), itself a port of SongRec
// (GPL-3.0, https://github.com/marin-m/SongRec), which worked out Shazam's format

package me.knighthat.kreate3.shazam

import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import kotlin.io.encoding.Base64
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sin

/** The rate Shazam's signatures are made at, in samples per second. */
internal const val SIGNATURE_RATE = 16_000

/**
 * What Shazam looks a song up by: the loudest frequency peaks of the sound over time, in four bands,
 * packed in Shazam's binary format.
 *
 * @param uri the signature as Shazam takes it, a `data:audio/vnd.shazam.sig;base64,…` address
 * @param sampleMillis how much sound it was made from
 * @param peaks how many peaks were found, none means silence or noise
 */
internal class Signature(val uri: String, val sampleMillis: Long, val peaks: Int) {

    companion object {

        /** Makes the signature of [pcm], mono 16-bit samples at [SIGNATURE_RATE]. */
        fun of(pcm: ShortArray): Signature {
            val generator = Generator()
            generator.process(pcm)
            return Signature(
                uri = "data:audio/vnd.shazam.sig;base64," + Base64.encode(generator.encode()),
                sampleMillis = pcm.size * 1000L / SIGNATURE_RATE,
                peaks = generator.totalPeaks,
            )
        }
    }
}

private const val FFT_SIZE = 2048
private const val FFT_BINS = FFT_SIZE / 2 + 1
private const val STEP = 128
private const val RING_SIZE = 256

/** Enough peaks for a signature; past [MAX_SECONDS] with this many, the rest of the sound is left out. */
private const val MAX_PEAKS = 255
private const val MAX_SECONDS = 12

/** The quietest a peak can be. */
private const val MIN_PEAK = 1.0 / 64

private val HANNING = DoubleArray(FFT_SIZE) { 0.5 * (1 - cos(2 * PI * (it + 1) / (FFT_SIZE + 1))) }

/** Earlier spectra a new one spreads its values into. */
private val TIME_SPREAD = intArrayOf(-1, -3, -6)

/** Bins next to a candidate it must beat, in the spread spectrum. */
private val NEIGHBOR_BINS = intArrayOf(-10, -7, -4, -3, 1, 2, 5, 8)

/** Spectra around a candidate it must beat, one bin lower. */
private val NEIGHBOR_SPECTRA =
    intArrayOf(-53, -45, 165, 172, 179, 186, 193, 200, 214, 221, 228, 235, 242, 249)

/** A peak: the step it was found at, its loudness on Shazam's scale and its bin × 64. */
private class Peak(val pass: Int, val magnitude: Int, val bin: Int)

/** Turns sound into peaks, [STEP] samples at a time, see [Signature]. */
private class Generator {

    private val samples = IntArray(FFT_SIZE)
    private var samplesPos = 0
    private var numSamples = 0

    private val real = DoubleArray(FFT_SIZE)
    private val imaginary = DoubleArray(FFT_SIZE)
    private val spectra = Array(RING_SIZE) { DoubleArray(FFT_BINS) }
    private var spectraPos = 0
    private val spread = Array(RING_SIZE) { DoubleArray(FFT_BINS) }
    private var spreadPos = 0
    private var spreadWritten = 0

    private val bands = Array(4) { mutableListOf<Peak>() }
    var totalPeaks = 0
        private set

    fun process(pcm: ShortArray) {
        var offset = 0
        while (offset + STEP <= pcm.size) {
            if (numSamples >= MAX_SECONDS * SIGNATURE_RATE && totalPeaks >= MAX_PEAKS) break
            for (i in offset until offset + STEP) {
                samples[samplesPos] = pcm[i].toInt()
                samplesPos = (samplesPos + 1) % FFT_SIZE
            }
            numSamples += STEP
            spectrum()
            spreadAndFindPeaks()
            offset += STEP
        }
    }

    /** The spectrum of the newest [FFT_SIZE] samples, windowed. */
    private fun spectrum() {
        for (i in 0 until FFT_SIZE)
            real[i] = samples[(samplesPos + i) % FFT_SIZE] * HANNING[i]
        imaginary.fill(0.0)
        fft(real, imaginary)
        val out = spectra[spectraPos]
        for (i in 0 until FFT_BINS)
            out[i] = max((real[i] * real[i] + imaginary[i] * imaginary[i]) / (1 shl 17), 1e-10)
        spectraPos = (spectraPos + 1) % RING_SIZE
    }

    private fun spreadAndFindPeaks() {
        val current = spread[spreadPos]
        spectra[ring(spectraPos - 1)].copyInto(current)
        for (bin in 0 until FFT_BINS - 2)
            current[bin] = maxOf(current[bin], current[bin + 1], current[bin + 2])
        for (bin in 0 until FFT_BINS) {
            var highest = current[bin]
            for (offset in TIME_SPREAD) {
                val earlier = spread[ring(spreadPos + offset)]
                if (earlier[bin] > highest) highest = earlier[bin]
                earlier[bin] = highest
            }
        }
        spreadPos = (spreadPos + 1) % RING_SIZE
        spreadWritten++
        if (spreadWritten < 47) return

        // Far enough back for the spectra after it to be known
        val spectrum = spectra[ring(spectraPos - 46)]
        val spreadSpectrum = spread[ring(spreadPos - 49)]
        for (bin in 10 until FFT_BINS - 8) {
            val value = spectrum[bin]
            if (value < MIN_PEAK || value < spreadSpectrum[bin]) continue

            var highest = 0.0
            for (offset in NEIGHBOR_BINS) highest = max(highest, spreadSpectrum[bin + offset])
            if (value <= highest) continue
            for (offset in NEIGHBOR_SPECTRA)
                highest = max(highest, spread[ring(spreadPos + offset)][bin - 1])
            if (value <= highest) continue

            val magnitude = magnitude(value)
            val before = magnitude(spectrum[bin - 1])
            val after = magnitude(spectrum[bin + 1])
            // Between the bins, where the curve through the three tops out
            val corrected = bin * 64.0 + (after - before) * 32 / (magnitude * 2 - before - after)
            val hertz = corrected * (SIGNATURE_RATE / 2.0 / 1024 / 64)
            val band = when {
                hertz < 250 -> continue
                hertz < 520 -> 0
                hertz < 1450 -> 1
                hertz < 3500 -> 2
                hertz <= 5500 -> 3
                else -> continue
            }
            bands[band] += Peak(spreadWritten - 46, magnitude.toInt(), corrected.toInt())
            totalPeaks++
        }
    }

    /** Shazam's binary signature of the peaks found so far, its checksum filled in. */
    fun encode(): ByteArray {
        val contents = ByteArrayOutputStream()
        bands.forEachIndexed { band, peaks ->
            if (peaks.isEmpty()) return@forEachIndexed
            val data = ByteArrayOutputStream()
            var previous = 0
            for (peak in peaks) {
                // A gap a byte can't hold starts over from the peak's own step
                if (peak.pass - previous >= 255) {
                    data.write(0xFF)
                    data.le32(peak.pass)
                    previous = peak.pass
                }
                data.write(peak.pass - previous)
                data.le16(peak.magnitude)
                data.le16(peak.bin)
                previous = peak.pass
            }
            contents.le32(0x60030040 + band)
            contents.le32(data.size())
            data.writeTo(contents)
            repeat((4 - data.size() % 4) % 4) { contents.write(0) }
        }

        val out = ByteArrayOutputStream()
        out.le32(0xCAFE2580.toInt())
        out.le32(0) // Checksum, filled in below
        out.le32(contents.size() + 8)
        out.le32(0x94119C00.toInt())
        repeat(3) { out.le32(0) }
        out.le32(3 shl 27) // 16 kHz
        repeat(2) { out.le32(0) }
        out.le32(numSamples + (SIGNATURE_RATE * 0.24).toInt())
        out.le32((15 shl 19) + 0x40000)
        out.le32(0x40000000)
        out.le32(contents.size() + 8)
        contents.writeTo(out)

        val bytes = out.toByteArray()
        val crc = CRC32().apply { update(bytes, 8, bytes.size - 8) }.value.toInt()
        for (i in 0 until 4) bytes[4 + i] = (crc ushr (8 * i)).toByte()
        return bytes
    }

    private fun ring(index: Int): Int = ((index % RING_SIZE) + RING_SIZE) % RING_SIZE

    private fun magnitude(value: Double): Double = ln(max(MIN_PEAK, value)) * 1477.3 + 6144
}

private fun ByteArrayOutputStream.le16(value: Int) {
    write(value and 0xFF)
    write((value ushr 8) and 0xFF)
}

private fun ByteArrayOutputStream.le32(value: Int) {
    le16(value)
    le16(value ushr 16)
}

/** In-place radix-2 FFT of [real] + i·[imaginary], whose size is a power of 2. */
private fun fft(real: DoubleArray, imaginary: DoubleArray) {
    val n = real.size
    var j = 0
    for (i in 1 until n) {
        var bit = n ushr 1
        while (j and bit != 0) {
            j = j xor bit
            bit = bit ushr 1
        }
        j = j xor bit
        if (i < j) {
            real[i] = real[j].also { real[j] = real[i] }
            imaginary[i] = imaginary[j].also { imaginary[j] = imaginary[i] }
        }
    }
    var length = 2
    while (length <= n) {
        val half = length / 2
        val angle = -PI / half
        val stepReal = cos(angle)
        val stepImaginary = sin(angle)
        var start = 0
        while (start < n) {
            var wReal = 1.0
            var wImaginary = 0.0
            for (k in 0 until half) {
                val u = start + k
                val v = u + half
                val oddReal = real[v] * wReal - imaginary[v] * wImaginary
                val oddImaginary = real[v] * wImaginary + imaginary[v] * wReal
                real[v] = real[u] - oddReal
                imaginary[v] = imaginary[u] - oddImaginary
                real[u] += oddReal
                imaginary[u] += oddImaginary
                val nextReal = wReal * stepReal - wImaginary * stepImaginary
                wImaginary = wReal * stepImaginary + wImaginary * stepReal
                wReal = nextReal
            }
            start += length
        }
        length *= 2
    }
}

/**
 * [pcm] at [from] samples per second, brought to [SIGNATURE_RATE] by linear interpolation. Shazam
 * keeps only peaks up to 5.5 kHz, so what this blurs above 8 kHz doesn't matter.
 */
internal fun resample(pcm: ShortArray, from: Int): ShortArray {
    if (from == SIGNATURE_RATE || pcm.isEmpty()) return pcm
    val ratio = from.toDouble() / SIGNATURE_RATE
    return ShortArray((pcm.size / ratio).toInt()) { i ->
        val source = i * ratio
        val base = source.toInt().coerceAtMost(pcm.lastIndex)
        val next = (base + 1).coerceAtMost(pcm.lastIndex)
        val fraction = source - base
        (pcm[base] + (pcm[next] - pcm[base]) * fraction).toInt().toShort()
    }
}
