package io.github.haigerman.organicmusic.audio.songs

import io.github.haigerman.organicmusic.audio.dsp.Reverb
import io.github.haigerman.organicmusic.audio.dsp.midiHz
import io.github.haigerman.organicmusic.audio.instruments.Bass
import io.github.haigerman.organicmusic.audio.instruments.BassPreset
import io.github.haigerman.organicmusic.audio.instruments.Drone
import io.github.haigerman.organicmusic.audio.instruments.FmPreset
import io.github.haigerman.organicmusic.audio.instruments.FmSynth
import io.github.haigerman.organicmusic.audio.instruments.PolySynth
import io.github.haigerman.organicmusic.audio.instruments.SynthPreset
import io.github.haigerman.organicmusic.audio.instruments.Waves
import io.github.haigerman.organicmusic.audio.instruments.Wave
import io.github.haigerman.organicmusic.audio.music.Key
import io.github.haigerman.organicmusic.audio.music.Scale

/**
 * Entspannen / Einschlafen: generative Ambient-Musik ohne Schlagzeug. Langsame, atmende
 * Klangflächen, ein gedämpftes Klavier, das frei über den Akkorden improvisiert, vereinzelte
 * Glocken und leises Meeresrauschen – alles in einem großen Hallraum.
 *
 * Mit [sleep] wird alles langsamer, tiefer und dunkler; ein Grundton trägt, die Melodie
 * zieht sich fast ganz zurück.
 */
