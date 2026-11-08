package io.github.haigerman.organicmusic.audio.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tan

const val TWO_PI = (2.0 * PI)

/** Schneller Xorshift-Zufallsgenerator (keine Allokationen, deterministisch per Seed). */
class Rng(seed: Long) {
    private var s: Long = if (seed == 0L) 0x2545F4914F6CDD1DL else seed

    init {
        repeat(4) { nextLong() } // schlechte Seeds "aufwärmen"
    }

    fun nextLong(): Long {
        var x = s
        x = x xor (x shl 13)
        x = x xor (x ushr 7)
        x = x xor (x shl 17)
        s = x
        return x
    }

    /** [0, 1) */
    fun float(): Float = (nextLong() ushr 40).toInt() / 16777216f

    /** [-1, 1) */
    fun bipolar(): Float = float() * 2f - 1f

    fun range(a: Float, b: Float): Float = a + (b - a) * float()

    fun chance(p: Float): Boolean = float() < p

    /** [0, n) */
    fun int(n: Int): Int = ((nextLong() ushr 33) % n).toInt()

    /** [a, b] (beide inklusive) */
    fun int(a: Int, b: Int): Int = a + int(b - a + 1)

    fun <T> pick(list: List<T>): T = list[int(list.size)]

    fun pick(arr: IntArray): Int = arr[int(arr.size)]

    /** Wählt einen Index gemäß relativen Gewichten. */
    fun weighted(weights: FloatArray): Int {
        var sum = 0f
        for (w in weights) sum += w
        var x = float() * sum
        for (i in weights.indices) {
            x -= weights[i]
            if (x < 0f) return i
        }
        return weights.size - 1
    }
}

/** Rosa Rauschen (Paul Kellet, "economy"-Variante). */
class PinkNoise(private val rng: Rng) {
    private var b0 = 0f
    private var b1 = 0f
    private var b2 = 0f

    fun next(): Float {
        val w = rng.bipolar()
        b0 = 0.99765f * b0 + w * 0.0990460f
        b1 = 0.96300f * b1 + w * 0.2965164f
        b2 = 0.57000f * b2 + w * 1.0526913f
        return (b0 + b1 + b2 + w * 0.1848f) * 0.22f
    }
}

/** Einpoliger Tiefpass. */
class OnePole(sampleRate: Int, cutoff: Float) {
    private val sr = sampleRate.toFloat()
    private var a = 0f
    private var y = 0f

    init {
        setCutoff(cutoff)
    }

    fun setCutoff(fc: Float) {
        a = (1.0 - exp(-TWO_PI * fc / sr)).toFloat()
    }

    fun lp(x: Float): Float {
        y += a * (x - y)
        return y
    }

    fun hp(x: Float): Float = x - lp(x)
}

/** RBJ-Biquad. */
class Biquad(sampleRate: Int) {
    private val sr = sampleRate.toDouble()
    private var b0 = 1f
    private var b1 = 0f
    private var b2 = 0f
    private var a1 = 0f
    private var a2 = 0f
    private var x1 = 0f
    private var x2 = 0f
    private var y1 = 0f
    private var y2 = 0f

    private fun set(nb0: Double, nb1: Double, nb2: Double, na0: Double, na1: Double, na2: Double) {
        b0 = (nb0 / na0).toFloat()
        b1 = (nb1 / na0).toFloat()
        b2 = (nb2 / na0).toFloat()
        a1 = (na1 / na0).toFloat()
        a2 = (na2 / na0).toFloat()
    }

    /** Bandpass mit konstanter Spitzenverstärkung 0 dB. */
    fun bandpass(f: Float, q: Float): Biquad {
        val w = TWO_PI * f.coerceIn(10f, (sr * 0.45).toFloat()) / sr
        val alpha = sin(w) / (2.0 * q)
        set(alpha, 0.0, -alpha, 1 + alpha, -2 * cos(w), 1 - alpha)
        return this
    }

    fun lowpass(f: Float, q: Float = 0.707f): Biquad {
        val w = TWO_PI * f.coerceIn(10f, (sr * 0.45).toFloat()) / sr
        val alpha = sin(w) / (2.0 * q)
        val c = cos(w)
        set((1 - c) / 2, 1 - c, (1 - c) / 2, 1 + alpha, -2 * c, 1 - alpha)
        return this
    }

    fun highpass(f: Float, q: Float = 0.707f): Biquad {
        val w = TWO_PI * f.coerceIn(10f, (sr * 0.45).toFloat()) / sr
        val alpha = sin(w) / (2.0 * q)
        val c = cos(w)
        set((1 + c) / 2, -(1 + c), (1 + c) / 2, 1 + alpha, -2 * c, 1 - alpha)
        return this
    }

    fun process(x: Float): Float {
        val y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
        x2 = x1; x1 = x
        y2 = y1; y1 = y
        return y
    }
}

/**
 * Zustandsvariablen-Filter (TPT nach Zavalishin): bleibt stabil, auch wenn die
 * Grenzfrequenz schnell moduliert wird – ideal für Filterhüllkurven.
 */
