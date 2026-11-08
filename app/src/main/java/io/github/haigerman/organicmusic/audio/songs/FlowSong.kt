package io.github.haigerman.organicmusic.audio.songs

import io.github.haigerman.organicmusic.audio.dsp.Compressor
import io.github.haigerman.organicmusic.audio.dsp.Eq
import io.github.haigerman.organicmusic.audio.dsp.FdnReverb
import io.github.haigerman.organicmusic.audio.instruments.Kick
import io.github.haigerman.organicmusic.audio.instruments.Sampler
import io.github.haigerman.organicmusic.audio.music.Key
import io.github.haigerman.organicmusic.audio.music.Melody
import io.github.haigerman.organicmusic.audio.music.Scale
import io.github.haigerman.organicmusic.audio.samples.SampleBank

/**
 * Kreativ: "Organic House" – ein leichter, positiver Groove aus echten Instrumenten.
 * Congas, Bongos und Shaker, echte Handclaps und Hi-Hats über einer weichen elektronischen
 * Bassdrum; E-Bass mit Fingern gezupft, Flügel-Akkorde, Marimba-Arpeggios, eine Flöte als
 * Melodie und Streicher als Fläche. Hält wach und gut gelaunt, ohne zu drängen.
 */
class FlowSong(sr: Int, seed: Long, bpm: Float, intensity: Float, bank: SampleBank) : Song(sr, seed, bpm, intensity) {

    override val reverb = FdnReverb(sr, decaySeconds = 2.2f, dampHz = 6000f, predelayMs = 20f, size = 1f, early = 0.3f)
    override val outputGain = 1.2f

    private val kick = Kick(sr, rng).apply {
        startHz = 120f; endHz = 49f; pitchDecay = 0.03f; decay = 0.38f; drive = 1.4f; click = 0.12f; tone = 5000f
    }
    private val drums = bank["drums"]
    private val hats = Sampler(sr, rng, drums, 8).apply {
        chokeGroups[42] = 1; chokeGroups[43] = 1; chokeGroups[46] = 1; pan = 0.2f
    }
    private val cymbals = Sampler(sr, rng, drums, 4).apply { pan = -0.2f }
    private val perc = bank["perc"]
    private val shaker = Sampler(sr, rng, perc, 6).apply { pan = -0.35f }
    private val congas = Sampler(sr, rng, perc, 8).apply { pan = 0.35f }
    private val claps = Sampler(sr, rng, bank["claps"], 8).apply { spread = 0.2f }
    private val bass = Sampler(sr, rng, bank["ebass"], 4).apply { release = 0.12f; retrigger = 0.04f }
    private val piano = Sampler(sr, rng, bank["piano"], 32).apply { release = 0.5f; retrigger = 0.1f; spread = 0.15f }
    private val marimba = Sampler(sr, rng, bank["marimba"], 10).apply { spread = 0.5f }
    private val flute = Sampler(sr, rng, bank["flute"], 3).apply { attack = 0.05f; release = 0.45f; pan = 0.1f }
    private val violins = Sampler(sr, rng, bank["violins"], 8).apply { attack = 0.9f; release = 2f; pan = 0.3f }
    private val violas = Sampler(sr, rng, bank["violas"], 8).apply { attack = 0.9f; release = 2f; pan = -0.3f }