class AmbientSong(
    sr: Int,
    seed: Long,
    bpm: Float,
    intensity: Float,
    private val sleep: Boolean,
) : Song(sr, seed, bpm, intensity) {

    override val reverb = Reverb(sr, roomSize = if (sleep) 0.94f else 0.92f, damping = if (sleep) 0.6f else 0.45f, inputGain = 0.014f)
    override val outputGain = if (sleep) 1.8f else 1.95f

    private val pad = PolySynth(
        sr, rng, 10,
        SynthPreset(
            wave = Wave.SAW, oscs = 3, detuneCents = 12f,
            cutoff = if (sleep) 420f else 800f, resonance = 0.6f,
            envAmount = 0.4f, fAttack = 3f, fDecay = 4f, fSustain = 0.6f,
            attack = if (sleep) 5f else 3f, decay = 3f, sustain = 0.9f, release = if (sleep) 7f else 5f,
            keyTrack = 0.2f, spread = 0.9f, wobble = 0.3f, wobbleRate = 0.06f, gain = 0.55f,
        ),
    )
    private val piano = FmSynth(
        sr, rng, 12,
        FmPreset(
            ratio = 1f, index = if (sleep) 0.6f else 0.95f, indexDecay = 0.35f, sustainIndex = 0.08f,
            decay = 4.5f, release = 1.5f, tine = 0.02f, tineDecay = 0.05f,
            tremoloDepth = 0.03f, tremoloRate = 3f, detuneCents = 2.5f, spread = 0.55f, gain = 0.6f,
        ),
    )
    private val bells = FmSynth(
        sr, rng, 5,
        FmPreset(
            ratio = 3.5f, index = 1.4f, indexDecay = 1.5f, sustainIndex = 0.2f, decay = 5f, release = 2.5f,
            tine = 0f, tremoloDepth = 0.05f, tremoloRate = 2f, detuneCents = 1.5f, spread = 0.75f, gain = 0.35f,
        ),
    )
    private val bass = Bass(
        sr,
        BassPreset(
            saw = 0.25f, sine = 0.7f, cutoff = 320f, envAmount = 0.3f, fDecay = 1f, resonance = 0.6f,
            attack = 1.2f, decay = 3f, sustain = 0.85f, release = 3f, glide = 0.3f, drive = 1f,
        ),
    )
    private val waves = Waves(sr, rng)
    private val drone = Drone(sr, rng)

    private val chPad = channel(pad, 0.26f, reverb = 0.6f, fade = 6f)
    private val chPiano = channel(piano, 0.38f, reverb = 0.55f, delay = 0.18f, fade = 4f)
    private val chBells = channel(bells, 0.16f, reverb = 0.7f, delay = 0.3f, fade = 4f)
    private val chBass = channel(bass, 0.2f, reverb = 0.1f, fade = 5f)
    private val chWaves = channel(waves, if (sleep) 0.22f else 0.12f, reverb = 0.05f, fade = 8f)
    private val chDrone = channel(drone, 0.08f, reverb = 0.3f, fade = 8f)

    private val relaxProgressions = listOf(
        intArrayOf(0, 3),
        intArrayOf(0, 5, 3, 4),
        intArrayOf(0, 4, 5, 3),
        intArrayOf(3, 0, 4, 5),
        intArrayOf(0, 2, 3, 0),
        intArrayOf(5, 3, 0, 4),
    )
    private val sleepProgressions = listOf(
        intArrayOf(0, 3),
        intArrayOf(0, 5),
        intArrayOf(3, 0),
        intArrayOf(0, 4, 3, 0),
    )

    private var padVoicing: IntArray? = null
    private var pianoPool = intArrayOf()
    private var lastPiano = 72
    private var bellNext = 0

    init {
        delayBeats = 1.5f
        delay.feedback = 0.5f
        delay.setDamping(2200f)
        reverbReturn = 1.1f
        delayToReverb = 0.5f
        if (sleep) toneCutoff = 5000f
    }

    override fun newPiece(first: Boolean) {
        key = if (first) {
            Key(rng.pick(intArrayOf(0, 2, 4, 5, 7, 9)), if (rng.chance(if (sleep) 0.7f else 0.6f)) Scale.MAJOR else Scale.LYDIAN)
        } else key.related(rng.pick(intArrayOf(0, 1, 0, 1, 4)))
        progression = rng.pick(if (sleep) sleepProgressions else relaxProgressions)
        chordBars = if (sleep) 4 else rng.pick(intArrayOf(2, 2, 4))
        chordSize = 4
        swing = 0f
        if (!sleep) buildHooks(low = 64, high = 81, notesPerHalf = rng.int(3, 5), syncopation = 0.2f, pentatonic = true, maxLength = 8, velocity = 0.55f)
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

    override fun onBar() {
        val c = chord
        if (pieceBar % chordBars == 0) {
            val len = stepsToSamples(chordBars * 16f - 2f)
            val pv = c.voicing(if (sleep) 48 else 52, if (sleep) 64 else 69, padVoicing)
            padVoicing = pv
            if (chPad.on) for ((i, n) in pv.withIndex()) pad.noteOn(n, rng.range(0.55f, 0.75f), len, ms(150f * i))
            val root = c.rootAtOrAbove(if (sleep) 36 else 38)
            if (chBass.on) bass.noteOn(root, 0.8f, len, glide = true)
            drone.setNote(midiHz(key.tonicNear(if (sleep) 45 else 48)))
            // Tonvorrat fürs Klavier: Akkordtöne + Nonen, über zwei Oktaven
            val low = if (sleep) 55 else 60
            val high = if (sleep) 74 else 86
            val pcs = c.pcs().toMutableSet().apply { add(Math.floorMod(c.rootPc + 2, 12)) }
            pianoPool = (low..high).filter { Math.floorMod(it, 12) in pcs && key.notesBetween(it, it).isNotEmpty() }.toIntArray()
        }
    }

    override fun onStep(s: Int) {
        val part = section.part

        // Klavier: frei schwebende Einzeltöne, die sich meist schrittweise bewegen
        if (chPiano.on && s % 2 == 0 && pianoPool.isNotEmpty()) {
            val density = if (sleep) 0.04f + 0.12f * intensity else 0.08f + 0.3f * intensity
            val strong = if (s % 8 == 0) 1.6f else if (s % 4 == 0) 1.1f else 0.6f
            val playHook = !sleep && part == Part.VARIATION
            val hook = if (playHook) hookNote(s) else null
            if (hook != null) {
                piano.noteOn(hook.midi, hook.vel, stepsToSamples(hook.length * 2f), human(25f))
                lastPiano = hook.midi
            } else if (!playHook && rng.chance(density * strong)) {
                var bi = 0
                for (i in pianoPool.indices) if (kotlin.math.abs(pianoPool[i] - lastPiano) < kotlin.math.abs(pianoPool[bi] - lastPiano)) bi = i
                val mv = rng.pick(intArrayOf(-2, -1, -1, 1, 1, 2, 3, -3))
                val idx = (bi + mv).coerceIn(0, pianoPool.size - 1)
                val note = pianoPool[idx]
                piano.noteOn(note, rng.range(0.35f, 0.65f), stepsToSamples(rng.range(6f, 14f)), human(30f))
                // gelegentlich ein zweiter Ton dazu (Terz/Sexte darunter)
                if (rng.chance(0.2f) && idx >= 2) piano.noteOn(pianoPool[idx - 2], rng.range(0.25f, 0.45f), stepsToSamples(10f), ms(60f) + human(30f))
                lastPiano = note
            }
        }

        // Glocken: selten, hoch, weit im Raum
        if (chBells.on && s == 0) {
            if (bellNext <= 0) {
                val c = chord
                val pcs = c.pcs()
                val note = (if (sleep) 72 else 76) + rng.int(14)
                val snapped = (note..note + 11).first { Math.floorMod(it, 12) in pcs }
                bells.noteOn(snapped, rng.range(0.4f, 0.7f), stepsToSamples(16f), human(80f))
                bellNext = rng.int(if (sleep) 3 else 1, if (sleep) 7 else 4)
            }
            bellNext--
        }
    }

    override fun updateLevels() {
        val part = section.part
        val i = intensity
        chPad.target = 1f
        chBass.target = if (part == Part.FLOAT && !sleep) 0.6f else 0.9f
        chPiano.target = when (part) {
            Part.FLOAT -> if (sleep) 0f else 0.7f * above(0.3f, 0.2f)
            else -> if (sleep) 0.8f else 1f
        }
        chBells.target = when (part) {
            Part.VARIATION -> 1f
            Part.THEME -> above(0.4f, 0.2f)
            else -> above(0.6f, 0.2f)
        } * if (sleep) 0.6f else 1f
        chWaves.target = (0.35f + 0.65f * (1f - i)).coerceIn(0f, 1f)
        chDrone.target = if (sleep) 1f else 0.4f * (1f - i)
    }
}
