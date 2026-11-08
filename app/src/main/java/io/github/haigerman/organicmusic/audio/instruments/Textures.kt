package io.github.haigerman.organicmusic.audio.instruments

import io.github.haigerman.organicmusic.audio.dsp.Biquad
import io.github.haigerman.organicmusic.audio.dsp.Drift
import io.github.haigerman.organicmusic.audio.dsp.OnePole
import io.github.haigerman.organicmusic.audio.dsp.PinkNoise
import io.github.haigerman.organicmusic.audio.dsp.Rng
import io.github.haigerman.organicmusic.audio.dsp.Sine

/** Schallplatten-Atmosphäre: leises Rauschen und unregelmäßiges Knistern. */
class Vinyl(sr: Int, private val rng: Rng) : Instrument(sr) {
    private val pink = PinkNoise(rng)
    private val hissBp = Biquad(sr).bandpass(3500f, 0.5f)
    private val crackleHp = Biquad(sr).highpass(1500f, 0.7f)
    private var crackle = 0f
    private var cracklePan = 0f
    private val crackleCoef = 0.55f

    override fun render(l: FloatArray, r: FloatArray, n: Int) {
        for (i in 0 until n) {
            val hiss = hissBp.process(pink.next()) * 0.05f
            if (rng.chance(9f / sr)) {
                crackle = rng.range(0.2f, 1f) * (if (rng.chance(0.08f)) 2.2f else 1f)
                cracklePan = rng.bipolar() * 0.7f
            }
            val c = crackleHp.process(crackle * rng.bipolar()) * 0.5f
            crackle *= crackleCoef
            l[i] += hiss + c * (1f - cracklePan) * 0.7f
            r[i] += hiss * 0.95f + c * (1f + cracklePan) * 0.7f
        }
    }

    override fun reset() {}
}

/** Meeresbrandung: tiefes Rauschen, das in langen, unregelmäßigen Wellen an- und abschwillt. */
class Waves(private val srate: Int, private val rng: Rng) : Instrument(srate) {
    private val pinkL = PinkNoise(rng)
    private val pinkR = PinkNoise(rng)
    private val lpL = OnePole(srate, 800f)
    private val lpR = OnePole(srate, 800f)
    private val hpL = OnePole(srate, 180f)
    private val hpR = OnePole(srate, 180f)
    private var phase = 0f
    private var period = 9f
    private val drift = Drift(srate, rng, 10f, 25f, 0.5f, 1f)
    private var counter = 0

    override fun render(l: FloatArray, r: FloatArray, n: Int) {
        for (i in 0 until n) {
            phase += 1f / (period * srate)
            if (phase >= 1f) {
                phase -= 1f
                period = rng.range(7f, 13f)
            }
            // asymmetrische Welle: schneller Anstieg, langes Auslaufen
            val x = if (phase < 0.35f) phase / 0.35f else 1f - (phase - 0.35f) / 0.65f
            val swell = x * x * (3f - 2f * x)
            if (counter == 0) {
                val fc = 400f + 1600f * swell
                lpL.setCutoff(fc)
                lpR.setCutoff(fc * 1.1f)
                drift.advance(64)
            }
            counter = (counter + 1) and 63
            val a = (0.15f + 0.85f * swell) * drift.value * 1.4f
            l[i] += hpL.hp(lpL.lp(pinkL.next())) * a * 1.6f
            r[i] += hpR.hp(lpR.lp(pinkR.next())) * a * 1.6f
        }
    }

    override fun reset() {}
}

/** Sehr tiefer, schwebender Grundton (zwei leicht verstimmte Sinus). */
class Drone(sr: Int, rng: Rng) : Instrument(sr) {
    private var p1 = rng.float()
    private var p2 = rng.float()
    private var freq = 55f
    private var target = 55f
    private val swell = Drift(sr, rng, 6f, 14f, 0.55f, 1f)

    fun setNote(hz: Float) {
        target = hz
    }

    override fun render(l: FloatArray, r: FloatArray, n: Int) {
        val g = swell.advance(n)
        for (i in 0 until n) {
            freq += (target - freq) * 0.00002f
            p1 += freq / sr
            if (p1 >= 1f) p1 -= 1f
            p2 += freq * 1.003f / sr
            if (p2 >= 1f) p2 -= 1f
            val o = Sine.at(p1 * 2f) * 0.25f
            val s = (Sine.at(p1) + Sine.at(p2) + o) * 0.35f * g
            l[i] += s
            r[i] += s
        }
    }

    override fun reset() {}
}
