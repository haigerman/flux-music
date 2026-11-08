package io.github.haigerman.organicmusic.audio.instruments

import io.github.haigerman.organicmusic.audio.dsp.OnePole
import io.github.haigerman.organicmusic.audio.dsp.Shelf

/**
 * Gitarrenverstärker für echte Gitarren-Aufnahmen aus dem [Sampler]:
 * Vorstufe mit Mitten-Anhebung → zweistufige Röhren-Sättigung (2-fach überabgetastet,
 * damit nichts digital klirrt) → Klangregelung → Lautsprecherbox (4×12) → Stereo-Position.
 */
class GuitarRig(
    sr: Int,
    /** Gitarren-Aufnahmen, die durch den Verstärker laufen (z. B. normal + abgedämpft). */
    val sources: List<Sampler>,
    private val drive: Float,
    var pan: Float = 0f,
    /** Präsenz/Höhen der Box in dB. */
    presenceDb: Float = 2.5f,
    /** Bässe des Amps in dB. */
    bassDb: Float = 1.5f,
) : Instrument(sr) {
    var outputGain = 1f

    private val inL = FloatArray(512)
    private val inR = FloatArray(512)

    private val tight = Shelf(sr).highpass(95f, 0.7f)
    private val preMid = Shelf(sr).peak(750f, 0.8f, 6f)
    private val preHi = Shelf(sr).lowpass(7500f, 0.6f)

    // 2-fach Überabtastung: Anti-Imaging vor, Anti-Aliasing nach der Sättigung (bei 2·sr)
    private val upA = Shelf(sr * 2).lowpass(19000f, 0.54f)
    private val upB = Shelf(sr * 2).lowpass(19000f, 1.31f)
    private val dnA = Shelf(sr * 2).lowpass(17000f, 0.54f)
    private val dnB = Shelf(sr * 2).lowpass(17000f, 1.31f)
    private var prev = 0f

    private val couple = OnePole(sr * 2, 30f) // Koppelkondensator zwischen den Stufen

    private val bass = Shelf(sr).lowShelf(140f, bassDb)
    private val mid = Shelf(sr).peak(450f, 0.9f, -2.5f)
    private val presence = Shelf(sr).peak(2300f, 1.1f, presenceDb)
    private val cabRes = Shelf(sr).peak(110f, 1.4f, 3f)
    private val cabLp1 = Shelf(sr).lowpass(5200f, 0.9f)
    private val cabLp2 = Shelf(sr).lowpass(6800f, 0.6f)
    private val cabHp = Shelf(sr).highpass(75f, 0.7f)

    private val bias = 0.2f
    private val offs = softSat(bias)
    private val norm = 1f / softSat(drive)

    private fun stage(x: Float): Float {
        // erste Stufe asymmetrisch (geradzahlige Obertöne, "röhrig"), zweite symmetrisch
        val a = (softSat(x * drive + bias) - offs) * norm
        val b = couple.hp(a)
        return softSat(b * 2.2f) * 0.6f
    }

    override fun reset() {
        for (s in sources) s.reset()
    }

    override fun render(l: FloatArray, r: FloatArray, n: Int) {
        java.util.Arrays.fill(inL, 0, n, 0f)
        java.util.Arrays.fill(inR, 0, n, 0f)
        for (s in sources) s.render(inL, inR, n)
        val gl = panL(pan) * outputGain
        val gr = panR(pan) * outputGain
        for (i in 0 until n) {
            var x = (inL[i] + inR[i]) * 0.7f
            x = preHi.process(preMid.process(tight.process(x)))
            // überabgetastet: Zwischenwert + aktueller Wert
            val m = (prev + x) * 0.5f
            prev = x
            // (lineare Zwischenwerte behalten die Amplitude; Filter glätten die Spiegelfrequenzen)
            val u0 = upB.process(upA.process(m))
            val u1 = upB.process(upA.process(x))
            dnB.process(dnA.process(stage(u0)))
            val y = dnB.process(dnA.process(stage(u1)))
            var v = bass.process(y)
            v = mid.process(v)
            v = presence.process(v)
            v = cabRes.process(v)
            v = cabHp.process(cabLp2.process(cabLp1.process(v)))
            l[i] += v * gl
            r[i] += v * gr
        }
    }
}
