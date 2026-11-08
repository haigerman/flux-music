package io.github.haigerman.organicmusic.audio.songs

import io.github.haigerman.organicmusic.audio.dsp.Compressor
import io.github.haigerman.organicmusic.audio.dsp.Drift
import io.github.haigerman.organicmusic.audio.dsp.Eq
import io.github.haigerman.organicmusic.audio.dsp.FdnReverb
import io.github.haigerman.organicmusic.audio.instruments.Sampler
import io.github.haigerman.organicmusic.audio.instruments.Waves
import io.github.haigerman.organicmusic.audio.music.Key
import io.github.haigerman.organicmusic.audio.music.Scale
import io.github.haigerman.organicmusic.audio.samples.SampleBank
import kotlin.math.abs
import kotlin.math.sin

/**
 * Entspannen / Einschlafen: ruhige Kammermusik ohne Schlagzeug, mit echten Instrumenten.
 * Ein Konzertflügel spielt wie ein Pianist mit Pedal – linke Hand Grundton und Quinte,
 * rechte Hand freie Melodiebögen mit Rubato und Dynamik. Darunter ein Streicherensemble
 * (Celli, Bratschen, Violinen), Kontrabass, gelegentlich Harfe und Glockenspiel – in einem
 * großen, warmen Saal.
 *
 * Mit [sleep] langsamer, tiefer und dunkler; die Melodie zieht sich fast ganz zurück.
 */
