package io.github.haigerman.organicmusic.audio.instruments

import io.github.haigerman.organicmusic.audio.dsp.Adsr
import io.github.haigerman.organicmusic.audio.dsp.Rng
import io.github.haigerman.organicmusic.audio.dsp.Sine
import io.github.haigerman.organicmusic.audio.dsp.Svf
import io.github.haigerman.organicmusic.audio.dsp.decayCoef
import io.github.haigerman.organicmusic.audio.dsp.midiHz
import io.github.haigerman.organicmusic.audio.dsp.sawBlep
import io.github.haigerman.organicmusic.audio.dsp.squareBlep
import io.github.haigerman.organicmusic.audio.dsp.triangle
import kotlin.math.pow

enum class Wave { SAW, SQUARE, TRIANGLE, SINE }

/** Klangeinstellung für den [PolySynth] (subtraktive Synthese). */
data class SynthPreset(
    val wave: Wave = Wave.SAW,
    /** Anzahl leicht verstimmter Oszillatoren pro Stimme (1–7, "Unisono"/Supersaw). */
    val oscs: Int = 2,
    /** Verteilung der Unisono-Oszillatoren im Stereobild (0 = mono, 1 = ganz breit). */
    val unisonWidth: Float = 0f,
    val detuneCents: Float = 9f,
    /** Sinus eine Oktave tiefer. */
    val sub: Float = 0f,
    val cutoff: Float = 1800f,
    val resonance: Float = 0.8f,
    /** Filterhüllkurve: Grenzfrequenz × (1 + envAmount · Hüllkurve). */
    val envAmount: Float = 2f,
    val fAttack: Float = 0.005f,
    val fDecay: Float = 0.4f,
    val fSustain: Float = 0.2f,
    val attack: Float = 0.01f,
    val decay: Float = 0.4f,
    val sustain: Float = 0.7f,
    val release: Float = 0.4f,
    /** Anteil, mit dem die Grenzfrequenz der Tonhöhe folgt (0–1). */
    val keyTrack: Float = 0.5f,
    val vibratoCents: Float = 0f,
    val vibratoRate: Float = 5f,
    /** Zufällige Stereobreite der Stimmen (0–1). */
    val spread: Float = 0.4f,
    /** Langsames "Atmen" der Grenzfrequenz (Anteil, 0–1). */
    val wobble: Float = 0f,
    val wobbleRate: Float = 0.15f,
    val gain: Float = 1f,
)

/**
 * Mehrstimmiger subtraktiver Synthesizer: Pads, Plucks, Leads, Akkordstabs, Arpeggios.
 * Wird eine Stimme "gestohlen", setzt sie von ihrer aktuellen Lautstärke aus neu an – kein Knacksen.
 */
class PolySynth(sr: Int, private val rng: Rng, voices: Int, var preset: SynthPreset) : Instrument(sr) {
    /** Von außen modulierbar (z. B. Filterfahrt im Aufbau). */
    var cutoffMul = 1f

    private var lfoPhase = rng.float()
    private var wobblePhase = rng.float()

    private inner class Voice {
        val ph = FloatArray(7) { rng.float() }
        var subPh = 0f
        var freq = 440f
        var vel = 1f
        var gate = 0
        var wait = -1
        var pMidi = 0
        var pVel = 0f
        var pLen = 0
        var pan = 0f
        var gl = 0.7f
        var gr = 0.7f
        var age = 0L
        val amp = Adsr(sr)
        val fenv = Adsr(sr)
        val svf = Svf(sr)
        val svfR = Svf(sr)
        var counter = 0
        val busy: Boolean get() = amp.active || wait >= 0

        fun start() {
            val p = preset
            freq = midiHz(pMidi)
            vel = pVel
            gate = pLen
            amp.attack = p.attack; amp.decay = p.decay; amp.sustain = p.sustain; amp.release = p.release
            fenv.attack = p.fAttack; fenv.decay = p.fDecay; fenv.sustain = p.fSustain; fenv.release = p.release
            amp.gateOn()
            fenv.gateOn()
            pan = rng.bipolar() * p.spread
            gl = panL(pan)
            gr = panR(pan)
            counter = 0
        }
    }

