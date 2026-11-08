package io.github.haigerman.organicmusic.audio.instruments

import io.github.haigerman.organicmusic.audio.dsp.OnePole
import io.github.haigerman.organicmusic.audio.dsp.Rng
import io.github.haigerman.organicmusic.audio.dsp.Sine
import io.github.haigerman.organicmusic.audio.dsp.decayCoef
import io.github.haigerman.organicmusic.audio.samples.SampleData
import io.github.haigerman.organicmusic.audio.samples.Zone
import kotlin.math.pow

/**
 * Spielt echte Instrumentenaufnahmen: wählt je Note die passende Aufnahme (Tonbereich,
 * Anschlagstärke, Variante), transponiert sie mit kubischer Interpolation exakt auf die
 * gewünschte Tonhöhe und formt Ein-/Ausklang. Gehaltene Töne laufen über nahtlose Loops.
 */
class Sampler(sr: Int, private val rng: Rng, val data: SampleData, voices: Int = 32) : Instrument(sr) {
    var gain = 1f
    /** Grundposition im Stereobild (-1 … 1). */
    var pan = 0f
    /** Zufällige Streuung im Stereobild je Note (0 … 1), nur für Mono-Aufnahmen. */
    var spread = 0f
    /** Einschwingzeit in Sekunden (0 = Aufnahme unverändert). */
    var attack = 0.0015f
    /** Ausklingzeit nach dem Loslassen (bis -60 dB) in Sekunden. */
    var release = data.release * 6f
    /** Lautstärke über der Anschlagstärke: bei nur einer Aufnahmestufe vel^curve. */
    var velocityCurve = 1.5f
    /** Spielt dieselbe Taste erneut, klingt der alte Ton so schnell aus (Sekunden, < 0 = nie). */
    var retrigger = 0.25f
    var tuneCents = 0f
    /** Gleitende Tonhöhenänderung aller klingenden Noten in Cent (z. B. Band-Leiern). */
    var pitchModCents = 0f
    /** Tiefpass auf dem Ausgang (Hz, 0 = aus) – z. B. für warmen Lo-Fi-Klang. */
    var toneHz = 0f
        set(v) {
            field = v
            if (v > 0f) {
                toneL.setCutoff(v); toneR.setCutoff(v)
            }
        }

    /** Tasten derselben Gruppe stoppen sich gegenseitig (z. B. offene/geschlossene Hi-Hat). */
    val chokeGroups = HashMap<Int, Int>()

    private val ch = data.channels
    private val pcm = data.pcm
    private val rate = data.sampleRate.toDouble() / sr
    private val toneL = OnePole(sr, 20000f)
    private val toneR = OnePole(sr, 20000f)

    /** Zonen je Taste, für schnelle Auswahl. */
    private val byKey: Array<Array<Zone>> = Array(128) { k ->
        data.zones.filter { k in it.lo..it.hi }.toTypedArray()
    }

    /** Anzahl Anschlag-Stufen je Taste. */
    private val layers = IntArray(128) { k -> byKey[k].map { it.v0 }.distinct().size }
    private val lastRr = HashMap<Long, Int>()

    private inner class Voice {
        var zone: Zone? = null
        var active = false
        var key = -1
        var pos = 0.0
        var baseInc = 1.0
        var inc = 1.0
        var env = 0f
        var attackInc = 1f
        var releasing = false
        var relCoef = 0.99f
        var gate = -1
        var amp = 1f
        var gl = 1f
        var gr = 1f
        var bend = 0f
        var bendCoef = 0f
        var vib = 0f
        var vibPhase = 0f
        var vibWait = 0
        var counter = 0
        var age = 0L
        var lastL = 0f
        var lastR = 0f
        // ausstehender Start
        var wait = -1
        var pKey = 0
        var pVel = 0f
        var pLen = -1
        var pPan = 0f
        var pBend = 0f
        var pBendMs = 80f
        var pVib = 0f
        var pGain = 1f

        fun release(seconds: Float) {
            if (!active) return
            releasing = true
            relCoef = decayCoef(seconds.coerceAtLeast(0.002f), sr)
        }
    }

    private val pool = Array(voices) { Voice() }
    private var clock = 0L
    private var declickL = 0f
    private var declickR = 0f
    private val declickCoef = decayCoef(0.004f, sr)

