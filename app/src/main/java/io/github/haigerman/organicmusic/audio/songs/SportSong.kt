package io.github.haigerman.organicmusic.audio.songs

import io.github.haigerman.organicmusic.audio.dsp.Reverb
import io.github.haigerman.organicmusic.audio.instruments.Bass
import io.github.haigerman.organicmusic.audio.instruments.BassPreset
import io.github.haigerman.organicmusic.audio.instruments.Clap
import io.github.haigerman.organicmusic.audio.instruments.Crash
import io.github.haigerman.organicmusic.audio.instruments.HiHat
import io.github.haigerman.organicmusic.audio.instruments.Kick
import io.github.haigerman.organicmusic.audio.instruments.PolySynth
import io.github.haigerman.organicmusic.audio.instruments.Riser
import io.github.haigerman.organicmusic.audio.instruments.Snare
import io.github.haigerman.organicmusic.audio.instruments.SynthPreset
import io.github.haigerman.organicmusic.audio.instruments.Wave
import io.github.haigerman.organicmusic.audio.music.Key
import io.github.haigerman.organicmusic.audio.music.Melody
import io.github.haigerman.organicmusic.audio.music.Scale

/**
 * Sport: treibende elektronische Tanzmusik. Gerade Bassdrum auf jedem Viertel, pumpende
 * Flächen (Sidechain), rollender Bass, Arpeggios – und ein klassischer Spannungsbogen aus
 * Groove → Steigerung (Snare-Wirbel, Rauschen, Filterfahrt) → Höhepunkt → Atempause.
 */
class SportSong(sr: Int, seed: Long, bpm: Float, intensity: Float) : Song(sr, seed, bpm, intensity) {

    override val reverb = Reverb(sr, roomSize = 0.84f, damping = 0.4f, inputGain = 0.014f)
    override val outputGain = 0.85f

    private val kick = Kick(sr, rng).apply {
        startHz = 165f; endHz = 49f; pitchDecay = 0.032f; decay = 0.42f; drive = 2.2f; click = 0.35f
    }
    private val clap = Clap(sr, rng)
    private val snare = Snare(sr, rng).apply { toneHz = 205f; noiseDecay = 0.13f; toneMix = 0.35f }
    private val hat = HiHat(sr, rng).apply { closedDecay = 0.04f; pan = 0.15f }
    private val openHat = HiHat(sr, rng).apply { openDecay = 0.28f; pan = -0.12f }
    private val bass = Bass(
        sr,
        BassPreset(
            saw = 0.75f, sine = 0.55f, cutoff = 240f, envAmount = 4.5f, fDecay = 0.11f,
            resonance = 1.5f, decay = 0.14f, sustain = 0.6f, release = 0.035f, drive = 1.7f,
        ),
    )
    private val pad = PolySynth(
        sr, rng, 8,
        SynthPreset(
            wave = Wave.SAW, oscs = 3, detuneCents = 16f, cutoff = 1500f, resonance = 0.7f,
            envAmount = 0.5f, fAttack = 0.3f, fDecay = 1.5f, fSustain = 0.5f,
            attack = 0.06f, decay = 1f, sustain = 0.85f, release = 0.5f,
            keyTrack = 0.3f, spread = 0.8f, gain = 0.55f,
        ),
    )
    private val arp = PolySynth(
        sr, rng, 6,
        SynthPreset(
            wave = Wave.SAW, oscs = 2, detuneCents = 8f, cutoff = 650f, resonance = 1.3f,
            envAmount = 5f, fDecay = 0.13f, fSustain = 0f,
            attack = 0.002f, decay = 0.22f, sustain = 0f, release = 0.12f,
            keyTrack = 0.6f, spread = 0.35f, gain = 0.7f,
        ),
    )
    private val leadPresets = listOf(
        SynthPreset(
            wave = Wave.SQUARE, oscs = 2, detuneCents = 10f, cutoff = 2000f, resonance = 0.9f,
            envAmount = 1.5f, fDecay = 0.3f, fSustain = 0.4f,
            attack = 0.008f, decay = 0.3f, sustain = 0.7f, release = 0.2f,
            vibratoCents = 12f, vibratoRate = 5.5f, spread = 0.1f, gain = 0.5f,
        ),
        SynthPreset(
            wave = Wave.SAW, oscs = 3, detuneCents = 12f, cutoff = 1300f, resonance = 1f,
            envAmount = 3f, fDecay = 0.25f, fSustain = 0.25f,
            attack = 0.004f, decay = 0.35f, sustain = 0.45f, release = 0.2f,
            spread = 0.2f, gain = 0.5f,
        ),
    )
    private val lead = PolySynth(sr, rng, 3, leadPresets[0])
    private val crash = Crash(sr, rng)
    private val riser = Riser(sr, rng)

