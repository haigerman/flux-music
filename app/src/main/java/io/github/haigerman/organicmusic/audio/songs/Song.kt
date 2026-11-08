package io.github.haigerman.organicmusic.audio.songs

import io.github.haigerman.organicmusic.audio.dsp.Compressor
import io.github.haigerman.organicmusic.audio.dsp.Eq
import io.github.haigerman.organicmusic.audio.dsp.FdnReverb
import io.github.haigerman.organicmusic.audio.dsp.OnePole
import io.github.haigerman.organicmusic.audio.dsp.PingPongDelay
import io.github.haigerman.organicmusic.audio.dsp.tapeSaturate
import io.github.haigerman.organicmusic.audio.dsp.Rng
import io.github.haigerman.organicmusic.audio.dsp.smoothstep
import io.github.haigerman.organicmusic.audio.instruments.Instrument
import io.github.haigerman.organicmusic.audio.music.Chord
import io.github.haigerman.organicmusic.audio.music.Key
import io.github.haigerman.organicmusic.audio.music.Melody
import io.github.haigerman.organicmusic.audio.music.Note

/** Teile eines Stücks – die Bezeichnungen erscheinen in der App. */
enum class Part(val label: String) {
    INTRO("Intro"),
    GROOVE("Groove"),
    BUILD("Steigerung"),
    DROP("Höhepunkt"),
    BREAK("Atempause"),
    THEME("Thema"),
    VARIATION("Variation"),
    OUTRO("Ausklang"),
    FLOAT("Schweben"),
    VERSE("Strophe"),
    CHORUS("Refrain"),
    SOLO("Solo"),
    RIFF("Riff"),
}

class Section(val part: Part, val bars: Int)

/** Was gerade läuft – für Anzeige und Benachrichtigung. */
data class SongInfo(
    val key: String = "",
    val chord: String = "",
    val part: String = "",
    val bpm: Int = 0,
)

/**
 * Ein Mischpultkanal: Instrument + Lautstärke, Hall-/Echo-Anteil und wie stark es vom
 * Sidechain "weggedrückt" wird. [target] wird vom Arrangement gesetzt, [level] folgt weich.
 */
class Channel(
    val instrument: Instrument,
    var gain: Float,
    var reverb: Float = 0f,
    var delay: Float = 0f,
    var duck: Float = 0f,
    /** Sekunden für eine volle Ein-/Ausblendung. */
    var fade: Float = 2f,
    /** Klangregelung und Kompressor im Kanal (vor dem Fader). */
    var eq: Eq? = null,
    var comp: Compressor? = null,
    /** Gruppe (z. B. Schlagzeug-Bus); null = direkt auf die Summe. */
    var bus: Bus? = null,
) {
    var target = 0f
    var level = 0f
    internal var silent = true

    /** Ob neue Noten ausgelöst werden sollen. */
    val on: Boolean get() = target > 0.01f
}

/** Gruppen-Bus: mehrere Kanäle gemeinsam bearbeiten (EQ, Kompressor, Sättigung). */
class Bus(
    var gain: Float = 1f,
    var eq: Eq? = null,
    var comp: Compressor? = null,
    var saturation: Float = 0f,
) {
    internal val l = FloatArray(512)
    internal val r = FloatArray(512)
}

/**
 * Grundgerüst eines live komponierten Stücks. Ein Taktgeber läuft in Sechzehnteln
 * (mit optionalem Swing), die Unterklassen entscheiden in [onStep], welche Noten erklingen,
 * und in [makePlan], wie sich das Stück aufbaut. Ist der Plan durchgespielt, beginnt mit
 * [newPiece] ein neuer Teil – mit neuen Mustern, neuer Akkordfolge und oft neuer Tonart.
 * So läuft die Musik endlos, ohne sich zu wiederholen.
 */
abstract class Song(protected val sr: Int, seed: Long, startBpm: Float, startIntensity: Float) {
    protected val rng = Rng(seed)

    @Volatile
    var targetBpm = startBpm

    @Volatile
    var targetIntensity = startIntensity

    var bpm = startBpm
        protected set
    protected var intensity = startIntensity
    protected var swing = 0f

    /** Laufender Sechzehntel-Zähler seit Beginn. */
    protected var step = 0L