    private val drumBus = bus(
        eq = Eq(sr).highpass(30f).highShelf(9000f, 1f),
        comp = Compressor(sr, thresholdDb = -16f, ratio = 3f, attackMs = 12f, releaseMs = 110f, makeupDb = 2.5f),
        saturation = 0.12f,
    )
    private val chKick = channel(kick, 0.62f, fade = 0.03f, bus = drumBus, eq = Eq(sr).highpass(32f))
    private val chClap = channel(claps, 0.42f, reverb = 0.25f, fade = 0.03f, bus = drumBus, eq = Eq(sr).highpass(200f).peak(1800f, 1f, 2f))
    private val chHat = channel(hats, 0.32f, fade = 0.2f, bus = drumBus, eq = Eq(sr).highpass(300f))
    private val chCymbal = channel(cymbals, 0.3f, reverb = 0.2f, fade = 0.03f, bus = drumBus)
    private val chShaker = channel(shaker, 0.3f, reverb = 0.1f, fade = 0.5f, bus = drumBus, eq = Eq(sr).highpass(400f))
    private val chConga = channel(congas, 0.5f, reverb = 0.2f, fade = 0.5f, bus = drumBus)
    private val chBass = channel(
        bass, 0.85f, duck = 0.55f, fade = 0.1f,
        eq = Eq(sr).highpass(30f).lowShelf(90f, 1.5f).peak(250f, 1f, -2f).peak(900f, 1.2f, 2f),
        comp = Compressor(sr, thresholdDb = -18f, ratio = 4f, attackMs = 6f, releaseMs = 100f, makeupDb = 3f),
    )
    private val chPiano = channel(
        piano, 0.55f, reverb = 0.28f, delay = 0.08f, duck = 0.25f, fade = 1.5f,
        eq = Eq(sr).highpass(150f).peak(400f, 1f, -2f).highShelf(5000f, 1.5f),
        comp = Compressor(sr, thresholdDb = -18f, ratio = 2.5f, attackMs = 10f, releaseMs = 120f, makeupDb = 2f),
    )
    private val chMarimba = channel(marimba, 0.38f, reverb = 0.3f, delay = 0.25f, duck = 0.2f, fade = 1.5f, eq = Eq(sr).highpass(200f))
    private val chFlute = channel(flute, 0.42f, reverb = 0.35f, delay = 0.22f, duck = 0.1f, fade = 1.5f, eq = Eq(sr).highpass(250f).highShelf(8000f, 1f))
    private val chViolins = channel(violins, 0.3f, reverb = 0.5f, duck = 0.5f, fade = 3f, eq = Eq(sr).highpass(200f))
    private val chViolas = channel(violas, 0.3f, reverb = 0.5f, duck = 0.5f, fade = 3f, eq = Eq(sr).highpass(150f))

    private val majorProgressions = listOf(
        intArrayOf(0, 4, 5, 3),   // I–V–vi–IV
        intArrayOf(5, 3, 0, 4),   // vi–IV–I–V
        intArrayOf(3, 4, 2, 5),   // IV–V–iii–vi
        intArrayOf(0, 3, 0, 3),   // I–IV
        intArrayOf(1, 4, 0, 0),   // ii–V–I
        intArrayOf(0, 5, 1, 4),   // I–vi–ii–V
    )
    private val dorianProgressions = listOf(
        intArrayOf(0, 3, 0, 3),   // i–IV (dorisch)
        intArrayOf(0, 6, 3, 3),   // i–VII–IV
        intArrayOf(0, 2, 3, 3),   // i–III–IV
    )

    private val bassPatterns = listOf(
        intArrayOf(0, 3, 6, 10, 14),
        intArrayOf(0, 6, 8, 11, 14),
        intArrayOf(2, 6, 10, 14),
        intArrayOf(0, 3, 7, 10, 12, 15),
        intArrayOf(0, 7, 8, 10, 13),
    )
    private val stabPatterns = listOf(
        listOf(2 to 1, 6 to 1, 10 to 1, 14 to 1),        // Offbeat-Stabs
        listOf(0 to 3, 3 to 3, 6 to 4, 10 to 2, 13 to 3), // synkopiert
        listOf(0 to 2, 3 to 2, 8 to 2, 11 to 4),
        listOf(3 to 2, 6 to 2, 11 to 3, 14 to 2),
    )

    private var bassPattern = bassPatterns[0]
    private var bassOct = BooleanArray(16)
    private var bassFifth = BooleanArray(16)
    private var stabs = stabPatterns[0]
    private var congaSteps = intArrayOf()
    private var congaKey = IntArray(16)
    private var arpPattern = IntArray(16)
    private var voicing: IntArray? = null
    private var padVoicing: IntArray? = null
    private var arpVoicing = intArrayOf(72, 76, 79)