    private val chKick = channel(kick, 0.8f, fade = 0.02f)
    private val chClap = channel(clap, 0.42f, reverb = 0.25f, fade = 0.02f)
    private val chSnare = channel(snare, 0.38f, reverb = 0.3f, fade = 0.05f)
    private val chHat = channel(hat, 0.2f, fade = 0.05f)
    private val chOpen = channel(openHat, 0.17f, reverb = 0.1f, fade = 0.05f)
    private val chBass = channel(bass, 0.5f, duck = 0.85f, fade = 0.05f)
    private val chPad = channel(pad, 0.3f, reverb = 0.45f, duck = 0.85f, fade = 3f)
    private val chArp = channel(arp, 0.26f, reverb = 0.3f, delay = 0.35f, duck = 0.5f, fade = 1.5f)
    private val chLead = channel(lead, 0.24f, reverb = 0.35f, delay = 0.3f, duck = 0.3f, fade = 1f)
    private val chCrash = channel(crash, 0.45f, reverb = 0.25f, fade = 0.02f)
    private val chRiser = channel(riser, 0.3f, reverb = 0.4f, fade = 0.02f)

    private val progressions = listOf(
        intArrayOf(0, 5, 2, 6),
        intArrayOf(0, 6, 5, 6),
        intArrayOf(0, 5, 3, 6),
        intArrayOf(5, 6, 0, 0),
        intArrayOf(0, 2, 6, 5),
        intArrayOf(0, 0, 5, 6),
        intArrayOf(0, 3, 5, 4),
    )

    private var bassStyle = 0
    private var hatStyle = 0
    private var kickPickup = false
    private var arpPattern = IntArray(16)
    private var padVoicing: IntArray? = null
    private var arpVoicing = intArrayOf(60, 64, 67)
    private var builds = 0

    init {
        delayBeats = 0.75f
        delay.feedback = 0.38f
        duckRelease = 0.2f
        chCrash.target = 1f
        chRiser.target = 1f
    }

    override fun newPiece(first: Boolean) {
        key = if (first) {
            Key(rng.pick(intArrayOf(9, 5, 7, 2, 0, 4, 11)), if (rng.chance(0.75f)) Scale.MINOR else Scale.DORIAN)
        } else if (rng.chance(0.55f)) key.related(rng.int(4)) else key
        progression = rng.pick(progressions)
        chordBars = if (rng.chance(0.7f)) 2 else 1
        chordSize = 3
        bassStyle = rng.int(3)
        hatStyle = rng.int(2)
        kickPickup = rng.chance(0.5f)
        arpPattern = Melody.arpPattern(rng, 3, rng.range(0.6f, 1f))
        lead.preset = rng.pick(leadPresets)
        buildHooks(low = 69, high = 86, notesPerHalf = rng.int(6, 9), syncopation = 0.6f, pentatonic = rng.chance(0.4f), maxLength = 4)
        swing = 0f
    }

    override fun makePlan(): List<Section> = buildList {
        add(Section(Part.INTRO, 8))
        add(Section(Part.GROOVE, if (rng.chance(0.5f)) 16 else 8))
        add(Section(Part.BUILD, 8))
        add(Section(Part.DROP, 16))
        add(Section(Part.BREAK, 8))
        add(Section(Part.BUILD, 8))
        add(Section(Part.DROP, 16))
        add(Section(Part.GROOVE, 8))
    }

    override fun onSection(part: Part) {
        when (part) {
            Part.DROP -> crash.trigger(1f, boom = true)
            Part.GROOVE -> crash.trigger(0.6f, boom = false)
            Part.BUILD -> {
                riser.start(section.bars * 4 * 60f / bpm, 0.9f)
                builds++
            }
            Part.BREAK -> crash.trigger(0.5f, boom = true)
            else -> {}
        }
    }

    override fun onBar() {
        val c = chord
        if (pieceBar % chordBars == 0) {
            val v = c.voicing(55, 72, padVoicing)
            padVoicing = v
            if (chPad.on) {
                val len = stepsToSamples(chordBars * 16f - 0.5f)
                for ((i, n) in v.withIndex()) pad.noteOn(n, 0.75f, len, i * ms(4f))
            }
            arpVoicing = c.voicing(60, 76, arpVoicing)
        }
    }

