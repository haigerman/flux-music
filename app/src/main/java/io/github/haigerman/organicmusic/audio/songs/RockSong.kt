package io.github.haigerman.organicmusic.audio.songs

import io.github.haigerman.organicmusic.audio.dsp.Reverb
import io.github.haigerman.organicmusic.audio.instruments.Bass
import io.github.haigerman.organicmusic.audio.instruments.BassPreset
import io.github.haigerman.organicmusic.audio.instruments.Crash
import io.github.haigerman.organicmusic.audio.instruments.ElectricGuitar
import io.github.haigerman.organicmusic.audio.instruments.HiHat
import io.github.haigerman.organicmusic.audio.instruments.Kick
import io.github.haigerman.organicmusic.audio.instruments.Perc
import io.github.haigerman.organicmusic.audio.instruments.Snare
import io.github.haigerman.organicmusic.audio.music.Key
import io.github.haigerman.organicmusic.audio.music.NOTE_NAMES
import io.github.haigerman.organicmusic.audio.music.Scale

/**
 * Sport · Rock: geradliniger Hardrock im Stil der großen Pub-Rock-Bands der 70er/80er.
 * Zwei knackig verzerrte Rhythmusgitarren links und rechts mit Powerchords und viel Luft
 * zwischen den Akkorden, ein stur durchlaufender Achtel-Bass, ein trockener Rock-Beat
 * (Bassdrum auf 1 und 3, Snare auf 2 und 4) und im Solo Blues-Licks mit Bendings.
 * Alle Riffs und Licks werden nach Stilregeln neu erfunden – nichts ist nachgespielt.
 */
class RockSong(sr: Int, seed: Long, bpm: Float, intensity: Float) : Song(sr, seed, bpm, intensity) {

    override val reverb = Reverb(sr, roomSize = 0.72f, damping = 0.45f, inputGain = 0.013f)
    override val outputGain = 0.54f

    // ---------- Band ----------
    private val kick = Kick(sr, rng).apply {
        startHz = 125f; endHz = 58f; pitchDecay = 0.018f; decay = 0.26f; drive = 2.2f; click = 0.7f; tone = 8000f
    }
    private val snare = Snare(sr, rng).apply {
        toneHz = 195f; toneMix = 0.5f; noiseDecay = 0.24f; toneDecay = 0.13f; brightness = 9000f
    }
    private val hat = HiHat(sr, rng).apply { closedDecay = 0.07f; openDecay = 0.2f; pan = 0.25f }
    private val toms = arrayOf(
        Perc(sr, rng).apply { decay = 0.35f; bend = 0.3f; noise = 0.3f; pan = -0.25f },
        Perc(sr, rng).apply { decay = 0.4f; bend = 0.3f; noise = 0.3f; pan = 0.05f },
        Perc(sr, rng).apply { decay = 0.5f; bend = 0.3f; noise = 0.3f; pan = 0.3f },
    )
    private val tomHz = floatArrayOf(190f, 140f, 98f)
    private val crash = Crash(sr, rng).apply { decay = 2.2f }

    private val gtrL = ElectricGuitar(sr, rng, 4, drive = 7f).apply { pan = -0.75f }
    private val gtrR = ElectricGuitar(sr, rng, 4, drive = 8f).apply { pan = 0.75f }
    private val lead = ElectricGuitar(sr, rng, 1, drive = 14f).apply { pan = 0.2f }
    private val bass = Bass(
        sr,
        BassPreset(
            saw = 0.55f, square = 0.25f, sine = 0.55f, cutoff = 650f, envAmount = 2.2f, fDecay = 0.09f,
            resonance = 0.9f, attack = 0.002f, decay = 0.22f, sustain = 0.65f, release = 0.04f, drive = 1.9f,
        ),
    )

