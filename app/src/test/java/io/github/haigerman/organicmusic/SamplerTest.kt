package io.github.haigerman.organicmusic

import io.github.haigerman.organicmusic.audio.dsp.Rng
import io.github.haigerman.organicmusic.audio.instruments.Sampler
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Prüft die Sample-Instrumente: richtige Tonhöhe über den ganzen Umfang, nahtlose Loops,
 * keine Knackser. Legt außerdem eine "Instrumentenschau" als WAV ab
 * (app/build/sound-previews/instrumente.wav).
 */
class SamplerTest {
    private val sr = 48000

    private fun render(s: Sampler, seconds: Float): FloatArray {
        val n = (seconds * sr).toInt()
        val l = FloatArray(n)
        val r = FloatArray(n)
        val bl = FloatArray(512)
        val br = FloatArray(512)
        var pos = 0
        while (pos < n) {
            val k = minOf(512, n - pos)
            java.util.Arrays.fill(bl, 0f)
            java.util.Arrays.fill(br, 0f)
            s.render(bl, br, k)
            for (i in 0 until k) {
                l[pos + i] = bl[i]
                r[pos + i] = br[i]
            }
            pos += k
        }
        return FloatArray(n) { (l[it] + r[it]) * 0.5f }
    }

    /** Tonhöhe (MIDI) über die Obertonreihe nahe [expected] – wie im Build-Skript. */
    private fun measure(x: FloatArray, expected: Double): Double? {
        val start = (0.08 * sr).toInt()
        val len = 1 shl 15
        if (x.size < start + len) return null
        val n = 1 shl 17
        val re = DoubleArray(n)
        val im = DoubleArray(n)
        for (i in 0 until len) re[i] = x[start + i] * (0.5 - 0.5 * kotlin.math.cos(2 * Math.PI * i / len))
        fft(re, im)
        val mag = DoubleArray(n / 2) { sqrt(re[it] * re[it] + im[it] * im[it]) }
        val ref = mag.max()
        fun at(f: Double): Double {
            if (f <= 20 || f >= sr * 0.45) return 0.0
            val i = f / sr * n
            val w = maxOf(3.0, i * 0.006)
            var m = 0.0
            for (k in (i - w).toInt()..(i + w).toInt()) if (k in mag.indices) m = maxOf(m, mag[k])
            return m
        }
        fun hz(m: Double) = 440.0 * Math.pow(2.0, (m - 69) / 12)
        var chosen: Double? = null
        for (c in doubleArrayOf(expected - 12, expected, expected + 12)) {
            val f = hz(c)
            val odd = listOf(1, 3, 5, 7).sumOf { at(f * it) }
            val even = listOf(2, 4, 6, 8).sumOf { at(f * it) }
            if (odd > 0.12 * even && odd > 0.05 * ref) {
                chosen = c
                break
            }
        }
        val c0 = chosen ?: return null
        var best = -1.0
        var bestC = c0
        var cents = -60
        while (cents <= 60) {
            val f = hz(c0 + cents / 100.0)
            val sc = (1..6).sumOf { at(f * it) / it }
            if (sc > best) {
                best = sc
                bestC = c0 + cents / 100.0
            }
            cents += 2
        }
        return bestC
    }

