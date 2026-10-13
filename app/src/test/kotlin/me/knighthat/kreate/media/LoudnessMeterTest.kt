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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class LoudnessMeterTest {

    /** A stereo sine of [seconds], the same in both channels. */
    private fun sine(sampleRate: Int, frequency: Double, amplitude: Double, seconds: Int) =
        FloatArray(sampleRate * seconds * 2) {
            val frame = it / 2
            (amplitude * sin(2 * PI * frequency * frame / sampleRate)).toFloat()
        }

    private fun measure(samples: FloatArray, sampleRate: Int, channels: Int = 2): Double? =
        LoudnessMeter(sampleRate, channels).apply { add(samples) }.integratedLufs()

    // EBU Tech 3341: a 1 kHz sine at -23 dBFS in both channels reads -23 LUFS
    @Test
    fun sineAtMinus23DbfsReadsMinus23Lufs() {
        val amplitude = Math.pow(10.0, -23.0 / 20)
        assertEquals(-23.0, measure(sine(48_000, 1000.0, amplitude, 10), 48_000)!!, 0.1)
    }

    @Test
    fun otherSampleRatesReadTheSame() {
        val amplitude = Math.pow(10.0, -20.0 / 20)
        assertEquals(-20.0, measure(sine(44_100, 1000.0, amplitude, 10), 44_100)!!, 0.1)
    }

    @Test
    fun quietPartsDontPullTheResultDown() {
        val amplitude = Math.pow(10.0, -20.0 / 20)
        val loud = sine(48_000, 1000.0, amplitude, 10)
        val quiet = sine(48_000, 1000.0, amplitude / 1000, 10)
        assertEquals(-20.0, measure(loud + quiet, 48_000)!!, 0.2)
    }

    @Test
    fun silenceHasNoLoudness() {
        assertNull(measure(FloatArray(48_000 * 2 * 5), 48_000))
    }

    @Test
    fun audioShorterThanOneBlockHasNoLoudness() {
        assertNull(measure(sine(48_000, 1000.0, 0.5, 1).copyOf(48_000 / 5 * 2), 48_000))
    }
}
