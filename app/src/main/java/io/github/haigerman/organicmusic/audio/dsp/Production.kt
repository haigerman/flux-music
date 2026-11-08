package io.github.haigerman.organicmusic.audio.dsp

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Kuhschwanz-Filter (Shelving) und Glocke für die Klangregelung. */
class Shelf(private val sr: Int) {
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
        b0 = (nb0 / na0).toFloat(); b1 = (nb1 / na0).toFloat(); b2 = (nb2 / na0).toFloat()
        a1 = (na1 / na0).toFloat(); a2 = (na2 / na0).toFloat()
    }

    fun lowShelf(f: Float, gainDb: Float, s: Float = 0.8f): Shelf {
        val a = 10.0.pow(gainDb / 40.0)
        val w = TWO_PI * f / sr
        val c = cos(w)
        val alpha = sin(w) / 2 * sqrt((a + 1 / a) * (1 / s - 1) + 2)
        val sa = 2 * sqrt(a) * alpha
        set(a * ((a + 1) - (a - 1) * c + sa), 2 * a * ((a - 1) - (a + 1) * c), a * ((a + 1) - (a - 1) * c - sa),
            (a + 1) + (a - 1) * c + sa, -2 * ((a - 1) + (a + 1) * c), (a + 1) + (a - 1) * c - sa)
        return this
    }

    fun highShelf(f: Float, gainDb: Float, s: Float = 0.8f): Shelf {
        val a = 10.0.pow(gainDb / 40.0)
        val w = TWO_PI * f / sr
        val c = cos(w)
        val alpha = sin(w) / 2 * sqrt((a + 1 / a) * (1 / s - 1) + 2)
        val sa = 2 * sqrt(a) * alpha
        set(a * ((a + 1) + (a - 1) * c + sa), -2 * a * ((a - 1) + (a + 1) * c), a * ((a + 1) + (a - 1) * c - sa),
            (a + 1) - (a - 1) * c + sa, 2 * ((a - 1) - (a + 1) * c), (a + 1) - (a - 1) * c - sa)
        return this
    }

    fun peak(f: Float, q: Float, gainDb: Float): Shelf {
        val a = 10.0.pow(gainDb / 40.0)
        val w = TWO_PI * f / sr
        val alpha = sin(w) / (2.0 * q)
        val c = cos(w)
        set(1 + alpha * a, -2 * c, 1 - alpha * a, 1 + alpha / a, -2 * c, 1 - alpha / a)
        return this
    }

    fun highpass(f: Float, q: Float = 0.707f): Shelf {
        val w = TWO_PI * f / sr
        val alpha = sin(w) / (2.0 * q)
        val c = cos(w)
        set((1 + c) / 2, -(1 + c), (1 + c) / 2, 1 + alpha, -2 * c, 1 - alpha)
        return this
    }

    fun lowpass(f: Float, q: Float = 0.707f): Shelf {
        val w = TWO_PI * f.coerceAtMost(sr * 0.45f) / sr
        val alpha = sin(w) / (2.0 * q)
        val c = cos(w)
        set((1 - c) / 2, 1 - c, (1 - c) / 2, 1 + alpha, -2 * c, 1 - alpha)
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
 * Stereo-EQ aus beliebig vielen Filtern (links/rechts getrennte Zustände).
 * Aufbau z. B.: Eq(sr).highpass(80f).lowShelf(200f, -2f).peak(3000f, 1f, 2f).highShelf(9000f, 1.5f)
 */
class Eq(private val sr: Int) {
    private val left = ArrayList<Shelf>()
    private val right = ArrayList<Shelf>()

    private fun add(cfg: (Shelf) -> Unit): Eq {
        left += Shelf(sr).also(cfg)
        right += Shelf(sr).also(cfg)
        return this
    }

    fun highpass(f: Float, q: Float = 0.707f) = add { it.highpass(f, q) }
    fun lowpass(f: Float, q: Float = 0.707f) = add { it.lowpass(f, q) }
    fun lowShelf(f: Float, db: Float) = add { it.lowShelf(f, db) }
    fun highShelf(f: Float, db: Float) = add { it.highShelf(f, db) }
    fun peak(f: Float, q: Float, db: Float) = add { it.peak(f, q, db) }

    fun process(l: FloatArray, r: FloatArray, n: Int) {
        for (k in left.indices) {
            val fl = left[k]
            val fr = right[k]
            for (i in 0 until n) {
                l[i] = fl.process(l[i])
                r[i] = fr.process(r[i])
            }
        }
    }
}

/**
 * Stereo-Kompressor (verknüpfte Kanäle) mit weichem Knie. Hält Dynamik zusammen und lässt
 * Instrumente "kleben" – wie im Studio. [mix] < 1 ergibt Parallel-Kompression.
 */
class Compressor(
    private val sr: Int,
    var thresholdDb: Float = -18f,
    var ratio: Float = 3f,
    attackMs: Float = 10f,
    releaseMs: Float = 120f,
    var kneeDb: Float = 6f,
    var makeupDb: Float = 0f,
    var mix: Float = 1f,
) {
    private var atk = 0f
    private var rel = 0f
    private var env = 0f
    private var gainDb = 0f
    private var counter = 0

    /** Aktuelle Pegelreduktion in dB (zum Prüfen). */
    var reductionDb = 0f
        private set

    init {
        setTimes(attackMs, releaseMs)
    }

    fun setTimes(attackMs: Float, releaseMs: Float) {
        atk = exp(-1.0 / (attackMs * 0.001 * sr)).toFloat()
        rel = exp(-1.0 / (releaseMs * 0.001 * sr)).toFloat()
    }

    private fun computeGain(levelDb: Float): Float {
        val over = levelDb - thresholdDb
        val red = when {
            over <= -kneeDb / 2 -> 0f
            over >= kneeDb / 2 -> over * (1f - 1f / ratio)
            else -> {
                val x = over + kneeDb / 2
                (1f - 1f / ratio) * x * x / (2f * kneeDb)
            }
        }
        return -red
    }

    fun process(l: FloatArray, r: FloatArray, n: Int) {
        val makeup = 10f.pow(makeupDb / 20f)
        var g = 10f.pow(gainDb / 20f)
        for (i in 0 until n) {
            val x = maxOf(abs(l[i]), abs(r[i]))
            env = if (x > env) atk * env + (1 - atk) * x else rel * env + (1 - rel) * x
            if (counter == 0) {
                val lvl = if (env > 1e-6f) 20f * log10(env) else -120f
                gainDb = computeGain(lvl)
                reductionDb = gainDb
                g = 10f.pow(gainDb / 20f)
            }
            counter = (counter + 1) and 7
            val k = g * makeup
            if (mix >= 1f) {
                l[i] *= k
                r[i] *= k
            } else {
                l[i] = l[i] * (1f - mix) + l[i] * k * mix
                r[i] = r[i] * (1f - mix) + r[i] * k * mix
            }
        }
    }
}

/** Sanfte Bandsättigung: rundet Spitzen ab und gibt Wärme (Stereo, in-place). */
fun tapeSaturate(l: FloatArray, r: FloatArray, n: Int, drive: Float) {
    if (drive <= 0f) return
    val k = 1f + drive * 3f
    val norm = 1f / softClipCurve(k)
    for (i in 0 until n) {
        l[i] = softClipCurve(l[i] * k) * norm
        r[i] = softClipCurve(r[i] * k) * norm
    }
}

private fun softClipCurve(x: Float): Float {
    val c = x.coerceIn(-3f, 3f)
    return c * (27f + c * c) / (27f + 9f * c * c)
}

/**
 * Hall nach dem Feedback-Delay-Network-Prinzip: 8 leicht modulierte Verzögerungsleitungen,
 * gemischt über eine Hadamard-Matrix, mit frequenzabhängiger Dämpfung, Vorverzögerung und
 * frühen Reflexionen. Klingt dicht und natürlich, ohne das metallische Flattern einfacher Hallgeräte.
 */
class FdnReverb(
    private val sr: Int,
    decaySeconds: Float = 2.2f,
    /** Dämpfung der Höhen: Grenzfrequenz im Nachhall. */
    dampHz: Float = 6000f,
    predelayMs: Float = 18f,
    /** Raumgröße 0.5–1.5 (skaliert die Leitungslängen). */
    size: Float = 1f,
    /** Anteil früher Reflexionen. */
    var early: Float = 0.25f,
    var width: Float = 1f,
) {
    private val baseMs = floatArrayOf(29.7f, 37.1f, 41.1f, 43.7f, 53.3f, 59.9f, 67.1f, 73.7f)
    private val n = 8
    private val lens = IntArray(n) { (baseMs[it] * size * sr / 1000f).toInt() }
    private val bufs = Array(n) { FloatArray(lens[it] + 64) }
    private val idx = IntArray(n)
    private val damp = FloatArray(n)
    private val gains = FloatArray(n)
    private var dampA = 0f
    private val lfo = FloatArray(n) { it * 0.37f }
    private val lfoInc = FloatArray(n) { (0.07f + it * 0.013f) / sr }
    private val modDepth = 0.0012f * sr

    private val preLen = (predelayMs * sr / 1000f).toInt().coerceAtLeast(1)
    private val pre = FloatArray(preLen + 1)
    private var preIdx = 0

    // frühe Reflexionen: Abgriffe einer kurzen Leitung
    private val erLen = (0.09f * sr).toInt()
    private val er = FloatArray(erLen)
    private var erIdx = 0
    private val erTapsL = intArrayOf(7, 13, 23, 31, 43, 59, 71).map { (it * sr / 1000f).toInt() }.toIntArray()
    private val erTapsR = intArrayOf(9, 17, 27, 37, 47, 61, 79).map { (it * sr / 1000f).toInt() }.toIntArray()
    private val erGain = floatArrayOf(0.8f, 0.65f, 0.55f, 0.45f, 0.35f, 0.28f, 0.2f)

    private val tmp = FloatArray(n)

    var outL = 0f
        private set
    var outR = 0f
        private set

    init {
        setDecay(decaySeconds, dampHz)
    }

    fun setDecay(seconds: Float, dampHz: Float) {
        for (i in 0 until n) {
            // Verstärkung je Durchlauf für die gewünschte Nachhallzeit (-60 dB)
            gains[i] = 10.0.pow(-3.0 * lens[i] / (seconds * sr)).toFloat()
        }
        dampA = exp(-TWO_PI * dampHz / sr).toFloat()
    }

    fun process(input: Float) {
        // Vorverzögerung
        val x = pre[preIdx]
        pre[preIdx] = input
        if (++preIdx > preLen) preIdx = 0

        // frühe Reflexionen
        er[erIdx] = x
        var el = 0f
        var erR = 0f
        for (t in erTapsL.indices) {
            var p = erIdx - erTapsL[t]
            if (p < 0) p += erLen
            el += er[p] * erGain[t]
            var q = erIdx - erTapsR[t]
            if (q < 0) q += erLen
            erR += er[q] * erGain[t]
        }
        if (++erIdx >= erLen) erIdx = 0

        // Leitungen lesen (moduliert, linear interpoliert)
        for (i in 0 until n) {
            lfo[i] += lfoInc[i]
            if (lfo[i] >= 1f) lfo[i] -= 1f
            val d = lens[i] - modDepth * (1f + Sine.at(lfo[i]))
            val buf = bufs[i]
            var rp = idx[i] - d
            val size = buf.size
            while (rp < 0f) rp += size
            var a = rp.toInt()
            if (a >= size) a -= size // Float-Rundung knapp unter der Puffergröße
            val f = rp - a
            val b = if (a + 1 >= size) 0 else a + 1
            val v = buf[a] + (buf[b] - buf[a]) * f
            // Höhendämpfung im Rückkopplungsweg
            damp[i] = v * (1f - dampA) + damp[i] * dampA
            tmp[i] = damp[i] * gains[i]
        }
        // Hadamard-Mischung (schnell, verlustfrei)
        var h = 1
        while (h < n) {
            var i = 0
            while (i < n) {
                for (j in i until i + h) {
                    val a = tmp[j]
                    val b = tmp[j + h]
                    tmp[j] = a + b
                    tmp[j + h] = a - b
                }
                i += h * 2
            }
            h *= 2
        }
        val norm = 0.35355339f // 1/sqrt(8)
        for (i in 0 until n) {
            val buf = bufs[i]
            buf[idx[i]] = tmp[i] * norm + x * (if (i % 2 == 0) 0.5f else -0.5f)
            if (++idx[i] >= buf.size) idx[i] = 0
        }
        var l = 0f
        var r = 0f
        for (i in 0 until n) {
            if (i % 2 == 0) l += damp[i] else r += damp[i]
        }
        l *= 0.5f
        r *= 0.5f
        val mid = (l + r) * 0.5f
        val side = (l - r) * 0.5f * width
        outL = mid + side + el * early * 0.3f
        outR = mid - side + erR * early * 0.3f
    }
}
