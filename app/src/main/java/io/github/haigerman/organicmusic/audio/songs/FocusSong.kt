package io.github.haigerman.organicmusic.audio.songs

import io.github.haigerman.organicmusic.audio.dsp.Reverb
import io.github.haigerman.organicmusic.audio.instruments.Bass
import io.github.haigerman.organicmusic.audio.instruments.BassPreset
import io.github.haigerman.organicmusic.audio.instruments.FmPreset
import io.github.haigerman.organicmusic.audio.instruments.FmSynth
import io.github.haigerman.organicmusic.audio.instruments.HiHat
import io.github.haigerman.organicmusic.audio.instruments.Kick
import io.github.haigerman.organicmusic.audio.instruments.PolySynth
import io.github.haigerman.organicmusic.audio.instruments.Shaker
import io.github.haigerman.organicmusic.audio.instruments.Snare
import io.github.haigerman.organicmusic.audio.instruments.SynthPreset
import io.github.haigerman.organicmusic.audio.instruments.Vinyl
import io.github.haigerman.organicmusic.audio.instruments.Wave
import io.github.haigerman.organicmusic.audio.music.Key
import io.github.haigerman.organicmusic.audio.music.Scale

/**
 * Fokus: entspannter Lo-Fi-Hip-Hop. Gedämpfte Drums mit Swing, warmes E-Piano mit
 * Jazz-Akkorden (Septimen/Nonen), weicher Bass, leises Vinyl-Knistern. Bewusst gleichmäßig
 * und ohne Überraschungen – die Musik soll tragen, nicht ablenken. Melodien sind sparsam
 * und lassen viel Raum.
 */
class FocusSong(sr: Int, seed: Long, bpm: Float, intensity: Float) : Song(sr, seed, bpm, intensity) {

    override val reverb = Reverb(sr, roomSize = 0.8f, damping = 0.5f, inputGain = 0.012f)
    // ohne Schlagzeug (geringe Intensität) etwas lauter, damit es nicht "wegsackt"
    override val outputGain: Float get() = 1.9f * (1f + 1.2f * maxOf(0f, 0.5f - intensity))

    private val kick = Kick(sr, rng).apply {
        startHz = 120f; endHz = 55f; pitchDecay = 0.03f; decay = 0.28f; drive = 1.5f; click = 0.1f; tone = 3200f
    }
    private val snare = Snare(sr, rng).apply {
        toneHz = 190f; toneMix = 0.35f; noiseDecay = 0.19f; toneDecay = 0.07f; brightness = 3800f
    }
    private val hat = HiHat(sr, rng).apply { closedDecay = 0.035f; brightness = 0.5f; pan = 0.2f }
    private val shaker = Shaker(sr, rng).apply { decay = 0.06f; pan = -0.25f }
    private val bass = Bass(
        sr,
        BassPreset(
            saw = 0.4f, sine = 0.55f, cutoff = 650f, envAmount = 1.5f, fDecay = 0.2f, resonance = 0.8f,
            attack = 0.006f, decay = 0.5f, sustain = 0.5f, release = 0.12f, glide = 0.03f, drive = 1.15f,
        ),
    )
    private val keys = FmSynth(
        sr, rng, 10,
        FmPreset(
            ratio = 1f, index = 1.8f, indexDecay = 0.5f, sustainIndex = 0.2f, decay = 3f, release = 0.45f,
            tine = 0.1f, tineDecay = 0.06f, tremoloDepth = 0.12f, tremoloRate = 4f, detuneCents = 4f,
            spread = 0.4f, gain = 0.55f,
        ),
    )
    private val melodyPresets = listOf(
        // Vibraphon
        FmPreset(
            ratio = 4f, index = 0.9f, indexDecay = 0.25f, sustainIndex = 0.05f, decay = 2.2f, release = 0.5f,
            tine = 0f, tremoloDepth = 0.25f, tremoloRate = 5.5f, detuneCents = 2f, spread = 0.2f, gain = 0.6f,
        ),
        // weiches Glockenspiel
        FmPreset(
            ratio = 3.01f, index = 1.1f, indexDecay = 0.4f, sustainIndex = 0.1f, decay = 1.8f, release = 0.4f,
            tine = 0f, tremoloDepth = 0.05f, detuneCents = 1.5f, spread = 0.25f, gain = 0.5f,
        ),
    )
    private val melody = FmSynth(sr, rng, 4, melodyPresets[0])
    private val pad = PolySynth(
        sr, rng, 6,
        SynthPreset(
            wave = Wave.SAW, oscs = 2, detuneCents = 10f, cutoff = 650f, resonance = 0.6f, envAmount = 0.2f,
            fDecay = 2f, fSustain = 0.8f, attack = 1.5f, decay = 2f, sustain = 0.9f, release = 2f,
            keyTrack = 0.2f, spread = 0.7f, wobble = 0.3f, wobbleRate = 0.1f, gain = 0.5f,
        ),
    )
    private val vinyl = Vinyl(sr, rng)