    /** Takt innerhalb des aktuellen Teils/Stücks. */
    protected var pieceBar = 0
    protected var barInSection = 0
    protected var sectionIndex = 0
    protected var plan: List<Section> = emptyList()
    protected val section: Section get() = plan[sectionIndex]
    private var started = false

    protected lateinit var key: Key
    protected var progression = intArrayOf(0)
    protected var chordBars = 1
    protected var chordSize = 3

    protected val channels = ArrayList<Channel>()
    protected abstract val reverb: FdnReverb
    protected val busses = ArrayList<Bus>()

    /** Summenbearbeitung: Klangregelung, Kompressor ("Kleber"), Bandsättigung. */
    protected var masterEq: Eq? = null
    protected var masterComp: Compressor? = null
    protected var masterSaturation = 0f
    protected val delay = PingPongDelay(sr)
    protected var delayBeats = 0.75f
    protected var reverbReturn = 1f
    protected var delayReturn = 1f
    protected var delayToReverb = 0.25f

    /** Tiefpass auf der Summe (0 = aus) – z. B. für den warmen Lo-Fi-Klang. */
    protected var toneCutoff = 0f
    private var appliedTone = -1f
    private val toneL = OnePole(sr, 20000f)
    private val toneR = OnePole(sr, 20000f)
    private val rumbleL = OnePole(sr, 28f)
    private val rumbleR = OnePole(sr, 28f)

    /** Lautstärkeabgleich zwischen den Stimmungen (gemessen im SongRenderTest). */
    protected open val outputGain = 1f

    // Sidechain ("Pumpen")
    private var duckT = Int.MAX_VALUE / 2
    protected var duckAttack = 0.004f
    protected var duckRelease = 0.25f

    @Volatile
    var info = SongInfo()
        private set

    /** Aktuelle Position in Vierteln (für die Puls-Animation). */
    @Volatile
    var beatPosition = 0.0
        private set

    private var samplesToStep = 0.0
    private var curStepLen = 1.0

    private val maxBlock = 512
    private val tmpL = FloatArray(maxBlock)
    private val tmpR = FloatArray(maxBlock)
    private val dryL = FloatArray(maxBlock)
    private val dryR = FloatArray(maxBlock)
    private val revBus = FloatArray(maxBlock)
    private val dlyBus = FloatArray(maxBlock)
    private val duckEnv = FloatArray(maxBlock)

    // ---------- von Unterklassen zu füllen ----------

    /** Wählt Tonart, Akkordfolge und Muster für einen neuen Teil. */
    protected abstract fun newPiece(first: Boolean)

    /** Ablauf der Teile für das aktuelle Stück. */
    protected abstract fun makePlan(): List<Section>

    /** Wird zu Beginn jedes Teils aufgerufen. */
    protected open fun onSection(part: Part) {}

    /** Wird zu Beginn jedes Takts aufgerufen (vor [onStep]). */
    protected open fun onBar() {}

    /** Ein Sechzehntel: 0–15 innerhalb des Takts. */
    protected abstract fun onStep(s: Int)

    /** Setzt die Zielpegel der Kanäle aus Teil und Intensität (jedes Viertel aufgerufen). */
    protected abstract fun updateLevels()

    /** Anzeige von Tonart und Akkord (zu Beginn jedes Takts abgefragt). */
    protected open fun keyName(): String = key.name
    protected open fun chordName(): String = chord.name

    // ---------- Hilfen ----------

    protected fun channel(
        instrument: Instrument,
        gain: Float,
        reverb: Float = 0f,
        delay: Float = 0f,
        duck: Float = 0f,
        fade: Float = 2f,
        eq: Eq? = null,
        comp: Compressor? = null,
        bus: Bus? = null,
    ): Channel = Channel(instrument, gain, reverb, delay, duck, fade, eq, comp, bus).also { channels += it }

    protected fun bus(gain: Float = 1f, eq: Eq? = null, comp: Compressor? = null, saturation: Float = 0f): Bus =
        Bus(gain, eq, comp, saturation).also { busses += it }

    protected fun pump() {
        duckT = 0
    }

    /** Akkord, der im Takt [bar] (des Stücks) klingt. */
    protected fun chordAt(bar: Int): Chord {
        val idx = Math.floorMod(bar / chordBars, progression.size)
        return key.chord(progression[idx], chordSize)
    }

    protected val chord: Chord get() = chordAt(pieceBar)

