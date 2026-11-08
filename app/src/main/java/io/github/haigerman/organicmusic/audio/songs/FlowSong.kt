package io.github.haigerman.organicmusic.audio.songs

import io.github.haigerman.organicmusic.audio.dsp.Reverb
import io.github.haigerman.organicmusic.audio.instruments.Bass
import io.github.haigerman.organicmusic.audio.instruments.BassPreset
import io.github.haigerman.organicmusic.audio.instruments.Clap
import io.github.haigerman.organicmusic.audio.instruments.Crash
import io.github.haigerman.organicmusic.audio.instruments.FmPreset
import io.github.haigerman.organicmusic.audio.instruments.FmSynth
import io.github.haigerman.organicmusic.audio.instruments.HiHat
import io.github.haigerman.organicmusic.audio.instruments.Kick
import io.github.haigerman.organicmusic.audio.instruments.Perc
import io.github.haigerman.organicmusic.audio.instruments.PolySynth
import io.github.haigerman.organicmusic.audio.instruments.Shaker
import io.github.haigerman.organicmusic.audio.instruments.SynthPreset
import io.github.haigerman.organicmusic.audio.instruments.Wave
import io.github.haigerman.organicmusic.audio.music.Key
import io.github.haigerman.organicmusic.audio.music.Melody
import io.github.haigerman.organicmusic.audio.music.Scale

/**
 * Kreativ: leichter, positiver Deep-House-Groove. Weiche gerade Bassdrum, Shaker und
 * Conga, funky Bass, House-Piano-Akkorde, Mallet-Arpeggios und eine sonnige Melodie.
 * Hält wach und gut gelaunt, ohne zu drängen.
 */
class FlowSong(sr: Int, seed: Long, bpm: Float, intensity: Float) : Song(sr, seed, bpm, intensity) {

    override val reverb = Reverb(sr, roomSize = 0.86f, damping = 0.4f, inputGain = 0.013f)
    override val outputGain = 1.35f

    private val kick = Kick(sr, rng).apply {
        startHz = 135f; endHz = 50f; pitchDecay = 0.03f; decay = 0.36f; drive = 1.5f; click = 0.18f; tone = 6000f
    }
    private val clap = Clap(sr, rng).apply { tail = 0.2f }
    private val hat = HiHat(sr, rng).apply { closedDecay = 0.04f; openDecay = 0.22f; brightness = 0.8f; pan = 0.2f }
    private val shaker = Shaker(sr, rng).apply { decay = 0.05f; pan = -0.3f }
    private val conga = Perc(sr, rng).apply { decay = 0.18f; bend = 0.35f; noise = 0.12f; pan = 0.35f }
    private val bass = Bass(
        sr,
        BassPreset(
            saw = 0.4f, square = 0.2f, sine = 0.6f, cutoff = 380f, envAmount = 3f, fDecay = 0.14f,
            resonance = 1f, decay = 0.25f, sustain = 0.5f, release = 0.05f, glide = 0.05f, drive = 1.3f,
        ),
    )
    private val piano = FmSynth(
        sr, rng, 10,
        FmPreset(
            ratio = 1f, index = 2.3f, indexDecay = 0.22f, sustainIndex = 0.15f, decay = 1.5f, release = 0.18f,
            tine = 0.05f, tineDecay = 0.04f, tremoloDepth = 0f, detuneCents = 6f, spread = 0.35f, gain = 0.5f,
        ),
    )
    private val mallet = FmSynth(
        sr, rng, 6,
        FmPreset(
            ratio = 4f, index = 1.3f, indexDecay = 0.05f, sustainIndex = 0f, decay = 0.5f, release = 0.15f,
            tine = 0f, tremoloDepth = 0f, detuneCents = 1f, spread = 0.5f, gain = 0.6f,
        ),
    )
    private val pad = PolySynth(
        sr, rng, 8,
        SynthPreset(
            wave = Wave.SAW, oscs = 3, detuneCents = 12f, cutoff = 1100f, resonance = 0.6f, envAmount = 0.4f,
            fAttack = 0.5f, fDecay = 2f, fSustain = 0.6f, attack = 0.5f, decay = 1.5f, sustain = 0.85f, release = 1.2f,
            keyTrack = 0.3f, spread = 0.8f, wobble = 0.2f, wobbleRate = 0.12f, gain = 0.5f,
        ),
    )
    private val leadPresets = listOf(
        SynthPreset(
            wave = Wave.TRIANGLE, oscs = 2, detuneCents = 6f, cutoff = 3000f, resonance = 0.7f, envAmount = 0.5f,
            fDecay = 0.3f, fSustain = 0.6f, attack = 0.015f, decay = 0.4f, sustain = 0.7f, release = 0.25f,
            vibratoCents = 10f, vibratoRate = 5f, spread = 0.1f, gain = 0.7f,
        ),
        SynthPreset(
            wave = Wave.SQUARE, oscs = 1, cutoff = 1600f, resonance = 0.8f, envAmount = 1.5f, fDecay = 0.2f,
            fSustain = 0.3f, attack = 0.005f, decay = 0.3f, sustain = 0.5f, release = 0.2f,
            vibratoCents = 6f, vibratoRate = 5.5f, spread = 0.1f, gain = 0.5f,
        ),
    )
    private val lead = PolySynth(sr, rng, 3, leadPresets[0])
    private val crash = Crash(sr, rng).apply { decay = 1.8f }

