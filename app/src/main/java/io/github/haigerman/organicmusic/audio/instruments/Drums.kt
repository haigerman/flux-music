package io.github.haigerman.organicmusic.audio.instruments

import io.github.haigerman.organicmusic.audio.dsp.Biquad
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

/** Snare: zwei gedämpfte Töne + gefiltertes Rauschen. */
class Snare(sr: Int, private val rng: Rng) : Drum(sr) {
    var toneHz = 185f
    var toneMix = 0.45f
    var noiseDecay = 0.17f
    var toneDecay = 0.09f
    var brightness = 7000f

    private var p1 = 0f
    private var p2 = 0f
    private var tEnv = 0f
    private var nEnv = 0f
    private var tCoef = 0f
    private var nCoef = 0f
    private var vel = 1f
    private val hp = Biquad(sr).highpass(900f, 0.7f)
    private val lp = OnePole(sr, 7000f)

    override fun start(vel: Float) {
        this.vel = vel
        tEnv = 1f
        nEnv = 1f
        tCoef = decayCoef(toneDecay, sr)
        nCoef = decayCoef(noiseDecay, sr)
        lp.setCutoff(brightness)
        active = true
    }

    override fun sample(): Float {
        p1 += toneHz / sr
        if (p1 >= 1f) p1 -= 1f
        p2 += toneHz * 1.78f / sr
        if (p2 >= 1f) p2 -= 1f
        val tone = (Sine.at(p1) + 0.5f * Sine.at(p2)) * tEnv * toneMix
        val noise = lp.lp(hp.process(rng.bipolar())) * nEnv * (1f - toneMix * 0.5f)
        tEnv *= tCoef
        nEnv *= nCoef
        if (nEnv < 0.0005f && tEnv < 0.0005f) active = false
        return (tone + noise * 1.4f) * vel
    }
}

/** Handclap: drei kurze Rauschstöße plus Nachklang. */
class Clap(sr: Int, private val rng: Rng) : Drum(sr) {
    var tail = 0.16f
    private val bp = Biquad(sr).bandpass(1150f, 1.1f)
    private val hp = OnePole(sr, 500f)
    private var t = 0
    private var burst = 0f
    private var tailEnv = 0f
    private val burstCoef = decayCoef(0.012f, sr)
    private var tailCoef = 0f
    private val gap = (0.009f * sr).toInt()
    private var vel = 1f

    override fun start(vel: Float) {
        this.vel = vel
        t = 0
        burst = 1f
        tailEnv = 0f
        tailCoef = decayCoef(tail, sr)
        active = true
    }

    override fun sample(): Float {
        if (t == gap || t == gap * 2) burst = 1f
        if (t == gap * 3) tailEnv = 0.8f
        t++
        val env = burst + tailEnv
        burst *= burstCoef
        tailEnv *= tailCoef
        if (t > gap * 4 && env < 0.0005f) active = false
        return hp.hp(bp.process(rng.bipolar())) * env * vel * 2.2f
    }
}

/** Hi-Hat: "metallische" Rechteckmischung (wie bei klassischen Drumcomputern) + Rauschen. */
class HiHat(sr: Int, private val rng: Rng) : Drum(sr) {
    var closedDecay = 0.045f
    var openDecay = 0.32f
    var brightness = 1f

    private val freqs = floatArrayOf(205.3f, 304.4f, 369.6f, 522.7f, 540f, 800f).map { it * 1.6f }.toFloatArray()
    private val ph = FloatArray(6)
    private val bp = Biquad(sr).bandpass(10000f, 0.9f)
    private val hp = Biquad(sr).highpass(7000f, 0.7f)
    private var env = 0f
    private var coef = 0f
    private var vel = 1f
    private var nextOpen = false

    fun trigger(vel: Float, open: Boolean, delay: Int = 0) {
        nextOpen = open
        trigger(vel, delay)
    }

    override fun start(vel: Float) {
        this.vel = vel
        env = 1f
        coef = decayCoef(if (nextOpen) openDecay else closedDecay, sr)
        active = true
    }

