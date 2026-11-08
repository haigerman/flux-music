package io.github.haigerman.organicmusic.audio.songs

import io.github.haigerman.organicmusic.audio.dsp.Compressor
import io.github.haigerman.organicmusic.audio.dsp.Drift
import io.github.haigerman.organicmusic.audio.dsp.Eq
import io.github.haigerman.organicmusic.audio.dsp.FdnReverb
import io.github.haigerman.organicmusic.audio.instruments.Sampler
import io.github.haigerman.organicmusic.audio.instruments.Vinyl
import io.github.haigerman.organicmusic.audio.music.Key
import io.github.haigerman.organicmusic.audio.music.Scale
import io.github.haigerman.organicmusic.audio.samples.SampleBank

/**
 * Fokus: entspannter Lo-Fi-Hip-Hop mit echten Instrumenten. Ein Schlagzeug, aufgenommen
 * über ein Lo-Fi-Mikrofon, spielt mit Swing leicht hinter dem Beat; ein Wurlitzer-E-Piano
 * spielt Jazz-Akkorde, ein gezupfter Kontrabass den Grundton. Sparsame Melodien von
 * Glockenspiel oder Flöte, darunter leise Bratschen, Vinyl-Knistern und das sanfte Leiern
 * einer Bandmaschine. Bewusst gleichmäßig – die Musik soll tragen, nicht ablenken.
 */
class FocusSong(sr: Int, seed: Long, bpm: Float, intensity: Float, bank: SampleBank) : Song(sr, seed, bpm, intensity) {

    override val reverb = FdnReverb(sr, decaySeconds = 1.6f, dampHz = 4500f, predelayMs = 12f, size = 0.8f, early = 0.4f)

    // ohne Schlagzeug (geringe Intensität) etwas lauter, damit es nicht "wegsackt"
    override val outputGain: Float get() = 1.0f * (1f + 0.8f * maxOf(0f, 0.5f - intensity))

    private val kit = bank["drums_lofi"]
    private val kick = Sampler(sr, rng, kit, 4)
    private val snare = Sampler(sr, rng, kit, 6)
    private val hats = Sampler(sr, rng, kit, 6).apply {
        chokeGroups[42] = 1; chokeGroups[44] = 1; chokeGroups[46] = 1
        pan = 0.15f
    }
    private val wurli = Sampler(sr, rng, bank["wurli"], 24).apply {
        release = 0.6f; retrigger = 0.15f; spread = 0.25f; toneHz = 6500f
    }
    private val upright = Sampler(sr, rng, bank["upright"], 4).apply { release = 0.35f; retrigger = 0.08f }
    private val glock = Sampler(sr, rng, bank["glock"], 6).apply { release = 1.5f; pan = 0.2f; toneHz = 7000f }
    private val flute = Sampler(sr, rng, bank["flute"], 3).apply { attack = 0.06f; release = 0.5f; pan = -0.15f; toneHz = 7000f }
    private val violas = Sampler(sr, rng, bank["violas"], 8).apply { attack = 1.5f; release = 2.5f; toneHz = 3500f }
    private val vinyl = Vinyl(sr, rng)

    private val drumBus = bus(
        gain = 1f,
        eq = Eq(sr).highpass(40f).lowShelf(110f, 2f).highShelf(8000f, -3f),
        comp = Compressor(sr, thresholdDb = -20f, ratio = 3f, attackMs = 15f, releaseMs = 140f, makeupDb = 3f),
        saturation = 0.2f,
    )
    private val chKick = channel(kick, 0.9f, fade = 0.05f, bus = drumBus)
    private val chSnare = channel(snare, 0.7f, reverb = 0.12f, fade = 0.05f, bus = drumBus)
    private val chHats = channel(hats, 0.45f, fade = 0.3f, bus = drumBus)
    private val chKeys = channel(
        wurli, 0.75f, reverb = 0.22f, duck = 0.1f, fade = 2f,
        eq = Eq(sr).highpass(90f).peak(300f, 0.9f, -2f).peak(1800f, 1f, 1.5f),
        comp = Compressor(sr, thresholdDb = -18f, ratio = 2.5f, attackMs = 12f, releaseMs = 160f, makeupDb = 2f),
    )
    private val chBass = channel(upright, 0.85f, reverb = 0.05f, duck = 0.15f, fade = 0.4f,
        eq = Eq(sr).highpass(35f).lowShelf(100f, 2f).peak(700f, 1f, 1.5f),
        comp = Compressor(sr, thresholdDb = -20f, ratio = 3f, attackMs = 8f, releaseMs = 120f, makeupDb = 2f))
    private val chGlock = channel(glock, 0.3f, reverb = 0.35f, delay = 0.25f, fade = 2f)
    private val chFlute = channel(flute, 0.4f, reverb = 0.35f, delay = 0.2f, fade = 2f, eq = Eq(sr).highpass(250f))
    private val chPad = channel(violas, 0.32f, reverb = 0.5f, fade = 4f, eq = Eq(sr).highpass(120f))
    private val chVinyl = channel(vinyl, 0.45f, fade = 3f)