    private fun fft(re: DoubleArray, im: DoubleArray) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                var t = re[i]; re[i] = re[j]; re[j] = t
                t = im[i]; im[i] = im[j]; im[j] = t
            }
        }
        var len = 2
        while (len <= n) {
            val ang = -2 * Math.PI / len
            val wr = kotlin.math.cos(ang)
            val wi = kotlin.math.sin(ang)
            var i = 0
            while (i < n) {
                var cr = 1.0
                var ci = 0.0
                for (k in 0 until len / 2) {
                    val ar = re[i + k]
                    val ai = im[i + k]
                    val br = re[i + k + len / 2] * cr - im[i + k + len / 2] * ci
                    val bi = re[i + k + len / 2] * ci + im[i + k + len / 2] * cr
                    re[i + k] = ar + br
                    im[i + k] = ai + bi
                    re[i + k + len / 2] = ar - br
                    im[i + k + len / 2] = ai - bi
                    val ncr = cr * wr - ci * wi
                    ci = cr * wi + ci * wr
                    cr = ncr
                }
                i += len
            }
            len = len shl 1
        }
    }

    private val ranges = mapOf(
        "piano" to (33..96), "wurli" to (40..84), "upright" to (28..55), "ebass" to (28..60),
        "guitar" to (40..84), "violins" to (55..88), "violas" to (48..81), "cellos" to (36..72),
        "contrabass" to (28..55), "harp" to (36..96), "marimba" to (48..90), "flute" to (60..93),
    )

    @Test
    fun pitchIsCorrectAcrossRange() {
        val problems = ArrayList<String>()
        var checked = 0
        for ((id, range) in ranges) {
            val data = TestSamples.load(id)
            for (key in range step 5) {
                val s = Sampler(sr, Rng(key.toLong()), data, 4)
                s.noteOn(key, 0.7f, (1.0f * sr).toInt())
                val x = render(s, 1.0f)
                val m = measure(x, key.toDouble()) ?: continue
                checked++
                val cents = (m - key) * 100
                if (abs(cents) > 35) problems += "$id Taste $key: ${"%+.0f".format(cents)} Cent"
            }
        }
        println("Tonhöhe geprüft: $checked Töne, Abweichungen > 35 Cent: ${problems.size}")
        problems.forEach { println("  $it") }
        assertTrue("Falsche Tonhöhen:\n" + problems.joinToString("\n"), problems.size <= checked / 20)
    }

    @Test
    fun loopsSustainSeamlessly() {
        for (id in listOf("violins", "violas", "cellos", "contrabass", "flute")) {
            val data = TestSamples.load(id)
            val s = Sampler(sr, Rng(1), data, 4).apply { attack = 0.3f }
            val key = data.zones[data.zones.size / 2].key
            s.noteOn(key, 0.8f, 20 * sr)
            val x = render(s, 20f)
            fun rms(a: Int, b: Int): Double = sqrt((a until b).sumOf { x[it].toDouble() * x[it] } / (b - a))
            val early = rms(2 * sr, 5 * sr)
            val late = rms(15 * sr, 19 * sr)
            var jump = 0f
            for (i in 1 until x.size) jump = maxOf(jump, abs(x[i] - x[i - 1]))
            println("%-10s Taste %d: früh %.4f spät %.4f (%.1f dB) größter Sprung %.3f".format(id, key, early, late, 20 * kotlin.math.log10(late / early), jump))
            assertTrue("$id: Ton bricht ab (Loop greift nicht)", late > early * 0.35)
            assertTrue("$id: Knackser", jump < 0.25f)
        }
    }

    @Test
    fun decoderOffsetIsDetected() {
        // Der Klick im Vorspann muss gefunden werden – auch wenn ein Decoder Frames verschiebt
        for (id in listOf("piano", "drums", "ebass")) {
            val d = TestSamples.load(id)
            val sync = io.github.haigerman.organicmusic.audio.samples.SampleData.parseHeader(
                File("src/main/assets/instruments/$id/inst.txt").readText(),
            ).sync
            val pcm = d.pcm
            assertTrue("$id: kein Sync-Klick im Vorspann", sync >= 0)
            assertTrue("$id: Versatz bei exakter Dekodierung", io.github.haigerman.organicmusic.audio.samples.SampleData.syncShift(pcm, d.channels, sync) == 0)
            for (shift in intArrayOf(37, -21)) {
                val arr = ShortArray(20000 * d.channels)
                for (f in 0 until 20000) for (c in 0 until d.channels) {
                    val src = f - shift
                    arr[f * d.channels + c] = if (src in 0 until d.frames) pcm.get(src * d.channels + c) else 0
                }
                val found = io.github.haigerman.organicmusic.audio.samples.SampleData.syncShift(java.nio.ShortBuffer.wrap(arr), d.channels, sync)
                assertTrue("$id: Versatz $shift erkannt als $found", found == shift)
            }
        }
    }

    @Test
    fun instrumentTour() {
        // jedes Instrument spielt eine kurze Tonleiter – zum Probehören
        val out = ArrayList<FloatArray>()
        for ((id, range) in ranges) {
            val data = TestSamples.load(id)
            val s = Sampler(sr, Rng(5), data, 16)
            val mid = (range.first + range.last) / 2 - 7
            val scale = intArrayOf(0, 2, 4, 5, 7, 9, 11, 12)
            for ((i, st) in scale.withIndex()) s.noteOn(mid + st, 0.4f + 0.06f * i, (0.35f * sr).toInt(), (i * 0.4f * sr).toInt())
            // Akkord zum Schluss
            for ((i, st) in intArrayOf(0, 4, 7, 12).withIndex()) s.noteOn(mid + st, 0.6f, (1.6f * sr).toInt(), ((3.4f + i * 0.02f) * sr).toInt())
            out += render(s, 6f)
        }
        for (id in listOf("drums", "drums_lofi", "perc", "claps")) {
            val data = TestSamples.load(id)
            val s = Sampler(sr, Rng(5), data, 16)
            val keys = data.zones.map { it.key }.distinct()
            for ((i, k) in keys.withIndex()) s.noteOn(k, 0.8f, -1, (i * 0.3f * sr).toInt())
            out += render(s, keys.size * 0.3f + 2f)
        }
        val all = out.flatMap { it.asList() }
        val f = File("build/sound-previews/instrumente.wav")
        f.parentFile.mkdirs()
        DataOutputStream(FileOutputStream(f).buffered()).use { o ->
            fun i32(v: Int) { o.write(v and 255); o.write((v shr 8) and 255); o.write((v shr 16) and 255); o.write((v shr 24) and 255) }
            fun i16(v: Int) { o.write(v and 255); o.write((v shr 8) and 255) }
            val data = all.size * 2
            o.writeBytes("RIFF"); i32(36 + data); o.writeBytes("WAVE")
            o.writeBytes("fmt "); i32(16); i16(1); i16(1); i32(sr); i32(sr * 2); i16(2); i16(16)
            o.writeBytes("data"); i32(data)
            for (x in all) i16((x.coerceIn(-1f, 1f) * 32767).toInt())
        }
        println("Instrumentenschau: ${all.size / sr} s → ${f.path}")
        assertTrue(all.any { abs(it) > 0.01f })

    }
}