    private val chKick = channel(kick, 0.68f, fade = 0.03f)
    private val chClap = channel(clap, 0.32f, reverb = 0.3f, fade = 0.03f)
    private val chHat = channel(hat, 0.15f, fade = 0.2f)
    private val chShaker = channel(shaker, 0.12f, fade = 0.5f)
    private val chConga = channel(conga, 0.22f, reverb = 0.2f, fade = 0.5f)
    private val chBass = channel(bass, 0.42f, duck = 0.6f, fade = 0.1f)
    private val chPiano = channel(piano, 0.36f, reverb = 0.3f, delay = 0.12f, duck = 0.3f, fade = 1.5f)
    private val chMallet = channel(mallet, 0.2f, reverb = 0.3f, delay = 0.3f, duck = 0.2f, fade = 1.5f)
    private val chPad = channel(pad, 0.2f, reverb = 0.5f, duck = 0.6f, fade = 3f)
    private val chLead = channel(lead, 0.2f, reverb = 0.35f, delay = 0.3f, duck = 0.2f, fade = 1.5f)
    private val chCrash = channel(crash, 0.3f, reverb = 0.3f, fade = 0.02f)

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
    private var congaHigh = BooleanArray(16)
    private var arpPattern = IntArray(16)
    private var voicing: IntArray? = null
    private var padVoicing: IntArray? = null
    private var arpVoicing = intArrayOf(72, 76, 79)

    init {
        delayBeats = 0.75f
        delay.feedback = 0.4f
        duckRelease = 0.22f
        chCrash.target = 1f
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
        congaSteps = (0 until 16).filter { it % 4 != 0 && rng.chance(if (it % 2 == 1) 0.25f else 0.35f) }.toIntArray()
        congaHigh = BooleanArray(16) { rng.chance(0.5f) }
        arpPattern = Melody.arpPattern(rng, 4, rng.range(0.4f, 0.8f))
        lead.preset = rng.pick(leadPresets)
        buildHooks(low = 69, high = 86, notesPerHalf = rng.int(4, 7), syncopation = 0.55f, pentatonic = rng.chance(0.6f), maxLength = 6)
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
        when (part) {
            Part.GROOVE, Part.THEME -> crash.trigger(0.6f, boom = false)
            else -> {}
        }
    }

    override fun onBar() {
        val c = chord
        if (pieceBar % chordBars == 0) {
            voicing = c.voicing(57, 76, voicing)
            arpVoicing = c.voicing(70, 86, arpVoicing)
            if (chPad.on) {
                val pv = c.voicing(52, 70, padVoicing)
                padVoicing = pv
                val len = stepsToSamples(chordBars * 16f - 0.5f)
                for (n in pv) pad.noteOn(n, 0.65f, len)
            }
        }
        pad.cutoffMul = when (section.part) {
            Part.BREAK -> 0.7f
            Part.INTRO -> 0.6f
            else -> 1f
        }
    }

    override fun onStep(s: Int) {
        val part = section.part
        val v = voicing ?: return

        if (chKick.on && s % 4 == 0) {
            kick.trigger(if (s == 0) 0.95f else 0.85f)
            pump()
        }
        if (chClap.on && (s == 4 || s == 12)) clap.trigger(0.8f, human(4f))
        if (chHat.on) {
            if (s % 4 == 2) hat.trigger(0.7f, open = true, delay = human(3f))
            else if (s % 2 == 1 && rng.chance(0.3f)) hat.trigger(0.35f, open = false, delay = human(4f))
        }
        if (chShaker.on) shaker.trigger((if (s % 2 == 0) 0.6f else 0.85f) * rng.range(0.8f, 1.05f), human(5f))
        if (chConga.on && s in congaSteps) {
            val f = if (congaHigh[s]) 310f else 215f
            conga.trigger(rng.range(0.55f, 0.85f), f, human(6f))
        }

        if (chBass.on && s in bassPattern) {
            val root = chord.rootAtOrAbove(35)
            val note = root + (if (bassOct[s]) 12 else 0) + (if (bassFifth[s]) 7 else 0)
            val next = bassPattern.firstOrNull { it > s } ?: 16
            val len = (next - s).coerceAtMost(3) - 0.4f
            bass.noteOn(note, if (s == 0) 0.95f else 0.8f, stepsToSamples(len), human(3f))
        }

        if (chPiano.on) {
            for ((start, len) in stabs) {
                if (start != s) continue
                val vel = rng.range(0.6f, 0.8f)
                for ((i, n) in v.withIndex()) piano.noteOn(n, vel, stepsToSamples(len - 0.2f), ms(3f * i) + human(4f))
            }
        }

        if (chMallet.on) {
            val idx = arpPattern[s]
            if (idx >= 0 && s % 2 == 0) mallet.noteOn(Melody.arpNote(arpVoicing, idx), rng.range(0.55f, 0.8f), stepsToSamples(2f), human(5f))
        }

        if (chLead.on && (part == Part.THEME || part == Part.BREAK)) {
            hookNote(s)?.let { lead.noteOn(it.midi, it.vel, stepsToSamples(it.length - 0.3f), human(6f)) }
        }
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
        chShaker.target = lv(1f, 1f, 1f, 0.6f, 1f) * above(0.35f)
        chConga.target = lv(0.6f, 0.7f, 1f, 0.5f, 0.6f) * above(0.55f)
        chBass.target = lv(0f, 1f, 1f, 0f, 0.8f)
        chPiano.target = lv(0.7f, 1f, 0.9f, 0.8f, 0.8f)
        chMallet.target = lv(0.5f, above(0.4f), above(0.3f), 0.8f, 0.4f)
        chPad.target = lv(0.7f, 0.5f, 0.6f, 1f, 0.6f)
        chLead.target = lv(0f, 0f, above(0.4f), 0.7f * above(0.5f), 0f)
    }
}