    private val majorProgressions = listOf(
        intArrayOf(1, 4, 0, 5),   // ii–V–I–vi
        intArrayOf(0, 5, 1, 4),   // I–vi–ii–V
        intArrayOf(3, 2, 1, 0),   // IV–iii–ii–I
        intArrayOf(3, 2, 3, 2),   // IVmaj7–iii7
        intArrayOf(1, 4, 1, 4),   // ii–V
        intArrayOf(0, 3, 0, 3),   // Imaj7–IVmaj7
    )
    private val minorProgressions = listOf(
        intArrayOf(0, 3, 6, 2),   // i–iv–VII–III
        intArrayOf(0, 5, 3, 4),   // i–VI–iv–v
        intArrayOf(0, 3, 0, 3),   // i–iv
        intArrayOf(5, 4, 0, 0),   // VI–v–i
    )

    /** Bassdrum-Muster (Sechzehntel) im typischen "Boom-Bap". */
    private val kickPatterns = listOf(
        intArrayOf(0, 10),
        intArrayOf(0, 7, 10),
        intArrayOf(0, 3, 10),
        intArrayOf(0, 8, 10),
        intArrayOf(0, 6, 10, 11),
    )

    /** Anschlagmuster für die Akkorde: (Beginn, Länge) in Sechzehnteln. */
    private val compPatterns = listOf(
        listOf(0 to 15),
        listOf(0 to 6, 6 to 10),
        listOf(0 to 3, 10 to 6),
        listOf(0 to 7, 7 to 3, 10 to 6),
        listOf(0 to 10, 14 to 2),
    )

    private var kickPattern = kickPatterns[0]
    private var comp = compPatterns[0]
    private var ghostSnare = false
    private var crossstick = false
    private var hat16 = 0.1f
    private var bassStyle = 0
    private var useFlute = false
    private var voicing: IntArray? = null
    private var padVoicing: IntArray? = null
    private var pieces = 0
    private val wow = Drift(sr, rng, 1.2f, 3.5f, -1f, 1f)

    init {
        delayBeats = 0.75f
        delay.feedback = 0.35f
        delay.setDamping(2500f)
        duckRelease = 0.18f
        toneCutoff = 9500f
        masterEq = Eq(sr).highpass(30f).lowShelf(180f, 1f).highShelf(9000f, -2f)
        masterComp = Compressor(sr, thresholdDb = -16f, ratio = 2f, attackMs = 25f, releaseMs = 250f, kneeDb = 8f, makeupDb = 1.5f)
        masterSaturation = 0.18f
        chVinyl.target = 1f
    }

    override fun newPiece(first: Boolean) {
        key = if (first) {
            Key(rng.pick(intArrayOf(0, 2, 3, 5, 7, 8, 10)), if (rng.chance(0.65f)) Scale.MAJOR else Scale.DORIAN)
        } else if (rng.chance(0.5f)) key.related(rng.int(4)) else key
        progression = rng.pick(if (key.scale.minor) minorProgressions else majorProgressions)
        chordBars = if (rng.chance(0.65f)) 1 else 2
        chordSize = if (rng.chance(0.4f)) 5 else 4
        swing = rng.range(0.12f, 0.2f)
        kickPattern = rng.pick(kickPatterns)
        comp = rng.pick(compPatterns)
        ghostSnare = rng.chance(0.5f)
        crossstick = rng.chance(0.3f)
        hat16 = rng.range(0.05f, 0.22f)
        bassStyle = rng.int(3)
        useFlute = rng.chance(0.45f)
        buildHooks(low = if (useFlute) 72 else 74, high = if (useFlute) 86 else 91, notesPerHalf = rng.int(3, 5), syncopation = 0.45f,
            pentatonic = true, maxLength = 6, velocity = 0.65f)
        pieces++
    }

    override fun makePlan(): List<Section> = buildList {
        add(Section(Part.INTRO, if (pieces == 1) 4 else 2))
        add(Section(Part.GROOVE, 8))
        add(Section(Part.THEME, 16))
        add(Section(Part.GROOVE, 8))
        add(Section(Part.BREAK, 4))
        add(Section(Part.THEME, 16))
        add(Section(Part.GROOVE, 8))
    }

    override fun onBar() {
        val c = chord
        // Band-Leiern: sehr langsam, wenige Cent
        val w = wow.advance(stepsToSamples(16f)) * 7f
        wurli.pitchModCents = w
        glock.pitchModCents = w
        flute.pitchModCents = w * 0.6f
        if (pieceBar % chordBars == 0) {
            voicing = c.voicing(55, 74, voicing, skipRootIfLarge = true)
            if (chPad.on) {
                val pv = c.voicing(52, 67, padVoicing)
                padVoicing = pv
                val len = stepsToSamples(chordBars * 16f + 1f)
                for ((i, n) in pv.withIndex()) violas.noteOn(n, 0.45f, len, ms(80f * i))
            }
        }
    }

