package io.github.haigerman.organicmusic.audio.instruments

import io.github.haigerman.organicmusic.audio.dsp.Biquad
import io.github.haigerman.organicmusic.audio.dsp.OnePole
import io.github.haigerman.organicmusic.audio.dsp.Rng
import io.github.haigerman.organicmusic.audio.dsp.Sine
import io.github.haigerman.organicmusic.audio.dsp.decayCoef
import io.github.haigerman.organicmusic.audio.dsp.midiHz
import kotlin.math.abs
import kotlin.math.pow

/**
 * Gezupfte Saite nach Karplus-Strong: ein kurzer Rauschimpuls läuft durch eine
 * Verzögerung mit leichter Dämpfung – daraus entsteht von selbst ein sehr natürlicher
 * Saitenklang. Tonhöhe lässt sich gleitend ändern (Bendings, Vibrato).
 */
class GuitarString(private val sr: Int, private val rng: Rng) {
    private val size = sr / 25 + 4
    private val buf = FloatArray(size)
    private var w = 0
    private var delay = 100f
    private var targetDelay = 100f
    private var glideCoef = 0f
    private var g = 0.99f
    private var damp = 0.5f
    private var last = 0f
    private var env = 0f
    private val envCoef = decayCoef(0.05f, sr)
    private var vibrato = 0f
    private var vibPhase = 0f
    private var vibDelay = 0

    var active = false
        private set

    /**
     * Saite anschlagen. [brightness] 0–1 (Pick-Härte), [t60] Ausklingzeit in Sekunden
     * (kurz = abgedämpft/Palm-Mute), [bendFrom] in Halbtönen unter der Zielnote.
     */
    fun pluck(midi: Int, vel: Float, brightness: Float, t60: Float, bendFrom: Float = 0f, bendMs: Float = 90f, vib: Float = 0f) {
        val f = midiHz(midi)
        damp = 0.5f - 0.35f * brightness.coerceIn(0f, 1f) * 0.5f
        val target = sr / f - damp
        val start = if (bendFrom > 0f) sr / (f * 2f.pow(-bendFrom / 12f)) - damp else target
        delay = start.coerceIn(2f, size - 2f)
        targetDelay = target.coerceIn(2f, size - 2f)
        glideCoef = decayCoef(bendMs / 1000f, sr)
        // Schleifenverstärkung je Periode für die gewünschte Ausklingzeit
        g = 10.0.pow(-3.0 / (t60 * f)).toFloat().coerceAtMost(0.9995f)
        // Anregung: tiefpassgefiltertes Rauschen genau eine Periode lang (überschreibt die alte Schwingung)
        val n = delay.toInt()
        val lp = OnePole(sr, 1200f + 9000f * brightness)
        var p = w - n
        if (p < 0) p += size
        val a = vel * 0.6f
        for (k in 0 until n) {
            buf[p] = lp.lp(rng.bipolar()) * a + buf[p] * 0.15f
            if (++p >= size) p = 0
        }
        vibrato = vib
        vibDelay = (0.18f * sr).toInt()
        env = 1f
        active = true
    }

    /** Saite abdämpfen (Hand legt sich auf die Saiten). */
    fun choke() {
        if (active) g = 0.8f
    }

    fun next(): Float {
        if (!active) return 0f
        delay = targetDelay + (delay - targetDelay) * glideCoef
        var d = delay
        if (vibrato > 0f) {
            if (vibDelay > 0) vibDelay-- else {
                vibPhase += 5.5f / sr
                if (vibPhase >= 1f) vibPhase -= 1f
                d *= 1f + vibrato * Sine.at(vibPhase)
            }
        }
        var rp = w - d
        if (rp < 0f) rp += size
        val i = rp.toInt()
        val fr = rp - i
        val j = if (i + 1 >= size) 0 else i + 1
        val s = buf[i] + (buf[j] - buf[i]) * fr
        val y = g * ((1f - damp) * s + damp * last)
        last = s
        buf[w] = y
        if (++w >= size) w = 0
        val ay = abs(y)
        env = if (ay > env) ay else env * envCoef
        if (env < 0.00003f) active = false
        return y
    }
}

