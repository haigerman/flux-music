package io.github.haigerman.organicmusic

import io.github.haigerman.organicmusic.audio.Style
import io.github.haigerman.organicmusic.audio.MusicMixer
import org.junit.Test

class PerformanceTest {
    @Test
    fun realtimeFactorPerMood() {
        val sr = 48000
        for (mood in Style.entries) {
            val mixer = MusicMixer(sr)
            mixer.play(mood.create(sr, 99L, mood.bpmDefault.toFloat(), 1f))
            mixer.setMasterImmediate(1f)
            val buf = FloatArray(2048)
            repeat(400) { mixer.render(buf, 1024) } // Aufwärmen
            val seconds = 60
            val t0 = System.nanoTime()
            repeat(seconds * sr / 1024) { mixer.render(buf, 1024) }
            val ms = (System.nanoTime() - t0) / 1e6
            println("%-12s %ds Audio in %4.0f ms → %3.0fx Echtzeit".format(mood.name, seconds, ms, seconds * 1000 / ms))
        }
    }
}