class Svf(sampleRate: Int) {
    private val piOverSr = (PI / sampleRate).toFloat()
    private val maxFc = sampleRate * 0.45f
    private var a1 = 0f
    private var a2 = 0f
    private var a3 = 0f
    private var k = 1.4f
    private var ic1 = 0f
    private var ic2 = 0f

    /** Letzte Ausgänge (nach [process]). */
    var low = 0f
        private set
    var band = 0f
        private set
    var high = 0f
        private set

    init {
        set(1000f, 0.707f)
    }

    fun set(cutoff: Float, q: Float) {
        val g = tan(cutoff.coerceIn(20f, maxFc) * piOverSr)
        k = 1f / q
        a1 = 1f / (1f + g * (g + k))
        a2 = g * a1
        a3 = g * a2
    }

    fun process(x: Float) {
        val v3 = x - ic2
        val v1 = a1 * ic1 + a2 * v3
        val v2 = ic2 + a2 * ic1 + a3 * v3
        ic1 = 2f * v1 - ic1
        ic2 = 2f * v2 - ic2
        low = v2
        band = v1
        high = x - k * v1 - v2
    }

    fun lp(x: Float): Float {
        process(x)
        return low
    }

    fun reset() {
        ic1 = 0f; ic2 = 0f
    }
}

/**
 * Langsam driftender Zufallswert in [lo, hi]: wandert mit weicher (Cosinus-)Interpolation
 * zu immer neuen Zielwerten.
 */
class Drift(
    private val sampleRate: Int,
    private val rng: Rng,
    private val minSeconds: Float,
    private val maxSeconds: Float,
    private val lo: Float = 0f,
    private val hi: Float = 1f,
) {
    private var from = rng.range(lo, hi)
    private var to = rng.range(lo, hi)
    private var pos = 0f
    private var step = newStep()

    private fun newStep(): Float = 1f / (rng.range(minSeconds, maxSeconds) * sampleRate)

    var value: Float = from
        private set

    /** n Samples auf einmal weiterschalten. */
    fun advance(n: Int): Float {
        pos += step * n
        while (pos >= 1f) {
            pos -= 1f
            from = to
            to = rng.range(lo, hi)
            step = newStep()
        }
        val t = (1f - cos(pos * PI.toFloat())) * 0.5f
        value = from + (to - from) * t
        return value
    }
}

fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