    private val chKick = channel(kick, 0.6f, reverb = 0.05f, fade = 0.02f)
    private val chSnare = channel(snare, 0.5f, reverb = 0.35f, fade = 0.02f)
    private val chHat = channel(hat, 0.15f, reverb = 0.05f, fade = 0.05f)
    private val chToms = toms.map { channel(it, 0.36f, reverb = 0.25f, fade = 0.02f) }
    private val chCrash = channel(crash, 0.3f, reverb = 0.2f, fade = 0.02f)
    private val chGtrL = channel(gtrL, 0.38f, reverb = 0.12f, fade = 0.05f)
    private val chGtrR = channel(gtrR, 0.38f, reverb = 0.12f, fade = 0.3f)
    private val chLead = channel(lead, 0.3f, reverb = 0.3f, delay = 0.22f, fade = 0.3f)
    private val chBass = channel(bass, 0.3f, fade = 0.05f)

    // ---------- Riffs ----------
    private class Hit(val kind: Int, val root: Int, val len: Int, val full: Boolean)

    private class Riff(val bars: Int) {
        val hits = arrayOfNulls<Hit>(bars * 16)
        val roots = IntArray(bars * 16)
    }

    private class LeadNote(val midi: Int, val len: Int, val bend: Float, val vib: Float, val vel: Float)

    private companion object {
        const val RING = 0
        const val SHORT = 1
        const val MUTE = 2
        const val NOTE = 3
        const val CHOKE = 4

        // Zellen über vier Achtel: X = Akkord klingen lassen, x = kurz, m = abgedämpft,
        // - = abstoppen, . = weiterklingen
        val verseFirst = listOf("X...", "X..x", "X.-x", "X-x-", "X.mm", "X..-", "X-X.", "X--x")
        val verseOther = listOf("-x-x", "mmmm", "X..-", "-X..", "x-x-", "X.mm", "--x-", "-xX.", "X-x-")
        val chorusFirst = listOf("X...", "X..x", "X.X.", "X...")
        val chorusOther = listOf("X...", ".X..", "..X.", "x-X.", "X.X.", "...x")

        // Halbtöne über dem Grundton: I, IV, bVII, bIII, V – klassisches Rock-Vokabular
        val verseHarmony = listOf(
            intArrayOf(0, 0, 5, 0), intArrayOf(0, 0, 10, 5), intArrayOf(0, 10, 5, 0), intArrayOf(0, 0, 0, 10),
            intArrayOf(0, 5, 0, 5), intArrayOf(0, 0, 3, 5), intArrayOf(0, 0, 7, 5),
        )
        val chorusHarmony = listOf(
            intArrayOf(0, 10, 5, 0), intArrayOf(5, 0, 10, 0), intArrayOf(10, 5, 0, 0), intArrayOf(0, 3, 5, 5),
            intArrayOf(5, 10, 0, 0), intArrayOf(0, 10, 5, 5), intArrayOf(0, 5, 10, 5),
        )

        // Blues-Läufe auf der tiefen Saite, die zurück zum Grundton führen
        val runs = listOf(intArrayOf(0, 3, 4, 7), intArrayOf(12, 10, 7, 5), intArrayOf(7, 5, 3, 0), intArrayOf(3, 4, 7, 9), intArrayOf(10, 9, 7, 5))

        val kickPatterns = listOf(intArrayOf(0, 8), intArrayOf(0, 8, 10), intArrayOf(0, 6, 8), intArrayOf(0, 8, 11))

        // Fills im letzten Takt einer Phrase: (Schritt, 0 = Snare, 1–3 = Toms hoch/mittel/tief)
        val fills = listOf(
            listOf(8 to 0, 10 to 0, 12 to 0, 14 to 0),
            listOf(12 to 0, 13 to 0, 14 to 0, 15 to 0),
            listOf(8 to 1, 10 to 1, 12 to 2, 13 to 2, 14 to 3, 15 to 3),
            listOf(8 to 0, 10 to 0, 12 to 1, 13 to 2, 14 to 3, 15 to 3),
            listOf(12 to 0, 13 to 1, 14 to 2, 15 to 3),
        )
    }

    private var tonic = 9
    private var verseRiff = Riff(2)
    private var chorusRiff = Riff(4)
    private var kickPattern = kickPatterns[0]
    private var introAlone = 2
    private var fill: List<Pair<Int, Int>>? = null
    private var crashNext = false
    private var gtrSilent = false
    private var lick = arrayOfNulls<LeadNote>(32)
    private var currentRoot = 45
    private var currentFull = false