    private val pool = Array(voices) { Voice() }
    private var clock = 0L

    fun noteOn(midi: Int, vel: Float, lengthSamples: Int, delay: Int = 0) {
        val v = pool.firstOrNull { !it.busy } ?: pool.minBy { if (it.wait >= 0) 2f else it.amp.level }
        v.pMidi = midi
        v.pVel = vel
        v.pLen = lengthSamples.coerceAtLeast(1)
        v.age = clock
        v.wait = delay.coerceAtLeast(0)
    }

    fun releaseAll() {
        for (v in pool) {
            v.amp.gateOff(); v.fenv.gateOff(); v.gate = 0
        }
    }

    override fun reset() {
        for (v in pool) {
            v.amp.kill(); v.fenv.kill(); v.wait = -1
        }
    }

    override fun render(l: FloatArray, r: FloatArray, n: Int) {
        val p = preset
        val oscs = p.oscs.coerceIn(1, 7)
        val det = FloatArray(oscs)
        val oL = FloatArray(oscs)
        val oR = FloatArray(oscs)
        for (o in 0 until oscs) {
            val x = if (oscs == 1) 0f else -1f + 2f * o / (oscs - 1)
            // Supersaw-artige Verteilung: außen etwas stärker verstimmt
            det[o] = cents(x * p.detuneCents * (0.8f + 0.2f * kotlin.math.abs(x)))
            val pan = x * p.unisonWidth
            oL[o] = panL(pan) * 1.41f
            oR[o] = panR(pan) * 1.41f
        }
        val stereo = p.unisonWidth > 0f && oscs > 1
        val oscGain = 1f / kotlin.math.sqrt(oscs.toFloat()) / (if (oscs > 2) 1.2f else 1f)
        val lfoInc = p.vibratoRate / sr
        val wobbleInc = p.wobbleRate / sr
        clock += n

        for (v in pool) {
            if (!v.busy) continue
            for (i in 0 until n) {
                if (v.wait >= 0) {
                    if (v.wait == 0) v.start()
                    v.wait--
                }
                if (!v.amp.active) {
                    if (v.wait >= 0) continue else break
                }
                if (v.gate > 0 && --v.gate == 0) {
                    v.amp.gateOff(); v.fenv.gateOff()
                }
                val a = v.amp.next()
                val fe = v.fenv.next()
                var vib = 1f
                if (p.vibratoCents > 0f) {
                    vib = 1f + Sine.at(lfoPhase + i * lfoInc) * p.vibratoCents * 0.000578f
                }
                if (v.counter == 0) {
                    val track = (v.freq / 261.6f).pow(p.keyTrack)
                    val wob = if (p.wobble > 0f) 1f + p.wobble * Sine.at(wobblePhase + i * wobbleInc) else 1f
                    val fc = p.cutoff * track * cutoffMul * wob * (1f + p.envAmount * fe * (0.5f + 0.5f * v.vel))
                    v.svf.set(fc, p.resonance)
                    if (stereo) v.svfR.set(fc, p.resonance)
                }
                v.counter = (v.counter + 1) and 15
                var s = 0f
                var sR = 0f
                for (o in 0 until oscs) {
                    val dt = v.freq * det[o] * vib / sr
                    var ph = v.ph[o] + dt
                    if (ph >= 1f) ph -= 1f
                    v.ph[o] = ph
                    val w = when (p.wave) {
                        Wave.SAW -> sawBlep(ph, dt)
                        Wave.SQUARE -> squareBlep(ph, dt)
                        Wave.TRIANGLE -> triangle(ph)
                        Wave.SINE -> Sine.at(ph)
                    }
                    if (stereo) {
                        s += w * oL[o]
                        sR += w * oR[o]
                    } else {
                        s += w
                    }
                }
                s *= oscGain
                sR *= oscGain
                if (p.sub > 0f) {
                    v.subPh += v.freq * 0.5f / sr
                    if (v.subPh >= 1f) v.subPh -= 1f
                    val sub = Sine.at(v.subPh) * p.sub
                    s += sub
                    sR += sub
                }
                val k = a * v.vel * p.gain
                if (stereo) {
                    l[i] += v.svf.lp(s) * k * v.gl * 1.41f
                    r[i] += v.svfR.lp(sR) * k * v.gr * 1.41f
                } else {
                    val out = v.svf.lp(s) * k
                    l[i] += out * v.gl
                    r[i] += out * v.gr
                }
            }
        }
        lfoPhase = (lfoPhase + n * lfoInc) % 1f
        wobblePhase = (wobblePhase + n * wobbleInc) % 1f
    }