/** Weicher Übergang 0 → 1 zwischen [e0] und [e1]. */
fun smoothstep(x: Float, e0: Float, e1: Float): Float {
    val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/** MIDI-Notennummer → Frequenz in Hz. */
private val midiTable = FloatArray(128) { (440.0 * 2.0.pow((it - 69) / 12.0)).toFloat() }

fun midiHz(note: Int): Float = midiTable[note.coerceIn(0, 127)]

/** Faktor, um den ein Wert pro Sample schrumpft, damit er in [seconds] um 60 dB fällt. */
fun decayCoef(seconds: Float, sampleRate: Int): Float =
    if (seconds <= 0f) 0f else 10.0.pow(-3.0 / (seconds * sampleRate)).toFloat()

/** Sinustabelle mit linearer Interpolation (deutlich schneller als Math.sin). */
object Sine {
    private const val SIZE = 4096
    private val table = FloatArray(SIZE + 1) { sin(TWO_PI * it / SIZE).toFloat() }

    /** phase in [0, 1) – Werte außerhalb werden umgebrochen. */
    fun at(phase: Float): Float {
        var p = phase - phase.toInt()
        if (p < 0f) p += 1f
        val x = p * SIZE
        val i = x.toInt()
        if (i >= SIZE) return table[0] // Rundung bei p knapp unter 1
        val f = x - i
        return table[i] + (table[i + 1] - table[i]) * f
    }
}

/** PolyBLEP-Korrektur gegen Aliasing an Sprungstellen. t, dt in Perioden. */
fun polyBlep(t: Float, dt: Float): Float = when {
    t < dt -> {
        val x = t / dt
        x + x - x * x - 1f
    }
    t > 1f - dt -> {
        val x = (t - 1f) / dt
        x * x + x + x + 1f
    }
    else -> 0f
}

/** Bandbegrenzter Sägezahn. */
fun sawBlep(phase: Float, dt: Float): Float = 2f * phase - 1f - polyBlep(phase, dt)

/** Bandbegrenztes Rechteck (50 %). */
fun squareBlep(phase: Float, dt: Float): Float {
    var p2 = phase + 0.5f
    if (p2 >= 1f) p2 -= 1f
    return (if (phase < 0.5f) 1f else -1f) + polyBlep(phase, dt) - polyBlep(p2, dt)
}

/** Weiches Dreieck aus der Phase (für tiefe, warme Töne ausreichend sauber). */
fun triangle(phase: Float): Float = 4f * kotlin.math.abs(phase - 0.5f) - 1f

/**
 * ADSR-Hüllkurve: linearer Anstieg, exponentielles Abklingen. Zeiten in Sekunden.
 */
class Adsr(private val sr: Int) {
    var attack = 0.01f
    var decay = 0.3f
    var sustain = 0.7f
    var release = 0.5f

    private var stage = 0 // 0 aus, 1 attack, 2 decay/sustain, 3 release
    private var attackStep = 0f
    private var decayCoef = 0f
    private var releaseCoef = 0f

    var level = 0f
        private set

    val active: Boolean get() = stage != 0

    fun gateOn() {
        attackStep = 1f / (attack.coerceAtLeast(0.0005f) * sr)
        decayCoef = exp(-1.0 / (decay.coerceAtLeast(0.001f) * sr)).toFloat()
        releaseCoef = exp(-1.0 / (release.coerceAtLeast(0.001f) * sr)).toFloat()
        stage = 1
    }

    fun gateOff() {
        if (stage != 0) {
            releaseCoef = exp(-1.0 / (release.coerceAtLeast(0.001f) * sr)).toFloat()
            stage = 3
        }
    }

    fun kill() {
        stage = 0
        level = 0f
    }

    fun next(): Float {
        when (stage) {
            1 -> {
                level += attackStep
                if (level >= 1f) {
                    level = 1f
                    stage = 2
                }
            }
            2 -> level = sustain + (level - sustain) * decayCoef
            3 -> {
                level *= releaseCoef
                if (level < 0.0002f) {
                    level = 0f
                    stage = 0
                }
            }
        }
        return level
    }
}

/**
 * Stereo-Ping-Pong-Echo mit gedämpfter Rückkopplung. Die Verzögerung lässt sich weich
 * verändern (für Tempoänderungen).
 */
class PingPongDelay(private val sr: Int, maxSeconds: Float = 1.6f) {
    private val size = (maxSeconds * sr).toInt() + 2
    private val bufL = FloatArray(size)
    private val bufR = FloatArray(size)
    private var w = 0
    private var delay = sr * 0.3f
    private var targetDelay = delay
    private val dampL = OnePole(sr, 3500f)
    private val dampR = OnePole(sr, 3500f)
    private val hpL = OnePole(sr, 180f)
    private val hpR = OnePole(sr, 180f)

    var feedback = 0.45f

    var outL = 0f
        private set
    var outR = 0f
        private set

    fun setDelaySeconds(s: Float) {
        targetDelay = (s * sr).coerceIn(16f, size - 2f)
    }

    fun setDamping(fc: Float) {
        dampL.setCutoff(fc)
        dampR.setCutoff(fc)
    }

    private fun read(buf: FloatArray, d: Float): Float {
        var p = w - d
        if (p < 0f) p += size
        val i = p.toInt()
        val f = p - i
        val j = if (i + 1 >= size) 0 else i + 1
        return buf[i] + (buf[j] - buf[i]) * f
    }

    fun process(input: Float) {
        delay += (targetDelay - delay) * 0.0002f
        val l = read(bufL, delay)
        val r = read(bufR, delay)
        bufL[w] = hpL.hp(dampL.lp(input + r * feedback))
        bufR[w] = hpR.hp(dampR.lp(l * feedback))
        if (++w >= size) w = 0
        outL = l
        outR = r
    }
}

/** Einfacher Stereo-Hall nach dem Freeverb-Prinzip (Kammfilter + Allpässe). */
class Reverb(
    sampleRate: Int,
    private val roomSize: Float = 0.9f,
    private val damping: Float = 0.35f,
    private val inputGain: Float = 0.012f,
) {
    private val scale = sampleRate / 44100.0

    private inner class Comb(len: Int) {
        val buf = FloatArray((len * scale).toInt().coerceAtLeast(1))
        var idx = 0
        var store = 0f
        fun process(x: Float): Float {
            val out = buf[idx]
            store = out * (1 - damping) + store * damping
            buf[idx] = x + store * roomSize
            if (++idx >= buf.size) idx = 0
            return out
        }
    }

    private inner class AllPass(len: Int) {
        val buf = FloatArray((len * scale).toInt().coerceAtLeast(1))
        var idx = 0
        fun process(x: Float): Float {
            val b = buf[idx]
            buf[idx] = x + b * 0.5f
            if (++idx >= buf.size) idx = 0
            return b - x
        }
    }

    private val combL = intArrayOf(1116, 1188, 1277, 1356, 1422, 1491).map { Comb(it) }.toTypedArray()
    private val combR = intArrayOf(1139, 1211, 1300, 1379, 1445, 1514).map { Comb(it) }.toTypedArray()
    private val apL = intArrayOf(556, 441, 341).map { AllPass(it) }.toTypedArray()
    private val apR = intArrayOf(579, 464, 364).map { AllPass(it) }.toTypedArray()

    var outL = 0f
        private set
    var outR = 0f
        private set

    fun process(input: Float) {
        val x = input * inputGain
        var l = 0f
        var r = 0f
        for (c in combL) l += c.process(x)
        for (c in combR) r += c.process(x)
        for (a in apL) l = a.process(l)
        for (a in apR) r = a.process(r)
        outL = l
        outR = r
    }
}