    override fun onStep(s: Int) {
        val part = section.part
        val v = voicing ?: return

        // Akkorde (leicht gebrochen angeschlagen, wie mit der Hand)
        if (chKeys.on) {
            for ((start, len) in comp) {
                if (start != s) continue
                val vel = rng.range(0.38f, 0.58f) * if (s == 0) 1f else 0.85f
                val up = rng.chance(0.7f)
                val strum = rng.range(10f, 26f)
                for (i in v.indices) {
                    val order = if (up) i else v.size - 1 - i
                    wurli.noteOn(v[i], vel * rng.range(0.9f, 1.06f), stepsToSamples(len - 0.4f), ms(strum * order) + human(8f))
                }
            }
        }

        // Bassdrum & Snare (leicht hinter dem Beat)
        if (chKick.on && s in kickPattern) {
            kick.noteOn(36, if (s == 0) rng.range(0.75f, 0.85f) else rng.range(0.55f, 0.7f), -1, human(8f))
            if (s == 0) pump()
        }
        if (chSnare.on) {
            if (s == 4 || s == 12) {
                val k = if (crossstick && part == Part.GROOVE) 37 else 38
                snare.noteOn(k, rng.range(0.6f, 0.75f), -1, ms(14f) + human(10f))
            } else if (ghostSnare && (s == 7 || s == 15) && rng.chance(0.35f)) {
                snare.noteOn(38, rng.range(0.12f, 0.2f), -1, human(8f))
            }
        }

        // Hi-Hats
        if (chHats.on) {
            if (s % 2 == 0) hats.noteOn(42, (if (s % 4 == 0) 0.55f else 0.38f) * rng.range(0.85f, 1.1f), -1, human(10f))
            else if (rng.chance(hat16)) hats.noteOn(42, rng.range(0.2f, 0.3f), -1, human(6f))
            if (s == 14 && pieceBar % 4 == 3 && rng.chance(0.5f)) hats.noteOn(46, 0.4f, -1, human(6f))
        }

        // Kontrabass
        if (chBass.on && part != Part.INTRO) {
            val c = chord
            val root = c.rootAtOrAbove(31)
            val fifth = root + 7
            when (s) {
                0 -> upright.noteOn(root, rng.range(0.7f, 0.85f), stepsToSamples(if (bassStyle == 0) 9f else 6f), human(6f))
                10 -> if (bassStyle >= 1) upright.noteOn(if (rng.chance(0.6f)) root else fifth, rng.range(0.55f, 0.7f), stepsToSamples(3.5f), human(6f))
                7 -> if (bassStyle == 2 && rng.chance(0.5f)) upright.noteOn(root + 12, 0.5f, stepsToSamples(2f), human(6f))
                14 -> if (chordChangesNextBar && rng.chance(0.55f)) {
                    // chromatische Annäherung an den nächsten Grundton
                    val next = chordAt(pieceBar + 1).rootAtOrAbove(31)
                    upright.noteOn(next + if (rng.chance(0.5f)) -1 else 1, 0.55f, stepsToSamples(1.6f), human(6f))
                }
            }
        }

        // Melodie: nur im Thema, und jede zweite Phrase bleibt frei
        if (part == Part.THEME && (barInSection / 4) % 2 == 0) {
            hookNote(s)?.let {
                if (useFlute && chFlute.on) {
                    flute.noteOn(it.midi - 2 * 0, it.vel * 0.85f, stepsToSamples(it.length - 0.3f), human(14f))
                } else if (!useFlute && chGlock.on) {
                    glock.noteOn(it.midi, it.vel * 0.8f, -1, human(12f))
                }
            }
        }
    }

    init {
        for (c in listOf(chPad, chGlock, chFlute)) c.optional = true
    }

    /** Variabilität: Beat, Anschlagmuster, Bassfigur und Melodie-Instrument wechseln. */
    override fun onPhrase() {
        if (vary(0.4f)) kickPattern = rng.pick(kickPatterns)
        if (vary(0.45f)) comp = rng.pick(compPatterns)
        if (vary(0.3f)) ghostSnare = !ghostSnare
        if (vary(0.25f)) crossstick = !crossstick
        if (vary(0.35f)) hat16 = rng.range(0.05f, 0.3f)
        if (vary(0.35f)) bassStyle = rng.int(3)
        if (vary(0.25f)) useFlute = !useFlute
    }

    override fun updateLevels() {
        val part = section.part
        val drumsOn = when (part) {
            Part.INTRO, Part.BREAK -> 0f
            else -> 1f
        }
        chKick.target = drumsOn * above(0.15f)
        chSnare.target = drumsOn * above(0.22f)
        chHats.target = (if (part == Part.INTRO) 0.6f else 1f) * above(0.3f)
        chBass.target = if (part == Part.INTRO) 0f else above(0.08f) * (if (part == Part.BREAK) 0.75f else 1f)
        chKeys.target = 1f
        val mel = if (part == Part.THEME) above(0.45f) else 0f
        chGlock.target = mel
        chFlute.target = mel
        chPad.target = (0.3f + 0.6f * (1f - above(0.5f, 0.4f)) + if (part == Part.BREAK) 0.3f else 0f).coerceAtMost(1f)
    }
}