    init {
        delayBeats = 0.75f
        delay.feedback = 0.38f
        duckRelease = 0.22f
        masterEq = Eq(sr).highpass(28f).highShelf(10000f, 1f)
        masterComp = Compressor(sr, thresholdDb = -14f, ratio = 2f, attackMs = 20f, releaseMs = 200f, kneeDb = 8f, makeupDb = 2f)
        masterSaturation = 0.08f
        chCymbal.target = 1f
    }

    override fun newPiece(first: Boolean) {
        key = if (first) {
            Key(rng.pick(intArrayOf(0, 2, 4, 5, 7, 9, 10)), if (rng.chance(0.7f)) Scale.MAJOR else Scale.DORIAN)
        } else if (rng.chance(0.55f)) key.related(rng.int(4)) else key
        progression = rng.pick(if (key.scale.minor) dorianProgressions else majorProgressions)
        chordBars = if (rng.chance(0.5f)) 1 else 2
        chordSize = 4
        swing = rng.range(0.03f, 0.1f)
        bassPattern = rng.pick(bassPatterns)
        bassOct = BooleanArray(16) { it != 0 && rng.chance(0.25f) }
        bassFifth = BooleanArray(16) { it != 0 && rng.chance(0.15f) }
        stabs = rng.pick(stabPatterns)
        congaSteps = (0 until 16).filter { it % 4 != 0 && rng.chance(if (it % 2 == 1) 0.25f else 0.38f) }.toIntArray()
        congaKey = IntArray(16) { rng.pick(intArrayOf(62, 63, 63, 64, 60, 61)) }
        arpPattern = Melody.arpPattern(rng, 4, rng.range(0.4f, 0.8f))
        buildHooks(low = 72, high = 88, notesPerHalf = rng.int(4, 7), syncopation = 0.5f, pentatonic = rng.chance(0.6f), maxLength = 6)
    }

    override fun makePlan(): List<Section> = buildList {
        add(Section(Part.INTRO, 8))
        add(Section(Part.GROOVE, 16))
        add(Section(Part.THEME, 16))
        add(Section(Part.BREAK, 8))
        add(Section(Part.THEME, 16))
        add(Section(Part.OUTRO, 8))
    }

    override fun onSection(part: Part) {
        if (part == Part.GROOVE || part == Part.THEME) cymbals.noteOn(49, rng.range(0.5f, 0.65f), -1)
    }

    override fun onBar() {
        val c = chord
        if (pieceBar % chordBars == 0) {
            voicing = c.voicing(57, 76, voicing)
            arpVoicing = c.voicing(64, 81, arpVoicing)
            val pv = c.voicing(55, 74, padVoicing)
            padVoicing = pv
            val len = stepsToSamples(chordBars * 16f + 0.5f)
            if (chViolas.on) for (n in pv.take(2)) violas.noteOn(n, 0.5f, len, human(30f))
            if (chViolins.on) for (n in pv.drop(2)) violins.noteOn(n, 0.45f, len, human(30f))
        }
    }