    /** Ist im nächsten Takt ein Akkordwechsel? */
    protected val chordChangesNextBar: Boolean
        get() = (pieceBar + 1) % chordBars == 0

    /** Samples für [steps] Sechzehntel im aktuellen Tempo (ohne Swing). */
    protected fun stepsToSamples(steps: Float): Int = (steps * sr * 15f / bpm).toInt()

    protected fun ms(x: Float): Int = (x * sr / 1000f).toInt()

    /** Zufällige kleine Verzögerung für "menschliches" Timing. */
    protected fun human(maxMs: Float): Int = if (maxMs <= 0f) 0 else rng.int(ms(maxMs) + 1)

    /** 0 → 1, wenn die Intensität um [threshold] liegt (weicher Übergang ±[width]). */
    protected fun above(threshold: Float, width: Float = 0.08f): Float =
        smoothstep(intensity, threshold - width, threshold + width)

    protected val barsLeftInSection: Int get() = section.bars - barInSection

    /** Fortschritt im aktuellen Teil, 0 → 1, auf das Sechzehntel genau. */
    protected fun sectionProgress(s: Int): Float = (barInSection * 16 + s) / (section.bars * 16f)

    // ---------- Melodie-Hooks ----------

    /** Eine Phrase je 4 Takte des Akkordzyklus – sie kehrt bei jedem Durchlauf wieder. */
    private var hooks: Array<Array<Note?>> = emptyArray()

    protected fun buildHooks(
        low: Int,
        high: Int,
        notesPerHalf: Int,
        syncopation: Float,
        pentatonic: Boolean,
        maxLength: Int = 8,
        velocity: Float = 0.8f,
    ) {
        val cycle = chordBars * progression.size
        val blocks = maxOf(1, cycle / 4)
        var last: Int? = null
        hooks = Array(blocks) { b ->
            val chords = (0 until 4).map { chordAt(b * 4 + it) }
            val ph = Melody.phrase(rng, key, chords, low, high, notesPerHalf, syncopation, pentatonic, last, maxLength, velocity)
            last = ph.lastOrNull()?.midi
            arrayOfNulls<Note>(64).also { arr -> ph.forEach { if (it.step < 64) arr[it.step] = it } }
        }
    }

    /** Hook-Note, die auf diesem Sechzehntel beginnt (oder null). */
    protected fun hookNote(s: Int): Note? {
        if (hooks.isEmpty()) return null
        val cycle = chordBars * progression.size
        val block = ((pieceBar % maxOf(cycle, 4)) / 4) % hooks.size
        return hooks[block][(pieceBar % 4) * 16 + s]
    }

    // ---------- Taktgeber ----------

    private fun stepLength(s: Long): Double {
        val base = sr * 15.0 / bpm
        return if (s % 2 == 0L) base * (1 + swing) else base * (1 - swing)
    }

    private fun doStep(): Long {
        if (!started) {
            started = true
            newPiece(true)
            plan = makePlan()
            sectionIndex = 0
            barInSection = 0
            pieceBar = 0
            onSection(section.part)
        }
        val s = (step % 16).toInt()
        if (s == 0 && step > 0) advanceBar()
        if (s % 4 == 0) {
            // Tempo und Intensität gleiten weich zum Zielwert
            val tb = targetBpm
            bpm += (tb - bpm).coerceIn(-3f, 3f)
            val ti = targetIntensity
            intensity += (ti - intensity).coerceIn(-0.08f, 0.08f)
            delay.setDelaySeconds(delayBeats * 60f / bpm)
            updateLevels()
        }
        if (s == 0) {
            onBar()
            info = SongInfo(keyName(), chordName(), section.part.label, Math.round(bpm))
        }
        onStep(s)
        return step++
    }

    private fun advanceBar() {
        barInSection++
        pieceBar++
        if (barInSection >= section.bars) {
            barInSection = 0
            sectionIndex++
            if (sectionIndex >= plan.size) {
                newPiece(false)
                plan = makePlan()
                sectionIndex = 0
                pieceBar = 0
            }
            onSection(section.part)
        }
    }

