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

package me.knighthat.kreate.media

import kotlin.math.PI
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.tan

/**
 * Loudness YouTube normalizes to, in LUFS. A gain is the loudness of a song minus this.
 */
const val REFERENCE_LUFS = -14.0

/**
 * Measures the integrated loudness of audio as ITU-R BS.1770 describes it, the measure behind
 * EBU R128 and YouTube's normalization.
 *
 * Feed it all of a song with [add], then read [integratedLufs].
 *
 * @param sampleRate frames per second of the audio
 * @param channels number of channels, samples are interleaved
 */
class LoudnessMeter(sampleRate: Int, private val channels: Int) {

    private val filters = Array(channels) { KWeighting(sampleRate.toDouble()) }
    private val weights = channelWeights(channels)

    // Blocks are 400 ms long and start every 100 ms, so each block is the last 4 parts of 100 ms
    private val partFrames = maxOf(sampleRate / 10, 1)
    private val partEnergy = DoubleArray(channels)
    private var framesInPart = 0
    private val recentParts = ArrayDeque<Double>(PARTS_PER_BLOCK)

    /** Mean square of each block, channels weighted and summed. */
    private val blocks = mutableListOf<Double>()

    init {
        require(sampleRate > 0) { "sampleRate must be positive" }
        require(channels > 0) { "channels must be positive" }
    }

    /**
     * Adds the first [count] interleaved [samples], each between `-1` and `1`. [count] counts
     * samples, not frames, and should be a whole number of frames.
     */
    fun add(samples: FloatArray, count: Int = samples.size) {
        var index = 0
        while (index + channels <= count) {
            for (channel in 0 until channels) {
                val filtered = filters[channel].process(samples[index + channel].toDouble())
                partEnergy[channel] += filtered * filtered
            }
            index += channels

            if (++framesInPart == partFrames) endPart()
        }
    }

    /**
     * Loudness of everything added so far in LUFS, `null` when it is silent or shorter than one
     * block of 400 ms.
     */
    fun integratedLufs(): Double? {
        // Absolute gate, then a relative gate 10 LU below the loudness of what is left
        val audible = blocks.filter { it > ABSOLUTE_GATE }
        if (audible.isEmpty()) return null
        val relativeGate = audible.average() * RELATIVE_GATE
        val kept = audible.filter { it > relativeGate }
        if (kept.isEmpty()) return null
        return loudness(kept.average())
    }

    private fun endPart() {
        var energy = 0.0
        for (channel in 0 until channels) {
            energy += weights[channel] * partEnergy[channel]
            partEnergy[channel] = 0.0
        }
        framesInPart = 0

        if (recentParts.size == PARTS_PER_BLOCK) recentParts.removeFirst()
        recentParts.addLast(energy)
        if (recentParts.size == PARTS_PER_BLOCK)
            blocks += recentParts.sum() / (PARTS_PER_BLOCK * partFrames)
    }

    /**
     * The weighting filter of BS.1770: a shelf that lifts the highs like the head does, then a
     * high-pass that drops the lows. Coefficients are worked out for the sample rate, the
     * standard only lists them for 48 kHz.
     */
    private class KWeighting(sampleRate: Double) {

        private val shelf: Biquad
        private val highPass: Biquad

        init {
            val shelfK = tan(PI * SHELF_FREQUENCY / sampleRate)
            val vh = 10.0.pow(SHELF_GAIN_DB / 20)
            val vb = vh.pow(SHELF_VB_EXPONENT)
            val shelfA0 = 1 + shelfK / SHELF_Q + shelfK * shelfK
            shelf = Biquad(
                b0 = (vh + vb * shelfK / SHELF_Q + shelfK * shelfK) / shelfA0,
                b1 = 2 * (shelfK * shelfK - vh) / shelfA0,
                b2 = (vh - vb * shelfK / SHELF_Q + shelfK * shelfK) / shelfA0,
                a1 = 2 * (shelfK * shelfK - 1) / shelfA0,
                a2 = (1 - shelfK / SHELF_Q + shelfK * shelfK) / shelfA0,
            )

            val passK = tan(PI * HIGH_PASS_FREQUENCY / sampleRate)
            val passA0 = 1 + passK / HIGH_PASS_Q + passK * passK
            highPass = Biquad(
                b0 = 1.0,
                b1 = -2.0,
                b2 = 1.0,
                a1 = 2 * (passK * passK - 1) / passA0,
                a2 = (1 - passK / HIGH_PASS_Q + passK * passK) / passA0,
            )
        }

        fun process(sample: Double): Double = highPass.process(shelf.process(sample))
    }

    /** One second order filter, transposed direct form II. */
    private class Biquad(
        private val b0: Double,
        private val b1: Double,
        private val b2: Double,
        private val a1: Double,
        private val a2: Double,
    ) {

        private var z1 = 0.0
        private var z2 = 0.0

        fun process(input: Double): Double {
            val output = b0 * input + z1
            z1 = b1 * input - a1 * output + z2
            z2 = b2 * input - a2 * output
            return output
        }
    }

    private companion object {

        const val PARTS_PER_BLOCK = 4

        const val SHELF_FREQUENCY = 1681.974450955533
        const val SHELF_GAIN_DB = 3.999843853973347
        const val SHELF_Q = 0.7071752369554196
        const val SHELF_VB_EXPONENT = 0.4996667741545416
        const val HIGH_PASS_FREQUENCY = 38.13547087602444
        const val HIGH_PASS_Q = 0.5003270373238773

        /** -70 LUFS as a mean square. */
        val ABSOLUTE_GATE = 10.0.pow((-70.0 + 0.691) / 10)

        /** -10 LU as a ratio of mean squares. */
        val RELATIVE_GATE = 10.0.pow(-10.0 / 10)

        fun loudness(meanSquare: Double): Double = -0.691 + 10 * log10(meanSquare)

        /**
         * How much each channel counts, in the order Android decodes them. Surround channels count
         * 1.41 times, the low frequency channel not at all.
         */
        fun channelWeights(channels: Int): DoubleArray = when (channels) {
            5 -> doubleArrayOf(1.0, 1.0, 1.0, 1.41, 1.41)
            6 -> doubleArrayOf(1.0, 1.0, 1.0, 0.0, 1.41, 1.41)
            else -> DoubleArray(channels) { 1.0 }
        }
    }
}
