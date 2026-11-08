package io.github.haigerman.organicmusic.audio.instruments

import io.github.haigerman.organicmusic.audio.dsp.OnePole
import io.github.haigerman.organicmusic.audio.dsp.Rng
import io.github.haigerman.organicmusic.audio.dsp.Sine
import io.github.haigerman.organicmusic.audio.dsp.Svf
import io.github.haigerman.organicmusic.audio.dsp.decayCoef
import kotlin.math.exp

/** Basis für einstimmige Trommeln mit verzögertem Auslösen. */
abstract class Drum(sr: Int) : Instrument(sr) {
    private var wait = -1
    private var pendingVel = 0f
    protected var active = false

    fun trigger(vel: Float, delay: Int = 0) {
        if (delay <= 0) start(vel) else {
            wait = delay
            pendingVel = vel
        }
    }

    protected abstract fun start(vel: Float)
    protected abstract fun sample(): Float

    /** Mono-Trommel mit fester Stereo-Position. */
    var pan = 0f

    override fun render(l: FloatArray, r: FloatArray, n: Int) {
        val gl = panL(pan)
        val gr = panR(pan)
        for (i in 0 until n) {
            if (wait >= 0) {
                if (wait == 0) start(pendingVel)
                wait--
            }
            if (!active) continue
            val s = sample()
            l[i] += s * gl
            r[i] += s * gr
        }
    }

    override fun reset() {
        active = false
        wait = -1
    }
}

/** Bassdrum: Sinus mit schnellem Tonhöhenabfall, Klick und Sättigung. */
class Kick(sr: Int, private val rng: Rng) : Drum(sr) {
    var startHz = 150f
    var endHz = 47f
    var pitchDecay = 0.035f
    var decay = 0.45f
    var drive = 1.6f
    var click = 0.25f
    var tone = 9000f

    private var phase = 0f
    private var pitchEnv = 0f
    private var amp = 0f
    private var clickEnv = 0f
    private var pCoef = 0f
    private var aCoef = 0f
    private val cCoef = decayCoef(0.006f, sr)
    private var vel = 1f
    private var attack = 0f
    private val lp = OnePole(sr, 9000f)
    private var age = 0

    override fun start(vel: Float) {
        this.vel = vel
        pitchEnv = 1f
        amp = 1f
        clickEnv = 1f
        attack = 0f
        pCoef = exp(-1.0 / (pitchDecay * sr)).toFloat()
        aCoef = decayCoef(decay, sr)
        lp.setCutoff(tone)
        age = 0
        active = true
    }

    override fun sample(): Float {
        val f = endHz + (startHz - endHz) * pitchEnv
        pitchEnv *= pCoef
        phase += f / sr
        if (phase >= 1f) phase -= 1f
        if (attack < 1f) attack = minOf(1f, attack + 1f / (0.0015f * sr))
        var s = Sine.at(phase) * amp * attack
        s = softSat(s * drive) / softSat(drive)
        s += rng.bipolar() * clickEnv * click * 0.5f
        amp *= aCoef
        clickEnv *= cCoef
        if (++age > sr * 3 || amp < 0.0005f) active = false
        return lp.lp(s) * vel
    }
}

/** Rauschen, das über [start] Sekunden ansteigt und heller wird – typischer Spannungsaufbau. */
class Riser(sr: Int, private val rng: Rng) : Instrument(sr) {
    private val svfL = Svf(sr)
    private val svfR = Svf(sr)
    private var t = 0
    private var len = 0
    private var vel = 0f
    private var fade = 0f
    private var counter = 0

    fun start(seconds: Float, vel: Float) {
        len = (seconds * sr).toInt().coerceAtLeast(1)
        t = 0
        this.vel = vel
        fade = 1f
    }

    override fun render(l: FloatArray, r: FloatArray, n: Int) {
        if (len == 0) return
        for (i in 0 until n) {
            val x = (t.toFloat() / len).coerceAtMost(1f)
            if (counter == 0) {
                val fc = 250f * Math.pow(36.0, x.toDouble()).toFloat()
                svfL.set(fc, 2.2f)
                svfR.set(fc * 1.07f, 2.2f)
            }
            counter = (counter + 1) and 31
            val a = x * x * vel * fade
            svfL.process(rng.bipolar())
            svfR.process(rng.bipolar())
            l[i] += svfL.band * a
            r[i] += svfR.band * a
            if (t >= len) {
                fade *= 0.9993f
                if (fade < 0.001f) {
                    len = 0
                    return
                }
            }
            t++
        }
    }

    override fun reset() {
        len = 0
    }
}