    init {
        delayBeats = 0.75f
        delay.feedback = 0.3f
        chCrash.target = 1f
        for (c in chToms) c.target = 1f
    }

    /** Tiefster Grundton auf der Gitarre (E2 … Dis3). */
    private fun lowRoot(pc: Int): Int = 40 + Math.floorMod(pc - 4, 12)

    private fun makeRiff(harmony: IntArray, bars: Int, chorus: Boolean): Riff {
        val riff = Riff(bars)
        val steps = bars * 16
        val slotLen = steps / harmony.size
        for (k in harmony.indices) {
            val root = lowRoot(tonic + harmony[k])
            for (s in 0 until slotLen) riff.roots[k * slotLen + s] = root
            for (h in 0 until slotLen / 8) {
                val first = h == 0
                val cell = when {
                    chorus && first -> rng.pick(chorusFirst)
                    chorus -> rng.pick(chorusOther)
                    first -> rng.pick(verseFirst)
                    else -> rng.pick(verseOther)
                }
                applyCell(riff, cell, k * slotLen + h * 8, root, chorus)
            }
        }
        if (!chorus && rng.chance(0.55f)) {
            // letzter halber Takt: ein Blues-Lauf auf der tiefen Saite
            val base = steps - 8
            for (s in base until steps) riff.hits[s] = null
            val run = rng.pick(runs)
            val low = lowRoot(tonic)
            for (e in 0 until 4) riff.hits[base + e * 2] = Hit(NOTE, low + run[e], 2, false)
        }
        return riff
    }

    private fun applyCell(riff: Riff, cell: String, base: Int, root: Int, full: Boolean) {
        for (e in 0 until 4) {
            val step = base + e * 2
            when (cell[e]) {
                'X' -> {
                    var len = 2
                    var j = e + 1
                    while (j < 4 && cell[j] == '.') { len += 2; j++ }
                    riff.hits[step] = Hit(RING, root, len, full)
                }
                'x' -> riff.hits[step] = Hit(SHORT, root, 1, full)
                'm' -> riff.hits[step] = Hit(MUTE, root, 2, false)
                '-' -> riff.hits[step] = Hit(CHOKE, root, 0, false)
            }
        }
    }

    /** Blues-Lick über zwei Takte für das Solo. */
    private fun makeLick(): Array<LeadNote?> {
        val out = arrayOfNulls<LeadNote>(32)
        val pent = setOf(0, 3, 5, 7, 10)
        val notes = (62..86).filter { Math.floorMod(it - tonic, 12) in pent }
        val center = notes.size / 2
        var idx = center + rng.int(-2, 2)
        var dir = if (rng.chance(0.5f)) 1 else -1
        for (beat in 0 until 8) {
            val base = beat * 4
            if (beat == 7 || (beat == 3 && rng.chance(0.3f))) {
                // Phrasenende: langer Ton auf Grundton oder Quinte, mit Vibrato
                val targets = notes.withIndex().filter { Math.floorMod(it.value - tonic, 12) == 0 || Math.floorMod(it.value - tonic, 12) == 7 }
                val t = targets.minByOrNull { kotlin.math.abs(it.index - idx) } ?: continue
                idx = t.index
                out[base] = LeadNote(t.value, 4, if (rng.chance(0.5f)) 2f else 0f, 0.007f, 0.95f)
                continue
            }
            val r = rng.float()
            when {
                r < 0.15f -> {} // Pause
                r < 0.48f -> for (k in 0 until 4) { // Sechzehntel-Lauf
                    idx += dir
                    if (idx <= 0 || idx >= notes.size - 1) dir = -dir
                    idx = idx.coerceIn(0, notes.size - 1)
                    out[base + k] = LeadNote(notes[idx], 1, 0f, 0f, if (k == 0) 0.95f else 0.8f)
                }
                r < 0.75f -> for (k in 0 until 2) { // zwei Achtel
                    idx = (idx + rng.pick(intArrayOf(-2, -1, 1, 2))).coerceIn(0, notes.size - 1)
                    out[base + k * 2] = LeadNote(notes[idx], 2, 0f, 0f, 0.88f)
                }
                else -> { // langer, gezogener Ton
                    idx = (idx + rng.pick(intArrayOf(-1, 1, 2))).coerceIn(0, notes.size - 1)
                    val bend = if (rng.chance(0.6f)) (if (Math.floorMod(notes[idx] - tonic, 12) == 3) 1f else 2f) else 0f
                    out[base] = LeadNote(notes[idx], 4, bend, 0.006f, 0.95f)
                }
            }
        }
        return out
    }

