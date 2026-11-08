package io.github.haigerman.organicmusic

import io.github.haigerman.organicmusic.audio.MusicMixer
import io.github.haigerman.organicmusic.audio.Style
import io.github.haigerman.organicmusic.audio.dsp.OnePole
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Prüft den Regler "Variabilität": Bei hoher Variabilität müssen sich Takte weniger
 * gleichen als bei 0 – gemessen als Ähnlichkeit jedes Takts mit dem Takt 4 Takte später
 * (Rhythmus × Klangfarbe in drei Frequenzbändern), gemittelt über zwei Zufallsverläufe.
 */
class VariationTest {
    private val sr = 44100
    private val seconds = 240

    private fun render(style: Style, variation: Float, seed: Long): FloatArray {
        val mixer = MusicMixer(sr)
        mixer.play(style.create(sr, seed, style.bpmDefault.toFloat(), style.intensityDefault, TestSamples, variation))
        mixer.setMasterImmediate(1f)
        val frames = sr * seconds
        val out = FloatArray(frames)
        val chunk = FloatArray(1024 * 2)
        var pos = 0
        while (pos < frames) {
            val n = minOf(1024, frames - pos)
            mixer.render(chunk, n)
            for (i in 0 until n) {
                val l = chunk[i * 2]
                val r = chunk[i * 2 + 1]
                assertTrue("NaN bei Variabilität $variation", !l.isNaN() && !r.isNaN())
                assertTrue("Übersteuert bei Variabilität $variation", abs(l) <= 1f && abs(r) <= 1f)
                out[pos + i] = (l + r) * 0.5f
            }
            pos += n
        }
        return out
    }

    /** Mittlere Ähnlichkeit (Kosinus) zwischen Takt n und Takt n+4. */
    private fun repetition(x: FloatArray, bpm: Int): Double {
        val step = sr * 15.0 / bpm
        val lowF = OnePole(sr, 200f)
        val midF = OnePole(sr, 2000f)
        val steps = (x.size / step).toInt()
        val feat = Array(steps) { DoubleArray(3) }
        var s = 0
        var next = step
        for (i in x.indices) {
            if (i >= next) {
                s++
                next += step
                if (s >= steps) break
            }
            val low = lowF.lp(x[i])
            val mid = midF.lp(x[i]) - low
            val high = x[i] - low - mid
            feat[s][0] += (low * low).toDouble()
            feat[s][1] += (mid * mid).toDouble()
            feat[s][2] += (high * high).toDouble()
        }
        val bars = steps / 16
        fun bar(b: Int) = DoubleArray(48) { k -> sqrt(feat[b * 16 + k / 3][k % 3]) }
        var sum = 0.0
        var n = 0
        for (b in 4 until bars - 4) { // ohne Anfang
            val a = bar(b)
            val c = bar(b + 4)
            var dot = 0.0
            var na = 0.0
            var nc = 0.0
            for (k in a.indices) {
                dot += a[k] * c[k]; na += a[k] * a[k]; nc += c[k] * c[k]
            }
            if (na > 0 && nc > 0) {
                sum += dot / sqrt(na * nc)
                n++
            }
        }
        return sum / n
    }

    @Test
    fun higherVariationIsLessRepetitive() {
        var better = 0
        for (style in Style.entries) {
            // über zwei Zufallsverläufe gemittelt
            val r0 = (repetition(render(style, 0f, 11L), style.bpmDefault) + repetition(render(style, 0f, 23L), style.bpmDefault)) / 2
            val r1 = (repetition(render(style, 1f, 11L), style.bpmDefault) + repetition(render(style, 1f, 23L), style.bpmDefault)) / 2
            println("%-12s Wiederholung bei Variabilität 0: %.3f   bei 1: %.3f".format(style.name, r0, r1))
            if (r1 < r0) better++
        }
        assertTrue("Variabilität wirkt in zu wenigen Stilen ($better von ${Style.entries.size})", better >= Style.entries.size - 1)
    }
}