    private val chKick = channel(kick, 0.5f, fade = 0.05f)
    private val chSnare = channel(snare, 0.33f, reverb = 0.15f, fade = 0.05f)
    private val chHat = channel(hat, 0.11f, fade = 0.3f)
    private val chShaker = channel(shaker, 0.09f, fade = 0.5f)
    private val chBass = channel(bass, 0.36f, duck = 0.2f, fade = 0.3f)
    private val chKeys = channel(keys, 0.42f, reverb = 0.3f, duck = 0.12f, fade = 2f)
    private val chMelody = channel(melody, 0.24f, reverb = 0.35f, delay = 0.25f, fade = 2f)
    private val chPad = channel(pad, 0.18f, reverb = 0.5f, fade = 4f)
    private val chVinyl = channel(vinyl, 0.55f, fade = 3f)

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
    private var hat16 = 0.1f
    private var bassStyle = 0
    private var voicing: IntArray? = null
    private var padVoicing: IntArray? = null
    private var pieces = 0

    init {
        delayBeats = 0.75f
        delay.feedback = 0.35f
        delay.setDamping(2500f)
        duckRelease = 0.18f
        toneCutoff = 7000f
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
        hat16 = rng.range(0.05f, 0.25f)
        bassStyle = rng.int(3)
        melody.preset = rng.pick(melodyPresets)
        buildHooks(low = 67, high = 84, notesPerHalf = rng.int(3, 5), syncopation = 0.45f, pentatonic = true, maxLength = 6, velocity = 0.7f)
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
        if (pieceBar % chordBars == 0) {
            voicing = c.voicing(55, 74, voicing, skipRootIfLarge = true)
            if (chPad.on) {
                val pv = c.voicing(52, 67, padVoicing)
                padVoicing = pv
                val len = stepsToSamples(chordBars * 16f - 1f)
                for (n in pv) pad.noteOn(n, 0.6f, len)
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
                val vel = rng.range(0.5f, 0.72f) * if (s == 0) 1f else 0.85f
                val up = rng.chance(0.7f)
                val strum = rng.range(8f, 22f)
                for (i in v.indices) {
                    val order = if (up) i else v.size - 1 - i
                    keys.noteOn(v[i], vel * rng.range(0.9f, 1.05f), stepsToSamples(len - 0.4f), ms(strum * order) + human(6f))
                }
            }
        }

        // Bassdrum & Snare
        if (chKick.on && s in kickPattern) {
            kick.trigger(if (s == 0) 0.95f else 0.75f, human(8f))
            if (s == 0) pump()
        }
        if (chSnare.on) {
            if (s == 4 || s == 12) snare.trigger(0.8f * rng.range(0.9f, 1.05f), ms(12f) + human(10f)) // etwas "hinter dem Beat"
            else if (ghostSnare && (s == 7 || s == 15) && rng.chance(0.35f)) snare.trigger(0.18f, human(8f))
        }

        // Hi-Hats & Shaker
        if (chHat.on) {
            if (s % 2 == 0) hat.trigger((if (s % 4 == 0) 0.75f else 0.55f) * rng.range(0.8f, 1.05f), open = false, delay = human(10f))
            else if (rng.chance(hat16)) hat.trigger(0.3f, open = false, delay = human(6f))
            if (s == 14 && pieceBar % 4 == 3 && rng.chance(0.5f)) hat.trigger(0.5f, open = true)
        }
        if (chShaker.on && s % 2 == 1) shaker.trigger(rng.range(0.4f, 0.7f), human(8f))

        // Bass
        if (chBass.on && part != Part.INTRO) {
            val c = chord
            val root = c.rootAtOrAbove(36)
            val fifth = root + 7
            when (s) {
                0 -> bass.noteOn(root, 0.9f, stepsToSamples(if (bassStyle == 0) 9f else 6f), human(5f))
                10 -> if (bassStyle >= 1) bass.noteOn(if (rng.chance(0.6f)) root else fifth, 0.7f, stepsToSamples(3.5f), human(5f))
                7 -> if (bassStyle == 2 && rng.chance(0.5f)) bass.noteOn(root + 12, 0.55f, stepsToSamples(2f), human(5f))
                14 -> if (chordChangesNextBar && rng.chance(0.55f)) {
                    // chromatische Annäherung an den nächsten Grundton
                    val next = chordAt(pieceBar + 1).rootAtOrAbove(36)
                    bass.noteOn(next + if (rng.chance(0.5f)) -1 else 1, 0.6f, stepsToSamples(1.6f), human(5f), glide = true)
                }
            }
        }

        // Melodie: nur im Thema, und jede zweite Phrase bleibt frei
        if (chMelody.on && part == Part.THEME && (barInSection / 4) % 2 == 0) {
            hookNote(s)?.let { melody.noteOn(it.midi, it.vel, stepsToSamples(it.length.toFloat()), human(12f)) }
        }
    }

    override fun updateLevels() {
        val part = section.part
        val drumsOn = when (part) {
            Part.INTRO, Part.BREAK -> 0f
            else -> 1f
        }
        chKick.target = drumsOn * above(0.15f)
        chSnare.target = drumsOn * above(0.22f)
        chHat.target = (if (part == Part.INTRO) 0.6f else 1f) * above(0.3f)
        chShaker.target = drumsOn * above(0.7f)
        chBass.target = if (part == Part.INTRO) 0f else above(0.08f) * (if (part == Part.BREAK) 0.7f else 1f)
        chKeys.target = 1f
        chMelody.target = if (part == Part.THEME) above(0.45f) else 0f
        chPad.target = (0.35f + 0.65f * (1f - above(0.5f, 0.4f)) + if (part == Part.BREAK) 0.3f else 0f).coerceAtMost(1f)
    }
}