    // ---------- Songaufbau ----------

    override fun newPiece(first: Boolean) {
        val keys = intArrayOf(4, 9, 7, 2, 11, 0)
        tonic = if (first) rng.pick(keys) else {
            var t = rng.pick(keys)
            if (t == tonic) t = rng.pick(keys)
            t
        }
        key = Key(tonic, Scale.MAJOR)
        progression = intArrayOf(0)
        chordBars = 1
        swing = 0f
        verseRiff = makeRiff(rng.pick(verseHarmony), 2, chorus = false)
        chorusRiff = makeRiff(rng.pick(chorusHarmony), 4, chorus = true)
        kickPattern = rng.pick(kickPatterns)
        introAlone = if (rng.chance(0.6f)) 2 else 0
    }

    override fun makePlan(): List<Section> = listOf(
        Section(Part.INTRO, 4),
        Section(Part.VERSE, 8),
        Section(Part.CHORUS, 8),
        Section(Part.VERSE, 8),
        Section(Part.CHORUS, 8),
        Section(Part.SOLO, 8),
        Section(Part.BUILD, 4),
        Section(Part.CHORUS, 8),
        Section(Part.CHORUS, 8),
    )

    override fun keyName(): String = key.name

    override fun chordName(): String = NOTE_NAMES[Math.floorMod(currentRoot, 12)] + if (currentFull) "" else "5"

    private val drumsIn: Boolean
        get() = !(section.part == Part.INTRO && barInSection < introAlone)

    override fun onSection(part: Part) {
        if (part == Part.VERSE || part == Part.CHORUS || part == Part.SOLO) crashNext = true
    }

    override fun onBar() {
        val part = section.part
        if (part == Part.INTRO && barInSection == introAlone && introAlone > 0) crashNext = true
        if (crashNext && drumsIn) {
            crash.trigger(if (part == Part.CHORUS) 1f else 0.75f, boom = false)
            crashNext = false
        }
        // Fill im letzten Takt einer Phrase (oder vor dem nächsten Teil)
        val phraseEnd = barsLeftInSection == 1 || barInSection % 8 == 7
        fill = if (drumsIn && phraseEnd && rng.chance(0.35f + 0.6f * intensity)) rng.pick(fills) else null
        if (fill != null && barsLeftInSection == 1) crashNext = true

        if (part == Part.SOLO && barInSection % 2 == 0) lick = makeLick()

        val riff = riffFor(part)
        currentRoot = if (riff != null) riff.roots[(barInSection % riff.bars) * 16] else lowRoot(tonic)
        currentFull = part == Part.CHORUS
    }

    private fun riffFor(part: Part): Riff? = when (part) {
        Part.CHORUS -> chorusRiff
        Part.BUILD -> null
        else -> verseRiff
    }

