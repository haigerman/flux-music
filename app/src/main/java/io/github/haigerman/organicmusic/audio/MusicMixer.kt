package io.github.haigerman.organicmusic.audio

import io.github.haigerman.organicmusic.audio.songs.Song
import io.github.haigerman.organicmusic.audio.songs.SongInfo
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.sin
import kotlin.math.tanh

/**
 * Spielt das aktuelle Stück und blendet bei einem Wechsel (andere Stimmung oder
 * "Neues Stück") weich über. Am Ende sitzt ein sanfter Limiter. Rein in Kotlin, ohne
 * Android – so lässt es sich auch in Unit-Tests offline rendern.
 */
class MusicMixer(private val sampleRate: Int, private val blockSize: Int = 1024) {
    private val pending = AtomicReference<Song?>(null)
    private var current: Song? = null
    private var currentGain = 1f

    /** Stücke, die gerade ausgeblendet werden (Gain fällt auf 0). */
    private class Fading(val song: Song, var gain: Float)
    private val fading = ArrayList<Fading>(4)

    @Volatile
    var masterTarget = 1f
    private var master = 0f

    var masterRampSeconds = 2.5f
    var crossfadeSeconds = 4f

    private val curL = FloatArray(blockSize)
    private val curR = FloatArray(blockSize)
    private val tmpL = FloatArray(blockSize)
    private val tmpR = FloatArray(blockSize)
    private val mixL = FloatArray(blockSize)
    private val mixR = FloatArray(blockSize)

    /** Übergibt ein neues Stück (Aufruf von beliebigem Thread). */
    fun play(song: Song) {
        pending.set(song)
    }

    /** Das zuletzt übergebene Stück (für Tempo/Intensität und Anzeige). */
    val song: Song? get() = pending.get() ?: current

    val info: SongInfo? get() = current?.info

    val beatPosition: Double get() = current?.beatPosition ?: 0.0

    fun setMasterImmediate(v: Float) {
        master = v
        masterTarget = v
    }

    val isSilent: Boolean
        get() = master <= 0.0001f && masterTarget <= 0.0001f

    /** Rendert [frames] Stereo-Frames interleaved (L, R, L, R …) in [out]. */
    fun render(out: FloatArray, frames: Int) {
        var offset = 0
        while (offset < frames) {
            val n = minOf(blockSize, frames - offset)
            renderBlock(out, offset, n)
            offset += n
        }
    }

    private fun renderBlock(out: FloatArray, offset: Int, n: Int) {
        pending.getAndSet(null)?.let { next ->
            val cur = current
            if (cur == null || master <= 0.0001f) {
                fading.clear()
                currentGain = 1f
            } else {
                fading += Fading(cur, currentGain)
                if (fading.size > 3) fading.removeAt(fading.indices.minBy { fading[it].gain })
                currentGain = 0f
            }
            current = next
        }

        val cur = current
        if (cur == null) {
            java.util.Arrays.fill(out, offset * 2, (offset + n) * 2, 0f)
            return
        }
        val halfPi = (Math.PI / 2).toFloat()
        val xStep = n / (crossfadeSeconds * sampleRate)
        val inv = 1f / n

        // aktuelles Stück (blendet ggf. ein)
        cur.render(curL, curR, n)
        val g0 = currentGain
        val g1 = minOf(1f, g0 + xStep)
        currentGain = g1
        for (i in 0 until n) {
            val g = if (g0 >= 1f) 1f else sin((g0 + (g1 - g0) * i * inv) * halfPi)
            mixL[i] = curL[i] * g
            mixR[i] = curR[i] * g
        }

        // ausklingende Stücke
        val it = fading.iterator()
        while (it.hasNext()) {
            val f = it.next()
            val f0 = f.gain
            val f1 = maxOf(0f, f0 - xStep)
            f.gain = f1
            f.song.render(tmpL, tmpR, n)
            for (i in 0 until n) {
                val g = sin((f0 + (f1 - f0) * i * inv) * halfPi)
                mixL[i] += tmpL[i] * g
                mixR[i] += tmpR[i] * g
            }
            if (f1 <= 0f) it.remove()
        }

        val mFrom = master
        val mTo = approach(mFrom, masterTarget, n / (masterRampSeconds * sampleRate))
        master = mTo
        for (i in 0 until n) {
            val m = mFrom + (mTo - mFrom) * (i * inv)
            var l = mixL[i] * m
            var r = mixR[i] * m
            // Peak-Limiter: senkt kurz die Lautstärke statt die Wellenform zu verformen
            val peak = maxOf(kotlin.math.abs(l), kotlin.math.abs(r))
            if (peak * limGain > LIMIT) limGain = LIMIT / peak
            l *= limGain
            r *= limGain
            limGain += (1f - limGain) * limRelease
            out[(offset + i) * 2] = safety(l)
            out[(offset + i) * 2 + 1] = safety(r)
        }
    }

    private fun approach(from: Float, to: Float, step: Float): Float =
        if (from < to) minOf(to, from + step) else maxOf(to, from - step)

    private var limGain = 1f
    private val limRelease = (1.0 - kotlin.math.exp(-1.0 / (0.08 * sampleRate))).toFloat()

    /** Letzte Sicherung oberhalb des Limiters (greift praktisch nie). */
    private fun safety(x: Float): Float =
        if (x > -0.95f && x < 0.95f) x else (0.95f + 0.05f * tanh((kotlin.math.abs(x) - 0.95f) / 0.05f)) * kotlin.math.sign(x)

    private companion object {
        const val LIMIT = 0.89f // -1 dBFS
    }
}