    /** Überschreibt [left]/[right] mit [frames] neuen Samples. */
    fun render(left: FloatArray, right: FloatArray, frames: Int) {
        var pos = 0
        while (pos < frames) {
            if (samplesToStep < 1.0) {
                val s = doStep()
                curStepLen = stepLength(s)
                samplesToStep += curStepLen
            }
            val n = minOf(frames - pos, maxBlock, maxOf(1, samplesToStep.toInt()))
            renderSegment(left, right, pos, n)
            samplesToStep -= n
            pos += n
        }
        beatPosition = (step - samplesToStep / curStepLen) / 4.0
    }

    private fun renderSegment(left: FloatArray, right: FloatArray, off: Int, n: Int) {
        java.util.Arrays.fill(dryL, 0, n, 0f)
        java.util.Arrays.fill(dryR, 0, n, 0f)
        java.util.Arrays.fill(revBus, 0, n, 0f)
        java.util.Arrays.fill(dlyBus, 0, n, 0f)

        // Sidechain-Hüllkurve: 0 = kein Ducking, 1 = voll
        val att = duckAttack * sr
        val rel = duckRelease * sr
        for (i in 0 until n) {
            val t = duckT + i
            duckEnv[i] = when {
                t < att -> t / att
                t < att + rel -> {
                    val x = 1f - (t - att) / rel
                    x * x
                }
                else -> 0f
            }
        }
        duckT = (duckT + n).coerceAtMost(Int.MAX_VALUE / 2)

        for (ch in channels) {
            val from = ch.level
            val step = n / (ch.fade.coerceAtLeast(0.01f) * sr)
            val to = if (from < ch.target) minOf(ch.target, from + step) else maxOf(ch.target, from - step)
            ch.level = to
            if (from <= 0f && to <= 0f) {
                if (!ch.silent) {
                    ch.instrument.reset()
                    ch.silent = true
                }
                continue
            }
            ch.silent = false
            java.util.Arrays.fill(tmpL, 0, n, 0f)
            java.util.Arrays.fill(tmpR, 0, n, 0f)
            ch.instrument.render(tmpL, tmpR, n)
            ch.eq?.process(tmpL, tmpR, n)
            ch.comp?.process(tmpL, tmpR, n)
            val g0 = from * ch.gain
            val g1 = to * ch.gain
            val inv = 1f / n
            val b = ch.bus
            val outL = b?.l ?: dryL
            val outR = b?.r ?: dryR
            for (i in 0 until n) {
                var g = g0 + (g1 - g0) * (i * inv)
                if (ch.duck > 0f) g *= 1f - ch.duck * duckEnv[i]
                val l = tmpL[i] * g
                val r = tmpR[i] * g
                outL[i] += l
                outR[i] += r
                if (ch.reverb > 0f) revBus[i] += (l + r) * ch.reverb
                if (ch.delay > 0f) dlyBus[i] += (l + r) * ch.delay
            }
        }
        for (b in busses) {
            b.eq?.process(b.l, b.r, n)
            b.comp?.process(b.l, b.r, n)
            if (b.saturation > 0f) tapeSaturate(b.l, b.r, n, b.saturation)
            val g = b.gain
            for (i in 0 until n) {
                dryL[i] += b.l[i] * g
                dryR[i] += b.r[i] * g
            }
            java.util.Arrays.fill(b.l, 0, n, 0f)
            java.util.Arrays.fill(b.r, 0, n, 0f)
        }

        val og = outputGain
        val tone = toneCutoff > 0f
        if (tone && toneCutoff != appliedTone) {
            toneL.setCutoff(toneCutoff)
            toneR.setCutoff(toneCutoff)
            appliedTone = toneCutoff
        }
        for (i in 0 until n) {
            delay.process(dlyBus[i])
            reverb.process(revBus[i] + (delay.outL + delay.outR) * delayToReverb)
            dryL[i] += reverb.outL * reverbReturn + delay.outL * delayReturn
            dryR[i] += reverb.outR * reverbReturn + delay.outR * delayReturn
        }
        masterEq?.process(dryL, dryR, n)
        masterComp?.process(dryL, dryR, n)
        if (masterSaturation > 0f) tapeSaturate(dryL, dryR, n, masterSaturation)
        for (i in 0 until n) {
            var l = dryL[i] * og
            var r = dryR[i] * og
            if (tone) {
                l = toneL.lp(l)
                r = toneR.lp(r)
            }
            l = rumbleL.hp(l)
            r = rumbleR.hp(r)
            left[off + i] = l
            right[off + i] = r
        }
    }
}
