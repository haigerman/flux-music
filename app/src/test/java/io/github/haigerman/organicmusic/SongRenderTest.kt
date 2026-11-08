package io.github.haigerman.organicmusic

import io.github.haigerman.organicmusic.audio.Mood
import io.github.haigerman.organicmusic.audio.MusicMixer
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Rendert jede Stimmung offline, prüft auf NaN/Übersteuerung/Stille und legt
 * WAV-Vorschauen unter app/build/sound-previews ab (zum Reinhören ohne Handy).
 *
 * Länge einstellbar: ./gradlew :app:testDebugUnitTest -PpreviewSeconds=180
 */
class SongRenderTest {
    private val sr = 44100
    private val seconds = (System.getProperty("previewSeconds") ?: "60").toInt()

    private fun render(mood: Mood, seed: Long, intensity: Float = mood.intensityDefault, bpm: Int = mood.bpmDefault): FloatArray {
        val mixer = MusicMixer(sr)
        mixer.play(mood.create(sr, seed, bpm.toFloat(), intensity))
        mixer.setMasterImmediate(1f)
        val frames = sr * seconds
        val out = FloatArray(frames * 2)
        val chunk = FloatArray(1024 * 2)
        var pos = 0
        while (pos < frames) {
            val n = minOf(1024, frames - pos)
            mixer.render(chunk, n)
            System.arraycopy(chunk, 0, out, pos * 2, n * 2)
            pos += n
        }
        return out
    }

    private fun stats(name: String, buf: FloatArray): Pair<Double, Double> {
        var sum = 0.0
        var peak = 0.0
        for (x in buf) {
            assertTrue("$name enthält NaN", !x.isNaN() && !x.isInfinite())
            sum += x * x
            peak = maxOf(peak, abs(x).toDouble())
        }
        val rms = sqrt(sum / buf.size)
        println("%-12s RMS %6.1f dBFS   Peak %6.1f dBFS".format(name, 20 * log10(rms + 1e-12), 20 * log10(peak + 1e-12)))
        return rms to peak
    }

    private fun writeWav(file: File, buf: FloatArray) {
        file.parentFile.mkdirs()
        DataOutputStream(FileOutputStream(file).buffered()).use { o ->
            fun i32(v: Int) { o.write(v and 255); o.write((v shr 8) and 255); o.write((v shr 16) and 255); o.write((v shr 24) and 255) }
            fun i16(v: Int) { o.write(v and 255); o.write((v shr 8) and 255) }
            val data = buf.size * 2
            o.writeBytes("RIFF"); i32(36 + data); o.writeBytes("WAVE")
            o.writeBytes("fmt "); i32(16); i16(1); i16(2); i32(sr); i32(sr * 4); i16(4); i16(16)
            o.writeBytes("data"); i32(data)
            for (x in buf) i16((x.coerceIn(-1f, 1f) * 32767).toInt())
        }
    }

    @Test
    fun renderEachMood() {
        val dir = File("build/sound-previews")
        for (mood in Mood.entries) {
            val buf = render(mood, seed = 4242L)
            val (rms, peak) = stats(mood.name, buf)
            assertTrue("${mood.name} ist stumm", rms > 0.005)
            assertTrue("${mood.name} übersteuert", peak <= 1.0)
            writeWav(File(dir, "${mood.id}.wav"), buf)
        }
    }

    @Test
    fun moodSwitchCrossfadesSmoothly() {
        val mixer = MusicMixer(sr)
        mixer.setMasterImmediate(1f)
        val order = listOf(Mood.FOCUS, Mood.SPORT, Mood.RELAX, Mood.FLOW, Mood.FLOW, Mood.SLEEP)
        val perMood = sr * 12
        val out = FloatArray(perMood * order.size * 2)
        val chunk = FloatArray(1024 * 2)
        var pos = 0
        for ((i, m) in order.withIndex()) {
            mixer.play(m.create(sr, 100L + i, m.bpmDefault.toFloat(), m.intensityDefault))
            // schnelles Hin- und Herschalten mitten in der Überblendung
            if (i == 2) mixer.play(Mood.FOCUS.create(sr, 5L, 82f, 0.5f))
            if (i == 2) mixer.play(m.create(sr, 6L, m.bpmDefault.toFloat(), m.intensityDefault))
            var n = 0
            while (n < perMood) {
                val k = minOf(1024, perMood - n)
                mixer.render(chunk, k)
                System.arraycopy(chunk, 0, out, pos * 2, k * 2)
                pos += k
                n += k
            }
        }
        val (rms, peak) = stats("WECHSEL", out)
        assertTrue(rms > 0.005)
        assertTrue(peak <= 1.0)
        // keine harten Sprünge (Knackser) zwischen zwei Samples
        var maxJump = 0f
        for (i in 2 until out.size step 2) maxJump = maxOf(maxJump, abs(out[i] - out[i - 2]))
        println("Größter Sprung zwischen Samples: %.3f".format(maxJump))
        assertTrue("Knackser beim Wechsel", maxJump < 0.8f)
        writeWav(File("build/sound-previews/wechsel.wav"), out)
    }

    @Test
    fun longRunOverManyPieces() {
        // 15 Minuten je Stimmung: mehrere Teile, Tonartwechsel, alle Abschnitte
        for (mood in Mood.entries) {
            val mixer = MusicMixer(sr)
            val song = mood.create(sr, 31337L, mood.bpmMax.toFloat(), 1f)
            mixer.play(song)
            mixer.setMasterImmediate(1f)
            val chunk = FloatArray(1024 * 2)
            var peak = 0f
            val keys = mutableSetOf<String>()
            val parts = mutableSetOf<String>()
            repeat(15 * 60 * sr / 1024) {
                mixer.render(chunk, 1024)
                for (x in chunk) {
                    assertTrue("${mood.name}: NaN", !x.isNaN())
                    peak = maxOf(peak, abs(x))
                }
                song.info.let { keys += it.key; parts += it.part }
            }
            println("%-6s Tonarten: %s | Teile: %s".format(mood.name, keys.filter { it.isNotEmpty() }, parts.filter { it.isNotEmpty() }))
            assertTrue(peak <= 1f)
        }
    }

    @Test
    fun extremeSettingsStayClean() {
        for (mood in Mood.entries) {
            for ((i, b) in listOf(0f to mood.bpmMin, 1f to mood.bpmMax)) {
                val buf = render(mood, seed = 7L + b, intensity = i, bpm = b)
                val (rms, peak) = stats("${mood.name}@$b/$i", buf)
                assertTrue(rms > 0.002)
                assertTrue(peak <= 1.0)
            }
        }
    }
}