    override fun sample(): Float {
        var m = 0f
        for (k in 0 until 6) {
            ph[k] += freqs[k] / sr
            if (ph[k] >= 1f) ph[k] -= 1f
            m += if (ph[k] < 0.5f) 1f else -1f
        }
        val x = m * 0.12f + rng.bipolar() * 0.5f * brightness
        val s = hp.process(bp.process(x)) * env * vel
        env *= coef
        if (env < 0.0003f) active = false
        return s * 1.6f
    }
}

/** Shaker: kurzes, weiches Rauschen mit kleinem Anstieg. */
class Shaker(sr: Int, private val rng: Rng) : Drum(sr) {
    var decay = 0.07f
    private val bp = Biquad(sr).bandpass(6500f, 0.8f)
    private var env = 0f
    private var rise = 0f
    private var coef = 0f
    private var vel = 1f

    override fun start(vel: Float) {
        this.vel = vel
        rise = 0f
        env = 1f
        coef = decayCoef(decay, sr)
        active = true
    }

    override fun sample(): Float {
        if (rise < 1f) rise = minOf(1f, rise + 1f / (0.008f * sr)) else env *= coef
        if (env < 0.0005f) active = false
        return bp.process(rng.bipolar()) * env * rise * vel * 1.3f
    }
}

/** Gestimmte Perkussion (Conga/Tom/Holzblock): Sinus mit kleinem Tonhöhenabfall. */
class Perc(sr: Int, private val rng: Rng) : Drum(sr) {
    var decay = 0.16f
    var bend = 0.5f
    var noise = 0.15f
    private var freq = 220f
    private var nextFreq = 220f
    private var phase = 0f
    private var env = 0f
    private var bendEnv = 0f
    private var coef = 0f
    private val bendCoef = decayCoef(0.03f, sr)
    private val nCoef = decayCoef(0.01f, sr)
    private var nEnv = 0f
    private var vel = 1f

    fun trigger(vel: Float, hz: Float, delay: Int = 0) {
        nextFreq = hz
        trigger(vel, delay)
    }

    override fun start(vel: Float) {
        this.vel = vel
        freq = nextFreq
        env = 1f
        bendEnv = 1f
        nEnv = 1f
        coef = decayCoef(decay, sr)
        active = true
    }

    override fun sample(): Float {
        phase += freq * (1f + bend * bendEnv) / sr
        if (phase >= 1f) phase -= 1f
        val s = Sine.at(phase) * env + rng.bipolar() * nEnv * noise
        env *= coef
        bendEnv *= bendCoef
        nEnv *= nCoef
        if (env < 0.0005f) active = false
        return s * vel
    }
}

/** Becken mit optionalem tiefem "Einschlag" – markiert den Beginn großer Teile. */
class Crash(sr: Int, private val rng: Rng) : Instrument(sr) {
    var decay = 2.4f
    private val hpL = Biquad(sr).highpass(3500f, 0.6f)
    private val hpR = Biquad(sr).highpass(3600f, 0.6f)
    private val lp = OnePole(sr, 12000f)
    private var env = 0f
    private var coef = 0f
    private var boomEnv = 0f
    private var boomPhase = 0f
    private var boomPitch = 0f
    private val boomCoef = decayCoef(1.1f, sr)
    private val boomPitchCoef = decayCoef(0.25f, sr)
    private var vel = 1f

    fun trigger(vel: Float, boom: Boolean) {
        this.vel = vel
        env = 1f
        coef = decayCoef(decay, sr)
        if (boom) {
            boomEnv = 1f
            boomPitch = 1f
        }
    }

    override fun render(l: FloatArray, r: FloatArray, n: Int) {
        if (env < 0.0003f && boomEnv < 0.0003f) return
        for (i in 0 until n) {
            val nl = hpL.process(rng.bipolar())
            val nr = hpR.process(rng.bipolar())
            val c = env * vel * 0.55f
            env *= coef
            boomPhase += (42f + 70f * boomPitch) / sr
            if (boomPhase >= 1f) boomPhase -= 1f
            val b = Sine.at(boomPhase) * boomEnv * vel * 0.9f
            boomEnv *= boomCoef
            boomPitch *= boomPitchCoef
            l[i] += lp.lp(nl) * c + b
            r[i] += nr * c + b
        }
    }

    override fun reset() {
        env = 0f
        boomEnv = 0f
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