    /**
     * Note spielen. [vel] 0–1, [lengthSamples] Haltedauer (-1 = ausklingen lassen),
     * [bendFrom] Halbtöne unter der Zielnote anfangen und hinziehen, [vibrato] in Halbtönen.
     */
    fun noteOn(
        key: Int,
        vel: Float,
        lengthSamples: Int = -1,
        delay: Int = 0,
        pan: Float = Float.NaN,
        bendFrom: Float = 0f,
        bendMs: Float = 90f,
        vibrato: Float = 0f,
        gainMul: Float = 1f,
    ) {
        if (key !in 0..127 || byKey[key].isEmpty()) return
        val v = pool.firstOrNull { !it.active && it.wait < 0 } ?: steal()
        v.wait = delay.coerceAtLeast(0)
        v.pKey = key
        v.pVel = vel.coerceIn(0.01f, 1f)
        v.pLen = lengthSamples
        v.pPan = if (pan.isNaN()) this.pan + rng.bipolar() * spread else pan
        v.pBend = bendFrom
        v.pBendMs = bendMs
        v.pVib = vibrato
        v.pGain = gainMul
    }

    /** Alle klingenden Noten loslassen (z. B. Pedal heben). */
    fun releaseAll(seconds: Float = release) {
        for (v in pool) if (v.active) v.release(seconds)
    }

    /** Noten einer Taste loslassen. */
    fun noteOff(key: Int, seconds: Float = release) {
        for (v in pool) if (v.active && v.key == key) v.release(seconds)
    }

    private fun steal(): Voice {
        // die leiseste bzw. älteste Stimme übernehmen; ihr letzter Wert wird weich ausgeblendet
        var best = pool[0]
        var bestScore = Float.MAX_VALUE
        for (v in pool) {
            if (v.wait >= 0) continue
            val score = v.env * v.amp * (if (v.releasing) 0.3f else 1f) - (clock - v.age) * 1e-9f
            if (score < bestScore) {
                bestScore = score
                best = v
            }
        }
        return best
    }

    private fun pickZone(key: Int, vel: Float): Zone {
        val zones = byKey[key]
        val v = (vel * 127f).toInt().coerceIn(1, 127)
        var cands = zones.filter { v in it.v0..it.v1 }
        if (cands.isEmpty()) cands = listOf(zones.minBy { minOf(kotlin.math.abs(it.v0 - v), kotlin.math.abs(it.v1 - v)) })
        if (cands.size == 1) return cands[0]
        val id = cands[0].key * 1000L + cands[0].v0
        val last = lastRr[id] ?: -1
        var z = cands[rng.int(cands.size)]
        if (z.rr == last) z = cands[(cands.indexOf(z) + 1 + rng.int(cands.size - 1)) % cands.size]
        lastRr[id] = z.rr
        return z
    }

    private fun start(v: Voice) {
        // Klingt die Stimme noch, ihren Rest weich ausblenden (kein Knacksen)
        if (v.active) {
            declickL += v.lastL
            declickR += v.lastR
        }
        v.lastL = 0f
        v.lastR = 0f
        val key = v.pKey
        if (retrigger >= 0f) {
            for (o in pool) if (o !== v && o.active && o.key == key && !o.releasing) o.release(retrigger)
        }
        chokeGroups[key]?.let { g ->
            for (o in pool) if (o !== v && o.active && chokeGroups[o.key] == g) o.release(0.04f)
        }
        val z = pickZone(key, v.pVel)
        v.zone = z
        v.key = key
        v.pos = 0.0
        v.baseInc = rate * 2.0.pow((key - z.root + tuneCents / 100.0) / 12.0)
        v.inc = v.baseInc
        v.env = if (attack > 0f) 0f else 1f
        v.attackInc = if (attack > 0f) 1f / (attack * sr) else 1f
        v.releasing = false
        v.gate = v.pLen
        val vel = v.pVel
        val layerGain = if (layers[key] > 1) {
            val frac = ((vel * 127f) - z.v0) / (z.v1 - z.v0 + 1f)
            0.72f + 0.28f * frac.coerceIn(0f, 1f)
        } else vel.pow(velocityCurve)
        v.amp = layerGain * v.pGain
        val p = v.pPan.coerceIn(-1f, 1f)
        if (ch == 1) {
            v.gl = panL(p); v.gr = panR(p)
        } else {
            v.gl = minOf(1f, 1f - p); v.gr = minOf(1f, 1f + p)
        }
        v.bend = -v.pBend
        v.bendCoef = decayCoef(v.pBendMs / 1000f, sr).pow(16)
        v.vib = v.pVib
        v.vibPhase = 0f
        v.vibWait = (0.15f * sr).toInt()
        v.counter = 0
        v.age = clock
        v.active = true
    }

    override fun reset() {
        for (v in pool) {
            v.active = false
            v.wait = -1
        }
        declickL = 0f
        declickR = 0f
    }