/**
 * E-Gitarre mit Verstärker: mehrere Saiten → Hochpass (straffer Bass) → Vorverstärkung
 * → asymmetrische Röhren-Sättigung → Klangregelung (Mitten) → Lautsprecherbox
 * (Tiefpass). Ergibt den knackigen, mittigen Crunch klassischer Hardrock-Gitarren.
 */
class ElectricGuitar(sr: Int, private val rng: Rng, strings: Int, private val drive: Float) : Instrument(sr) {
    var pan = 0f

    private class Pending(var wait: Int, var midi: Int, var vel: Float, var bright: Float, var t60: Float, var bend: Float, var vib: Float)

    private val str = Array(strings) { GuitarString(sr, rng) }
    private val pend = Array(strings) { Pending(-1, 0, 0f, 0f, 0f, 0f, 0f) }

    private val tight = OnePole(sr, 90f)
    private val preMid = Biquad(sr).peaking(800f, 0.7f, 5f)
    private val tone = Biquad(sr).peaking(1800f, 1f, 3f)
    private val cab1 = Biquad(sr).lowpass(4300f, 0.9f)
    private val cab2 = OnePole(sr, 6500f)
    private val cabHp = Biquad(sr).highpass(85f, 0.7f)
    private val bias = 0.18f
    private val norm = 1f / softSat(drive)

    /**
     * Akkord anschlagen. [notes] von tief nach hoch; [strumMs] Abstand zwischen den Saiten
     * (Abschlag), [muted] = mit dem Handballen abgedämpft.
     */
    fun strike(notes: IntArray, vel: Float, muted: Boolean, ringSeconds: Float, strumMs: Float = 6f, delay: Int = 0) {
        val step = (strumMs * sr / 1000f).toInt()
        for (k in str.indices) {
            if (k < notes.size) {
                val p = pend[k]
                p.wait = delay + k * step
                p.midi = notes[k]
                p.vel = vel * rng.range(0.9f, 1.05f)
                p.bright = if (muted) 0.25f else 0.85f
                p.t60 = if (muted) 0.13f else ringSeconds
                p.bend = 0f
                p.vib = 0f
            } else {
                str[k].choke()
            }
        }
    }

    /** Einzelton (Riff-Lauf oder Solo), optional mit Bending und Vibrato. Nutzt die erste Saite. */
    fun note(midi: Int, vel: Float, ringSeconds: Float, bendFrom: Float = 0f, vibrato: Float = 0f, delay: Int = 0) {
        for (k in 1 until str.size) str[k].choke()
        val p = pend[0]
        p.wait = delay
        p.midi = midi
        p.vel = vel
        p.bright = 0.8f
        p.t60 = ringSeconds
        p.bend = bendFrom
        p.vib = vibrato
    }

    /** Alle Saiten abdämpfen – die typischen kurzen Pausen zwischen den Akkorden. */
    fun choke(delay: Int = 0) {
        if (delay <= 0) {
            for (s in str) s.choke()
        } else {
            chokeWait = delay
        }
    }

    private var chokeWait = -1

    override fun reset() {
        for (p in pend) p.wait = -1
        for (s in str) s.choke()
    }

    override fun render(l: FloatArray, r: FloatArray, n: Int) {
        val gl = panL(pan)
        val gr = panR(pan)
        val off = softSat(bias)
        for (i in 0 until n) {
            if (chokeWait >= 0) {
                if (chokeWait == 0) for (s in str) s.choke()
                chokeWait--
            }
            var x = 0f
            for (k in str.indices) {
                val p = pend[k]
                if (p.wait >= 0) {
                    if (p.wait == 0) str[k].pluck(p.midi, p.vel, p.bright, p.t60, p.bend, 90f, p.vib)
                    p.wait--
                }
                x += str[k].next()
            }
            // Verstärker
            var v = tight.hp(x)
            v = preMid.process(v)
            v = (softSat(v * drive + bias) - off) * norm
            v = tone.process(v)
            v = cab2.lp(cab1.process(cabHp.process(v)))
            l[i] += v * gl
            r[i] += v * gr
        }
    }
}