    override fun onStep(s: Int) {
        val part = section.part
        val lastBar = barsLeftInSection == 1
        val gapBeforeDrop = part == Part.BUILD && lastBar && s >= 12

        // Filterfahrten
        when (part) {
            Part.BUILD -> {
                val p = sectionProgress(s)
                arp.cutoffMul = 0.35f + 1.1f * p * p
                pad.cutoffMul = 0.4f + 0.8f * p
            }
            Part.INTRO -> {
                arp.cutoffMul = 0.3f + 0.6f * sectionProgress(s)
                pad.cutoffMul = 0.5f
            }
            Part.BREAK -> {
                arp.cutoffMul = 0.45f
                pad.cutoffMul = 0.6f
            }
            Part.DROP -> {
                arp.cutoffMul = 1.25f
                pad.cutoffMul = 1.05f
            }
            else -> {
                arp.cutoffMul = 0.85f
                pad.cutoffMul = 0.85f
            }
        }

        // Bassdrum
        if (chKick.on && !gapBeforeDrop) {
            val onBeat = s % 4 == 0
            val pickup = kickPickup && s == 14 && pieceBar % 4 == 3 && part != Part.BUILD
            if (onBeat || pickup) {
                kick.trigger(if (onBeat) 1f else 0.65f)
                pump()
            }
        }

        // Clap auf 2 und 4
        if (chClap.on && (s == 4 || s == 12) && !gapBeforeDrop) clap.trigger(0.9f, human(2f))

        // Snare-Wirbel in der Steigerung: Viertel → Achtel → Sechzehntel
        if (part == Part.BUILD && chSnare.on && !(lastBar && s >= 14)) {
            val p = sectionProgress(s)
            val every = when {
                p < 0.5f -> 4
                p < 0.75f -> 2
                else -> 1
            }
            if (s % every == 0) snare.trigger(0.25f + 0.7f * p)
        }

        // Hi-Hats
        if (chHat.on && !gapBeforeDrop) {
            val play = if (hatStyle == 0) true else s % 2 == 0
            if (play && s % 4 != 2) {
                val v = (if (s % 2 == 0) 0.7f else 0.45f) * rng.range(0.85f, 1.05f)
                hat.trigger(v, open = false, delay = human(2f))
            }
        }
        if (chOpen.on && s % 4 == 2 && !gapBeforeDrop) openHat.trigger(0.8f, open = true)

        // Bass
        if (chBass.on && !gapBeforeDrop) {
            val root = chord.rootAtOrAbove(33)
            when (bassStyle) {
                0 -> if (s % 4 == 2) bass.noteOn(root, 0.95f, stepsToSamples(1.6f))
                1 -> if (s % 4 != 0) {
                    val oct = if (s % 4 == 3 && rng.chance(0.2f)) 12 else 0
                    bass.noteOn(root + oct, if (s % 4 == 2) 1f else 0.72f, stepsToSamples(0.75f))
                }
                else -> if (s % 4 == 2) bass.noteOn(root + if (s % 8 == 6) 12 else 0, 0.95f, stepsToSamples(1.4f))
            }
        }

        // Arpeggio
        if (chArp.on) {
            val idx = arpPattern[s]
            if (idx >= 0) {
                val note = Melody.arpNote(arpVoicing, idx)
                arp.noteOn(note, if (s % 4 == 0) 0.95f else 0.75f, stepsToSamples(1.2f))
            }
        }

        // Melodie
        if (chLead.on) {
            hookNote(s)?.let { lead.noteOn(it.midi, it.vel, stepsToSamples(it.length - 0.3f)) }
        }
    }

    override fun updateLevels() {
        val part = section.part
        fun lv(intro: Float, groove: Float, build: Float, drop: Float, brk: Float) = when (part) {
            Part.INTRO -> intro
            Part.BUILD -> build
            Part.DROP -> drop
            Part.BREAK -> brk
            else -> groove
        }
        chKick.target = lv(1f, 1f, 1f, 1f, 0f)
        chClap.target = lv(0f, 1f, 0f, 1f, 0f) * above(0.1f)
        chSnare.target = lv(0f, 0f, 1f, 0f, 0f) * above(0.2f)
        chHat.target = lv(above(0.2f), 1f, 1f, 1f, 0.6f * above(0.45f))
        chOpen.target = lv(0f, above(0.35f), above(0.5f), above(0.15f), 0f)
        chBass.target = lv(above(0.3f) * 0.8f, 1f, 0.85f, 1f, 0f)
        chPad.target = lv(0.6f, 0.65f, 0.9f, 0.75f, 1f) * (0.5f + 0.5f * above(0.25f, 0.25f))
        chArp.target = lv(0.6f * above(0.3f), above(0.45f), 1f, above(0.25f), 0.8f)
        chLead.target = lv(0f, 0f, 0f, above(0.55f), 0.55f * above(0.7f))
    }
}