class AmbientSong(
    sr: Int,
    seed: Long,
    bpm: Float,
    intensity: Float,
    bank: SampleBank,
    private val sleep: Boolean,
) : Song(sr, seed, bpm, intensity) {

    override val reverb = FdnReverb(
        sr, decaySeconds = if (sleep) 4.8f else 3.6f, dampHz = if (sleep) 3200f else 5500f,
        predelayMs = 28f, size = 1.25f, early = 0.3f,
    )
    override val outputGain = if (sleep) 3.1f else 2.6f

    private val piano = Sampler(sr, rng, bank["piano"], voices = 48).apply {
        release = 1.4f; retrigger = 0.35f; velocityCurve = 1.2f
    }
    private val violins = if (bank.has("violins")) Sampler(sr, rng, bank["violins"], 12).apply { attack = 1.6f; release = 3.5f; pan = 0.25f } else null
    private val violas = Sampler(sr, rng, bank["violas"], 12).apply { attack = 1.8f; release = 3.5f; pan = -0.1f }
    private val cellos = Sampler(sr, rng, bank["cellos"], 12).apply { attack = 2f; release = 4f; pan = -0.3f }
    private val contrabass = Sampler(sr, rng, bank["contrabass"], 6).apply { attack = 2.2f; release = 4f; pan = -0.05f }
    private val harp = Sampler(sr, rng, bank["harp"], 24).apply { release = 3f; spread = 0.5f; velocityCurve = 1.3f }
    private val glock = if (bank.has("glock")) Sampler(sr, rng, bank["glock"], 8).apply { release = 3f; spread = 0.7f } else null
    private val waves = Waves(sr, rng)

    private val pianoEq = Eq(sr).highpass(45f).lowShelf(250f, if (sleep) 1.5f else 0f).highShelf(6000f, if (sleep) -6f else -1.5f)
    private val stringEq = Eq(sr).highpass(60f).peak(350f, 0.8f, -1.5f).highShelf(7000f, if (sleep) -5f else -1f)

    private val chPiano = channel(piano, 0.85f, reverb = 0.42f, delay = 0f, fade = 3f, eq = pianoEq)
    private val chViolins = violins?.let { channel(it, 0.45f, reverb = 0.6f, fade = 6f, eq = Eq(sr).highpass(150f).highShelf(7000f, -2f)) }
    private val chViolas = channel(violas, 0.5f, reverb = 0.6f, fade = 6f, eq = stringEq)
    private val chCellos = channel(cellos, 0.55f, reverb = 0.55f, fade = 6f, eq = Eq(sr).highpass(45f).highShelf(6000f, -2f))
    private val chBass = channel(contrabass, 0.45f, reverb = 0.35f, fade = 6f, eq = Eq(sr).highpass(32f).lowShelf(90f, 1f))
    private val chHarp = channel(harp, 0.5f, reverb = 0.55f, delay = 0.1f, fade = 3f)
    private val chGlock = glock?.let { channel(it, 0.22f, reverb = 0.7f, delay = 0.2f, fade = 4f, eq = Eq(sr).highShelf(8000f, -3f)) }
    private val chWaves = channel(waves, if (sleep) 0.09f else 0.06f, reverb = 0.05f, fade = 8f)

    private val relaxProgressions = listOf(
        intArrayOf(0, 3),
        intArrayOf(0, 5, 3, 4),
        intArrayOf(0, 4, 5, 3),
        intArrayOf(3, 0, 4, 5),
        intArrayOf(0, 2, 3, 0),
        intArrayOf(5, 3, 0, 4),
        intArrayOf(0, 3, 5, 4),
    )
    private val sleepProgressions = listOf(
        intArrayOf(0, 3),
        intArrayOf(0, 5),
        intArrayOf(3, 0),
        intArrayOf(0, 4, 3, 0),
    )

    private var cellVoicing: IntArray? = null
    private var violaVoicing: IntArray? = null
    private var violinTop = 76
    private var melodyNote = 72
    private var phraseDir = 1
    private var phraseLeft = 0
    private var phraseVel = 0.4f
    private var harpNext = 4
    private var bellNext = 2
    private val breath = Drift(sr, rng, 8f, 20f, 0.75f, 1f)

    init {
        delayBeats = 1.5f
        delay.feedback = 0.42f
        delay.setDamping(2400f)
        reverbReturn = 1f
        delayToReverb = 0.6f
        masterEq = Eq(sr).highpass(28f).highShelf(11000f, if (sleep) -4f else 1f)
        masterComp = Compressor(sr, thresholdDb = -22f, ratio = 1.8f, attackMs = 40f, releaseMs = 400f, kneeDb = 10f, makeupDb = 2f)
        if (sleep) toneCutoff = 6500f
    }

    override fun newPiece(first: Boolean) {
        key = if (first) {
            Key(rng.pick(intArrayOf(0, 2, 4, 5, 7, 9)), if (rng.chance(if (sleep) 0.75f else 0.65f)) Scale.MAJOR else Scale.LYDIAN)
        } else key.related(rng.pick(intArrayOf(0, 1, 0, 1, 4)))
        progression = rng.pick(if (sleep) sleepProgressions else relaxProgressions)
        chordBars = if (sleep) 4 else rng.pick(intArrayOf(2, 2, 4))
        chordSize = 4
        swing = 0f
        if (!sleep) buildHooks(low = 67, high = 84, notesPerHalf = rng.int(3, 5), syncopation = 0.2f, pentatonic = true, maxLength = 8, velocity = 0.5f)
    }

    override fun makePlan(): List<Section> = if (sleep) listOf(
        Section(Part.FLOAT, 8),
        Section(Part.THEME, 16),
        Section(Part.FLOAT, 8),
    ) else listOf(
        Section(Part.FLOAT, 8),
        Section(Part.THEME, 16),
        Section(Part.VARIATION, 16),
        Section(Part.FLOAT, 8),
    )

    // ---------- Akkordwechsel: Streicher, Bass, Pedal, linke Hand ----------

    override fun onBar() {
        val c = chord
        if (pieceBar % chordBars != 0) return
        val len = stepsToSamples(chordBars * 16f + 2f) // leicht überlappend – Bogenwechsel

        // Celli: Grundton + Quinte, tief
        val root = c.rootAtOrAbove(36)
        val cv = intArrayOf(root, root + 7)
        cellVoicing = cv
        if (chCellos.on) for ((i, n) in cv.withIndex()) cellos.noteOn(n, rng.range(0.45f, 0.6f), len, ms(60f * i) + human(40f))

        // Bratschen: Terz und Septime/None in der Mitte
        val vv = c.voicing(if (sleep) 52 else 55, if (sleep) 66 else 69, violaVoicing)
        violaVoicing = vv
        if (chViolas.on) for ((i, n) in vv.drop(1).withIndex()) violas.noteOn(n, rng.range(0.4f, 0.55f), len, ms(90f * i) + human(50f))

        // Violinen: eine ruhige Oberstimme, möglichst nah an der vorigen
        val vln = violins
        if (vln != null && chViolins?.on == true) {
            val cands = (72..86).filter { c.contains(it) }
            if (cands.isNotEmpty()) {
                violinTop = cands.minBy { abs(it - violinTop) }
                vln.noteOn(violinTop, rng.range(0.35f, 0.5f), len, ms(150f) + human(60f))
            }
        }

        // Kontrabass: lange Grundtöne
        if (chBass.on) contrabass.noteOn(c.rootAtOrAbove(28), rng.range(0.4f, 0.55f), len, human(30f))

        // Klavier: Pedal heben kurz nach dem neuen Anschlag, linke Hand Grundton + Quinte/Dezime
        if (chPiano.on) {
            val lhRoot = c.rootAtOrAbove(if (sleep) 36 else 40)
            val vel = (if (sleep) 0.22f else 0.3f) + 0.12f * intensity
            piano.noteOn(lhRoot, vel * rng.range(0.9f, 1.05f), stepsToSamples(chordBars * 16f), human(20f))
            val upper = if (rng.chance(0.5f)) lhRoot + 7 else lhRoot + 16 - (if (c.tones.getOrNull(1) == 3) 1 else 0)
            piano.noteOn(upper, vel * 0.8f, stepsToSamples(chordBars * 16f), ms(rng.range(60f, 140f)))
        }
    }

    // ---------- Rechte Hand, Harfe, Glockenspiel ----------

    override fun onStep(s: Int) {
        val part = section.part
        val c = chord

        // Rechte Hand: Melodiebögen (Phrasen) mit Crescendo/Decrescendo, auf Akkord- und Skalentönen
        if (chPiano.on && s % 2 == 0) {
            val hook = if (!sleep && part == Part.VARIATION) hookNote(s) else null
            if (hook != null) {
                piano.noteOn(hook.midi, hook.vel * (0.85f + 0.3f * intensity), pedal(s), human(25f))
                melodyNote = hook.midi
            } else if (!(part == Part.VARIATION && !sleep)) {
                val density = if (sleep) 0.05f + 0.12f * intensity else 0.1f + 0.3f * intensity
                val strong = if (s % 8 == 0) 1.5f else if (s % 4 == 0) 1.1f else 0.65f
                val quiet = part == Part.FLOAT && sleep
                if (!quiet && rng.chance(density * strong)) playMelodyNote(s)
            }
        }

        // Harfe: gelegentlich ein aufsteigendes, gebrochenes Arpeggio
        if (s == 0 && chHarp.on) {
            if (--harpNext <= 0) {
                val lo = if (sleep) 43 else 48
                val hi = if (sleep) 72 else 84
                val notes = (lo..hi).filter { c.contains(it) }
                val start = rng.int(0, maxOf(0, notes.size / 3))
                val count = rng.int(4, 7).coerceAtMost(notes.size - start)
                val gap = rng.range(70f, 130f)
                for (k in 0 until count) {
                    harp.noteOn(notes[start + k], rng.range(0.3f, 0.5f) * (1f - k * 0.05f), -1, ms(gap * k) + human(15f))
                }
                harpNext = rng.int(if (sleep) 6 else 3, if (sleep) 12 else 7)
            }
        }

        // Glockenspiel: seltene, helle Tupfer
        val gl = glock
        if (gl != null && chGlock?.on == true && s == 8) {
            if (--bellNext <= 0) {
                val cands = (79..93).filter { c.contains(it) }
                if (cands.isNotEmpty()) gl.noteOn(rng.pick(cands), rng.range(0.2f, 0.4f), -1, human(60f))
                bellNext = rng.int(2, 6)
            }
        }
    }

    /** Haltedauer wie mit gedrücktem Pedal: bis kurz nach dem nächsten Akkordwechsel. */
    private fun pedal(s: Int): Int {
        val left = (chordBars - pieceBar % chordBars) * 16 - s
        return stepsToSamples(left + 0.6f)
    }

    private fun playMelodyNote(s: Int) {
        val c = chord
        val low = if (sleep) 60 else 64
        val high = if (sleep) 79 else 88
        // neuer Bogen
        if (phraseLeft <= 0) {
            phraseLeft = rng.int(4, 9)
            phraseDir = if (melodyNote > (low + high) / 2 + 4) -1 else if (melodyNote < (low + high) / 2 - 4) 1 else if (rng.chance(0.5f)) 1 else -1
            phraseVel = rng.range(0.28f, 0.42f) + 0.12f * intensity
        }
        val scale = key.notesBetween(low, high)
        var idx = scale.indices.minBy { abs(scale[it] - melodyNote) }
        val stepSize = rng.pick(intArrayOf(1, 1, 1, 2, 2, 3))
        idx = (idx + phraseDir * stepSize).coerceIn(0, scale.size - 1)
        var note = scale[idx]
        // betonte Zählzeiten: Akkordton
        if (s % 8 == 0) {
            val ct = (low..high).filter { c.contains(it) }
            if (ct.isNotEmpty()) note = ct.minBy { abs(it - note) }
        }
        if (note == high || note == low) phraseDir = -phraseDir
        melodyNote = note
        // Dynamik im Bogen: Mitte am lautesten
        phraseLeft--
        val arc = 0.8f + 0.25f * sin(Math.PI.toFloat() * (1f - phraseLeft / 8f).coerceIn(0f, 1f))
        val vel = (phraseVel * arc * rng.range(0.9f, 1.08f)).coerceIn(0.12f, 0.7f)
        piano.noteOn(note, vel, pedal(s), human(35f))
        // gelegentlich eine Terz/Sexte darunter dazu
        if (rng.chance(0.18f)) {
            val under = (note - 9..note - 3).filter { c.contains(it) }
            if (under.isNotEmpty()) piano.noteOn(rng.pick(under), vel * 0.7f, pedal(s), ms(40f) + human(30f))
        }
    }

    override fun updateLevels() {
        val part = section.part
        val i = intensity
        val b = breath.advance(stepsToSamples(4f))
        chPiano.target = when (part) {
            Part.FLOAT -> if (sleep) 0.7f else 0.9f
            else -> 1f
        }
        chCellos.target = (0.75f + 0.25f * (1f - i)) * b
        chViolas.target = (if (part == Part.FLOAT) 0.85f else 0.65f + 0.2f * (1f - i)) * b
        chViolins?.target = when (part) {
            Part.VARIATION -> 0.85f
            Part.THEME -> 0.5f + 0.3f * i
            else -> 0.4f
        } * b
        chBass.target = if (part == Part.FLOAT && !sleep) 0.6f else 0.85f
        chHarp.target = if (sleep) 0.5f + 0.4f * i else 0.6f + 0.4f * i
        chGlock?.target = when (part) {
            Part.VARIATION -> 0.9f
            Part.THEME -> above(0.4f, 0.25f)
            else -> above(0.65f, 0.2f)
        }
        chWaves.target = (0.3f + 0.7f * (1f - i)).coerceIn(0f, 1f)
    }
}