    override fun render(l: FloatArray, r: FloatArray, n: Int) {
        val g = gain
        val stereo = ch == 2
        val mod = if (pitchModCents != 0f) 2.0.pow(pitchModCents / 1200.0) else 1.0
        for (v in pool) {
            if (!v.active && v.wait < 0) continue
            for (i in 0 until n) {
                if (v.wait >= 0) {
                    if (v.wait == 0) start(v)
                    v.wait--
                }
                if (!v.active) {
                    if (v.wait >= 0) continue else break
                }
                val z = v.zone ?: break
                // Tonhöhe (Bending/Vibrato) alle 16 Samples nachführen
                if (v.counter == 0 && (v.bend != 0f || v.vib > 0f)) {
                    v.bend *= v.bendCoef
                    if (kotlin.math.abs(v.bend) < 0.001f) v.bend = 0f
                    var semis = v.bend
                    if (v.vib > 0f) {
                        if (v.vibWait > 0) v.vibWait -= 16 else {
                            v.vibPhase += 16f * 5.5f / sr
                            if (v.vibPhase >= 1f) v.vibPhase -= 1f
                            semis += v.vib * Sine.at(v.vibPhase)
                        }
                    }
                    v.inc = v.baseInc * 2.0.pow(semis / 12.0)
                }
                v.counter = (v.counter + 1) and 15

                if (v.gate > 0 && --v.gate == 0) v.release(release)

                val p = v.pos
                val ip = p.toInt()
                val f = (p - ip).toFloat()
                val e: Float
                if (v.releasing) {
                    v.env *= v.relCoef
                    e = v.env
                    if (e < 0.00005f) {
                        v.active = false
                        continue
                    }
                } else {
                    if (v.env < 1f) v.env = minOf(1f, v.env + v.attackInc)
                    e = v.env
                }
                val a = e * v.amp * g
                val base = z.pos
                val last = z.len - 1
                val i0 = if (ip > 0) ip - 1 else 0
                val i2: Int
                val i3: Int
                if (z.looped) {
                    // über die Loop-Naht hinweg lesen (nahtlos)
                    val span = z.loopEnd - z.loopStart
                    i2 = if (ip + 1 >= z.loopEnd) ip + 1 - span else ip + 1
                    i3 = if (ip + 2 >= z.loopEnd) ip + 2 - span else ip + 2
                } else {
                    i2 = if (ip + 1 <= last) ip + 1 else last
                    i3 = if (ip + 2 <= last) ip + 2 else last
                }
                if (stereo) {
                    val sl = hermite(pcm.get((base + i0) * 2), pcm.get((base + ip) * 2), pcm.get((base + i2) * 2), pcm.get((base + i3) * 2), f)
                    val sr2 = hermite(pcm.get((base + i0) * 2 + 1), pcm.get((base + ip) * 2 + 1), pcm.get((base + i2) * 2 + 1), pcm.get((base + i3) * 2 + 1), f)
                    val ol = sl * a * v.gl
                    val or = sr2 * a * v.gr
                    l[i] += ol
                    r[i] += or
                    v.lastL = ol
                    v.lastR = or
                } else {
                    val s = hermite(pcm.get(base + i0), pcm.get(base + ip), pcm.get(base + i2), pcm.get(base + i3), f) * a
                    val ol = s * v.gl
                    val or = s * v.gr
                    l[i] += ol
                    r[i] += or
                    v.lastL = ol
                    v.lastR = or
                }
                var np = p + v.inc * mod
                if (z.looped) {
                    if (np >= z.loopEnd) np -= (z.loopEnd - z.loopStart)
                } else if (np >= last - 2) {
                    v.active = false
                    v.pos = np
                    continue
                }
                v.pos = np
            }
        }
        // Reste gestohlener Stimmen ausblenden
        if (declickL != 0f || declickR != 0f) {
            for (i in 0 until n) {
                declickL *= declickCoef
                declickR *= declickCoef
                l[i] += declickL
                r[i] += declickR
            }
            if (kotlin.math.abs(declickL) < 1e-6f) declickL = 0f
            if (kotlin.math.abs(declickR) < 1e-6f) declickR = 0f
        }
        if (toneHz > 0f) {
            for (i in 0 until n) {
                l[i] = toneL.lp(l[i])
                r[i] = toneR.lp(r[i])
            }
        }
        clock += n
    }

    /** Kubische Hermite-Interpolation zwischen x0 und x1 (16-Bit-Werte → ±1). */
    private fun hermite(xm1: Short, x0: Short, x1: Short, x2: Short, t: Float): Float {
        val a = xm1.toFloat()
        val b = x0.toFloat()
        val c = x1.toFloat()
        val d = x2.toFloat()
        val c1 = 0.5f * (c - a)
        val c2 = a - 2.5f * b + 2f * c - 0.5f * d
        val c3 = 0.5f * (d - a) + 1.5f * (b - c)
        return (((c3 * t + c2) * t + c1) * t + b) * (1f / 32768f)
    }
}