    override fun onStep(s: Int) {
        val part = section.part
        val fillNow = fill?.let { f -> s >= f.first().first } ?: false

        // ---- Schlagzeug ----
        if (drumsIn) {
            if (chKick.on && !fillNow) {
                val hitKick = if (part == Part.BUILD) s % 4 == 0 else s in kickPattern
                if (hitKick) kick.trigger(if (s == 0) 1f else 0.9f, human(3f))
            }
            if (chSnare.on && !fillNow) {
                if (s == 4 || s == 12) snare.trigger(0.95f, ms(5f) + human(4f)) // minimal hinter dem Beat
                if (part == Part.BUILD && barsLeftInSection <= 2) {
                    val every = if (barsLeftInSection == 1) 1 else 2
                    if (s % every == 0 && s != 4 && s != 12) snare.trigger(0.35f + 0.04f * s, human(2f))
                }
            }
            if (chHat.on && !fillNow && s % 2 == 0) {
                val loose = part == Part.CHORUS || part == Part.SOLO
                hat.trigger((if (s % 4 == 0) 0.85f else 0.6f) * rng.range(0.9f, 1.05f), open = loose && intensity > 0.35f, delay = human(3f))
            }
            fill?.forEach { (st, drum) ->
                if (st == s) {
                    val v = 0.6f + 0.3f * (s / 15f)
                    if (drum == 0) snare.trigger(v, human(3f)) else toms[drum - 1].trigger(v, tomHz[drum - 1], human(3f))
                }
            }
        }

        // ---- Gitarren ----
        val riff = riffFor(part)
        if (riff == null) {
            // Steigerung: abgedämpfte Achtel auf dem Grundton, immer kräftiger
            if (s % 2 == 0) {
                val root = lowRoot(tonic)
                val v = 0.55f + 0.4f * sectionProgress(s)
                val notes = intArrayOf(root, root + 7)
                gtrL.strike(notes, v, muted = true, ringSeconds = 0.13f, strumMs = 3f)
                if (chGtrR.on) gtrR.strike(notes, v, muted = true, ringSeconds = 0.13f, strumMs = 3f, delay = ms(rng.range(3f, 9f)))
                gtrSilent = false
            }
        } else {
            val pos = (barInSection % riff.bars) * 16 + s
            riff.hits[pos]?.let { h ->
                play(gtrL, h, human(4f))
                if (chGtrR.on) play(gtrR, h, ms(rng.range(4f, 11f)))
                gtrSilent = h.kind == CHOKE
            }
        }

        // ---- Bass: stur durchlaufende Achtel ----
        if (chBass.on && drumsIn && s % 2 == 0 && !gtrSilent) {
            val root = (riff?.roots?.get((barInSection % riff.bars) * 16 + s) ?: lowRoot(tonic)) - 12
            bass.noteOn(root, if (s % 4 == 0) 0.95f else 0.82f, stepsToSamples(1.7f), human(3f))
        }

        // ---- Solo ----
        if (part == Part.SOLO && chLead.on) {
            lick[(barInSection % 2) * 16 + s]?.let { n ->
                val ring = if (n.len >= 4) 2.5f else 0.9f
                lead.note(n.midi, n.vel, ring, bendFrom = n.bend, vibrato = n.vib, delay = human(6f))
            }
        }
    }

    private fun play(g: ElectricGuitar, h: Hit, delay: Int) {
        val shape = if (h.full) intArrayOf(h.root, h.root + 7, h.root + 12, h.root + 16) else intArrayOf(h.root, h.root + 7, h.root + 12)
        when (h.kind) {
            RING -> g.strike(shape, 0.95f, muted = false, ringSeconds = 2.5f, strumMs = 7f, delay = delay)
            SHORT -> {
                g.strike(shape, 0.85f, muted = false, ringSeconds = 2f, strumMs = 5f, delay = delay)
                g.choke(delay + stepsToSamples(1.3f))
            }
            MUTE -> g.strike(intArrayOf(h.root, h.root + 7), 0.9f, muted = true, ringSeconds = 0.13f, strumMs = 3f, delay = delay)
            NOTE -> g.note(h.root, 0.9f, 0.7f, delay = delay)
            CHOKE -> g.choke(delay)
        }
    }

    override fun updateLevels() {
        val part = section.part
        val drums = if (drumsIn) 1f else 0f
        chKick.target = drums
        chSnare.target = drums
        chHat.target = drums * above(0.08f)
        chBass.target = drums
        chGtrL.target = 1f
        chGtrR.target = if (part == Part.INTRO && !drumsIn) 0f else above(0.25f)
        chLead.target = if (part == Part.SOLO) above(0.35f) else 0f
    }
}