    override fun onStep(s: Int) {
        val part = section.part
        val v = voicing ?: return

        if (chKick.on && s % 4 == 0) {
            kick.trigger(if (s == 0) 0.95f else 0.85f)
            pump()
        }
        // Handclaps: zwei Hände leicht versetzt – klingt nach echter Gruppe
        if (chClap.on && (s == 4 || s == 12)) {
            claps.noteOn(39, rng.range(0.65f, 0.8f), -1, human(4f))
            if (intensity > 0.5f) claps.noteOn(39, rng.range(0.45f, 0.6f), -1, ms(rng.range(6f, 14f)))
        }
        if (chHat.on) {
            if (s % 4 == 2) hats.noteOn(if (intensity > 0.6f) 46 else 43, rng.range(0.45f, 0.6f), -1, human(4f))
            else if (s % 2 == 1 && rng.chance(0.35f)) hats.noteOn(42, rng.range(0.2f, 0.32f), -1, human(5f))
        }
        // Shaker: Auf- und Abschlag im Wechsel
        if (chShaker.on) shaker.noteOn(if (s % 2 == 0) 70 else 82, (if (s % 4 == 2) 0.7f else 0.5f) * rng.range(0.8f, 1.05f), -1, human(6f))
        if (chConga.on && s in congaSteps) congas.noteOn(congaKey[s], rng.range(0.5f, 0.8f), -1, human(7f))

        if (chBass.on && s in bassPattern) {
            val root = chord.rootAtOrAbove(31)
            val note = root + (if (bassOct[s]) 12 else 0) + (if (bassFifth[s]) 7 else 0)
            val next = bassPattern.firstOrNull { it > s } ?: 16
            val len = (next - s).coerceAtMost(3) - 0.35f
            bass.noteOn(note, if (s == 0) rng.range(0.75f, 0.9f) else rng.range(0.55f, 0.75f), stepsToSamples(len), human(4f))
        }

        if (chPiano.on) {
            for ((start, len) in stabs) {
                if (start != s) continue
                val vel = rng.range(0.5f, 0.68f)
                for ((i, n) in v.withIndex()) piano.noteOn(n, vel * rng.range(0.92f, 1.05f), stepsToSamples(len - 0.15f), ms(4f * i) + human(5f))
            }
        }

        if (chMarimba.on) {
            val idx = arpPattern[s]
            if (idx >= 0 && s % 2 == 0) marimba.noteOn(Melody.arpNote(arpVoicing, idx), rng.range(0.45f, 0.7f), -1, human(6f))
        }

        if (chFlute.on && (part == Part.THEME || part == Part.BREAK)) {
            hookNote(s)?.let { flute.noteOn(it.midi, it.vel * 0.85f, stepsToSamples(it.length - 0.25f), human(10f)) }
        }
    }

    init {
        for (c in listOf(chConga, chMarimba, chShaker, chViolins, chFlute)) c.optional = true
    }

    /** Variabilität: Bass-Groove, Piano-Rhythmus, Percussion und Arpeggio entwickeln sich. */
    override fun onPhrase() {
        if (vary(0.4f)) {
            bassPattern = rng.pick(bassPatterns)
            bassOct = BooleanArray(16) { it != 0 && rng.chance(0.25f) }
        }
        if (vary(0.25f)) bassFifth = BooleanArray(16) { it != 0 && rng.chance(0.15f) }
        if (vary(0.45f)) stabs = rng.pick(stabPatterns)
        if (vary(0.4f)) {
            congaSteps = (0 until 16).filter { it % 4 != 0 && rng.chance(if (it % 2 == 1) 0.25f else 0.38f) }.toIntArray()
            congaKey = IntArray(16) { rng.pick(intArrayOf(62, 63, 63, 64, 60, 61)) }
        }
        if (vary(0.45f)) arpPattern = Melody.arpPattern(rng, 4, rng.range(0.4f, 0.8f))
    }

    override fun updateLevels() {
        val part = section.part
        fun lv(intro: Float, groove: Float, theme: Float, brk: Float, outro: Float) = when (part) {
            Part.INTRO -> intro
            Part.THEME -> theme
            Part.BREAK -> brk
            Part.OUTRO -> outro
            else -> groove
        }
        chKick.target = lv(0f, 1f, 1f, 0f, 1f) * above(0.1f)
        chClap.target = lv(0f, 1f, 1f, 0f, 0.8f) * above(0.25f)
        chHat.target = lv(0.7f, 1f, 1f, 0.4f, 0.8f) * above(0.2f)
        chShaker.target = lv(1f, 1f, 1f, 0.6f, 1f) * above(0.3f)
        chConga.target = lv(0.7f, 0.8f, 1f, 0.6f, 0.7f) * above(0.45f)
        chBass.target = lv(0f, 1f, 1f, 0f, 0.8f)
        chPiano.target = lv(0.7f, 1f, 0.9f, 0.8f, 0.8f)
        chMarimba.target = lv(0.6f, above(0.4f), above(0.3f), 0.8f, 0.5f)
        val pad = lv(0.7f, 0.5f, 0.6f, 1f, 0.6f)
        chViolins.target = pad
        chViolas.target = pad
        chFlute.target = lv(0f, 0f, above(0.4f), 0.8f * above(0.5f), 0f)
    }
}