    private fun cents(c: Float): Float = 2f.pow(c / 1200f)
}

/** Klangeinstellung für den [Bass]. */
data class BassPreset(
    val saw: Float = 0.6f,
    val square: Float = 0f,
    val sine: Float = 0.6f,
    val cutoff: Float = 300f,
    val envAmount: Float = 3f,
    val fDecay: Float = 0.18f,
    val resonance: Float = 1.1f,
    val attack: Float = 0.003f,
    val decay: Float = 0.3f,
    val sustain: Float = 0.8f,
    val release: Float = 0.06f,
    val glide: Float = 0.04f,
    val drive: Float = 1.3f,
    val gain: Float = 1f,
)

/** Einstimmiger Bass mit Filterhüllkurve, Sub-Sinus und optionalem Gleiten. */
class Bass(sr: Int, var preset: BassPreset) : Instrument(sr) {
    private var phase = 0f
    private var sinePhase = 0f
    private var freq = 55f
    private var targetFreq = 55f
    private var vel = 0f
    private var gate = 0
    private var wait = -1
    private var pMidi = 0
    private var pVel = 0f
    private var pLen = 0
    private var pGlide = false
    private var glideCoef = 0f
    private val amp = Adsr(sr)
    private val fenv = Adsr(sr)
    private val svf = Svf(sr)
    private var counter = 0

    fun noteOn(midi: Int, vel: Float, lengthSamples: Int, delay: Int = 0, glide: Boolean = false) {
        pMidi = midi
        pVel = vel
        pLen = lengthSamples.coerceAtLeast(1)
        pGlide = glide
        wait = delay.coerceAtLeast(0)
    }

    private fun start() {
        val p = preset
        targetFreq = midiHz(pMidi)
        if (!pGlide || !amp.active) freq = targetFreq
        glideCoef = decayCoef(p.glide, sr)
        vel = pVel
        gate = pLen
        amp.attack = p.attack; amp.decay = p.decay; amp.sustain = p.sustain; amp.release = p.release
        fenv.attack = 0.001f; fenv.decay = p.fDecay; fenv.sustain = 0f; fenv.release = p.release
        amp.gateOn()
        fenv.gateOn()
    }

    override fun reset() {
        amp.kill(); fenv.kill(); wait = -1
    }

    override fun render(l: FloatArray, r: FloatArray, n: Int) {
        val p = preset
        for (i in 0 until n) {
            if (wait >= 0) {
                if (wait == 0) start()
                wait--
            }
            if (!amp.active) continue
            if (gate > 0 && --gate == 0) {
                amp.gateOff(); fenv.gateOff()
            }
            freq = targetFreq + (freq - targetFreq) * glideCoef
            val a = amp.next()
            val fe = fenv.next()
            if (counter == 0) svf.set(p.cutoff * (1f + p.envAmount * fe * vel), p.resonance)
            counter = (counter + 1) and 15
            val dt = freq / sr
            phase += dt
            if (phase >= 1f) phase -= 1f
            sinePhase += dt
            if (sinePhase >= 1f) sinePhase -= 1f
            var s = 0f
            if (p.saw > 0f) s += sawBlep(phase, dt) * p.saw
            if (p.square > 0f) s += squareBlep(phase, dt) * p.square
            s = svf.lp(s) + Sine.at(sinePhase) * p.sine
            val out = softSat(s * p.drive) * a * vel * p.gain
            l[i] += out
            r[i] += out
        }
    }
}
