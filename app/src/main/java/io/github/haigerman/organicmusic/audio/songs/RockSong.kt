package io.github.haigerman.organicmusic.audio.songs

import io.github.haigerman.organicmusic.audio.dsp.Compressor
import io.github.haigerman.organicmusic.audio.dsp.Eq
import io.github.haigerman.organicmusic.audio.dsp.FdnReverb
import io.github.haigerman.organicmusic.audio.instruments.GuitarRig
import io.github.haigerman.organicmusic.audio.instruments.Sampler
import io.github.haigerman.organicmusic.audio.music.Key
import io.github.haigerman.organicmusic.audio.music.NOTE_NAMES
import io.github.haigerman.organicmusic.audio.music.Scale
import io.github.haigerman.organicmusic.audio.samples.SampleBank

/**
 * Sport · Rock: geradliniger Hardrock im Stil der großen Pub-Rock-Bands der 70er/80er –
 * mit echten Instrumenten. Ein Studio-Schlagzeug (Nah-, Overhead- und Raummikrofone), ein
 * gezupfter E-Bass in stur durchlaufenden Achteln und zwei echte Gitarren links und rechts,
 * jede über einen eigenen, crunchig eingestellten Röhrenverstärker. Powerchords mit viel Luft
 * zwischen den Akkorden, im Solo Blues-Licks mit Bendings.
 * Alle Riffs und Licks werden nach Stilregeln neu erfunden – nichts ist nachgespielt.
 */
class RockSong(sr: Int, seed: Long, bpm: Float, intensity: Float, private val bank: SampleBank) : Song(sr, seed, bpm, intensity) {

    override val reverb = FdnReverb(sr, decaySeconds = 1.3f, dampHz = 5000f, predelayMs = 8f, size = 0.75f, early = 0.5f)
    override val outputGain = 0.66f

    // ---------- Band ----------
    private val kit = bank["drums"]
    private val kick = Sampler(sr, rng, kit, 4)
    private val snare = Sampler(sr, rng, kit, 6)
    private val hats = Sampler(sr, rng, kit, 8).apply {
        chokeGroups[42] = 1; chokeGroups[43] = 1; chokeGroups[46] = 1; chokeGroups[44] = 1
    }
    private val toms = Sampler(sr, rng, kit, 6)
    private val cymbals = Sampler(sr, rng, kit, 6)
    private val tomKeys = intArrayOf(48, 48, 41)

    private fun guitarSampler() = Sampler(sr, rng, bank["guitar"], 8).apply { release = 0.08f; retrigger = 0.03f }
    private fun muteSampler() = Sampler(sr, rng, bank["guitar_mute"], 6).apply { release = 0.05f; retrigger = 0.02f }
    private val gtrLs = guitarSampler()
    private val gtrLm = muteSampler()
    private val gtrRs = guitarSampler()
    private val gtrRm = muteSampler()
    private val leadS = Sampler(sr, rng, bank["guitar"], 2).apply { release = 0.2f; retrigger = 0.06f }
    private val gtrL = GuitarRig(sr, listOf(gtrLs, gtrLm), drive = 9f, pan = -0.8f, presenceDb = 2.5f, bassDb = 1.5f)
    private val gtrR = GuitarRig(sr, listOf(gtrRs, gtrRm), drive = 10f, pan = 0.8f, presenceDb = 3f, bassDb = 1f)
    private val lead = GuitarRig(sr, listOf(leadS), drive = 22f, pan = 0.15f, presenceDb = 3.5f, bassDb = 0f)
    private val bass = Sampler(sr, rng, bank["ebass"], 4).apply { release = 0.1f; retrigger = 0.03f }

    private val drumBus = bus(
        eq = Eq(sr).highpass(35f).lowShelf(80f, 2f).peak(400f, 0.9f, -2.5f).highShelf(8000f, 1.5f),
        comp = Compressor(sr, thresholdDb = -14f, ratio = 4f, attackMs = 10f, releaseMs = 90f, makeupDb = 4f, mix = 0.7f),
        saturation = 0.15f,
    )
    private val guitarBus = bus(
        eq = Eq(sr).highpass(90f).peak(250f, 1f, -1.5f),
        comp = Compressor(sr, thresholdDb = -16f, ratio = 2.5f, attackMs = 15f, releaseMs = 120f, makeupDb = 2f),
    )
    private val chKick = channel(kick, 1.0f, reverb = 0.04f, fade = 0.02f, bus = drumBus)
    private val chSnare = channel(snare, 0.9f, reverb = 0.25f, fade = 0.02f, bus = drumBus)
    private val chHat = channel(hats, 0.45f, reverb = 0.05f, fade = 0.05f, bus = drumBus)
    private val chToms = channel(toms, 0.85f, reverb = 0.2f, fade = 0.02f, bus = drumBus)
    private val chCymbal = channel(cymbals, 0.5f, reverb = 0.15f, fade = 0.02f, bus = drumBus)
    private val chGtrL = channel(gtrL, 0.55f, reverb = 0.1f, fade = 0.05f, bus = guitarBus)
    private val chGtrR = channel(gtrR, 0.55f, reverb = 0.1f, fade = 0.3f, bus = guitarBus)
    private val chLead = channel(lead, 0.5f, reverb = 0.3f, delay = 0.25f, fade = 0.3f, eq = Eq(sr).highpass(150f).peak(1500f, 1f, 1.5f))
    private val chBass = channel(
        bass, 0.95f, fade = 0.05f,
        eq = Eq(sr).highpass(32f).lowShelf(80f, 1.5f).peak(800f, 1.2f, 3f).highShelf(3000f, 1f),
        comp = Compressor(sr, thresholdDb = -18f, ratio = 4f, attackMs = 5f, releaseMs = 90f, makeupDb = 3f),
    )

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
    private var verseH = intArrayOf(0, 0, 5, 0)
    private var chorusH = intArrayOf(0, 10, 5, 0)
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
        chCymbal.target = 1f
        chToms.target = 1f
        masterEq = Eq(sr).highpass(28f).highShelf(10000f, 1f)
        masterComp = Compressor(sr, thresholdDb = -12f, ratio = 2.5f, attackMs = 15f, releaseMs = 150f, kneeDb = 6f, makeupDb = 2f)
        masterSaturation = 0.1f
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
        verseH = rng.pick(verseHarmony)
        verseRiff = makeRiff(verseH, 2, chorus = false)
        chorusH = rng.pick(chorusHarmony)
        chorusRiff = makeRiff(chorusH, 4, chorus = true)
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
            cymbals.noteOn(49, if (part == Part.CHORUS) rng.range(0.75f, 0.9f) else rng.range(0.55f, 0.7f), -1)
            crashNext = false
        }
        // Fill im letzten Takt einer Phrase (oder vor dem nächsten Teil)
        val phraseEnd = barsLeftInSection == 1 || barInSection % 8 == 7
        fill = if (drumsIn && phraseEnd && rng.chance(0.35f + 0.6f * intensity + 0.3f * variation)) rng.pick(fills) else null
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
                if (hitKick) kick.noteOn(36, if (s == 0) rng.range(0.85f, 0.95f) else rng.range(0.75f, 0.88f), -1, human(3f))
            }
            if (chSnare.on && !fillNow) {
                if (s == 4 || s == 12) {
                    // im Refrain kräftiger Rimshot, sonst Mitte – minimal hinter dem Beat
                    val k = if (part == Part.CHORUS || part == Part.SOLO) 40 else 38
                    snare.noteOn(k, rng.range(0.82f, 0.95f), -1, ms(5f) + human(4f))
                }
                if (part == Part.BUILD && barsLeftInSection <= 2) {
                    val every = if (barsLeftInSection == 1) 1 else 2
                    if (s % every == 0 && s != 4 && s != 12) snare.noteOn(38, 0.3f + 0.04f * s, -1, human(2f))
                }
            }
            if (chHat.on && !fillNow && s % 2 == 0) {
                val loose = part == Part.CHORUS || part == Part.SOLO
                val k = if (loose && intensity > 0.35f) 43 else 42
                if (part == Part.CHORUS && s % 4 == 0 && intensity > 0.55f && rng.chance(0.25f)) {
                    cymbals.noteOn(51, rng.range(0.55f, 0.7f), -1, human(3f))
                }
                hats.noteOn(k, (if (s % 4 == 0) 0.75f else 0.55f) * rng.range(0.9f, 1.08f), -1, human(3f))
            }
            fill?.forEach { (st, drum) ->
                if (st == s) {
                    val v = 0.6f + 0.3f * (s / 15f)
                    if (drum == 0) snare.noteOn(38, v, -1, human(3f)) else toms.noteOn(tomKeys[drum - 1], v, -1, human(3f))
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
                chug(gtrLm, root, v, human(3f))
                if (chGtrR.on) chug(gtrRm, root, v, ms(rng.range(3f, 9f)))
                gtrSilent = false
            }
        } else {
            val pos = (barInSection % riff.bars) * 16 + s
            riff.hits[pos]?.let { h ->
                // auf der Zählzeit Abschlag, dazwischen Aufschlag – wie die rechte Hand eines Gitarristen
                val up = (pos / 2) % 2 == 1
                play(gtrLs, gtrLm, h, human(4f), up)
                if (chGtrR.on) play(gtrRs, gtrRm, h, ms(rng.range(4f, 11f)), up)
                gtrSilent = h.kind == CHOKE
            }
        }

        // ---- Bass: stur durchlaufende Achtel ----
        if (chBass.on && drumsIn && s % 2 == 0 && !gtrSilent) {
            val root = (riff?.roots?.get((barInSection % riff.bars) * 16 + s) ?: lowRoot(tonic)) - 12
            bass.noteOn(root, if (s % 4 == 0) rng.range(0.8f, 0.9f) else rng.range(0.68f, 0.8f), stepsToSamples(1.75f), human(3f))
        }

        // ---- Solo ----
        if (part == Part.SOLO && chLead.on) {
            lick[(barInSection % 2) * 16 + s]?.let { n ->
                val len = stepsToSamples(n.len.toFloat() - 0.1f)
                leadS.noteOn(n.midi, n.vel, len, human(6f), bendFrom = n.bend, bendMs = 110f, vibrato = n.vib * 40f)
            }
        }
    }

    /** Abgedämpfter Anschlag (Palm-Mute) auf Grundton + Quinte. */
    private fun chug(m: Sampler, root: Int, vel: Float, delay: Int) {
        m.releaseAll(0.02f)
        m.noteOn(root, vel, stepsToSamples(1.6f), delay)
        m.noteOn(root + 7, vel * 0.9f, stepsToSamples(1.6f), delay + ms(3f))
    }

    private fun play(g: Sampler, m: Sampler, h: Hit, delay: Int, up: Boolean = false) {
        val shape = if (h.full) intArrayOf(h.root, h.root + 7, h.root + 12, h.root + 16) else intArrayOf(h.root, h.root + 7, h.root + 12)
        fun strum(vel: Float, lenSteps: Float, strumMs: Float) {
            val k = if (up) 0.85f else 1f
            for ((i, n) in shape.withIndex()) {
                val order = if (up) shape.size - 1 - i else i
                // beim Aufschlag klingen die tiefen Saiten schwächer mit
                val v = vel * k * (if (up && i == 0) 0.8f else 1f) * rng.range(0.92f, 1.05f)
                g.noteOn(n, v, stepsToSamples(lenSteps), delay + ms(strumMs * order))
            }
        }
        // neuer Anschlag: die vorher klingenden Saiten verstummen (wie beim echten Spielen)
        if (h.kind != CHOKE) {
            g.releaseAll(0.03f)
            m.releaseAll(0.02f)
        }
        when (h.kind) {
            RING -> strum(rng.range(0.85f, 0.95f), h.len.toFloat() + 6f, 7f)
            SHORT -> strum(rng.range(0.78f, 0.88f), 1.3f, 5f)
            MUTE -> chug(m, h.root, rng.range(0.8f, 0.92f), delay)
            NOTE -> g.noteOn(h.root, 0.85f, stepsToSamples(1.8f), delay)
            CHOKE -> {
                g.releaseAll(0.035f)
                m.releaseAll(0.03f)
            }
        }
    }

    init {
        for (c in listOf(chGtrR, chLead)) c.optional = true
    }

    /** Variabilität: Riffs werden variiert oder neu erfunden, der Beat wechselt. */
    override fun onPhrase() {
        if (vary(0.4f)) verseRiff = makeRiff(if (vary(0.5f)) rng.pick(verseHarmony).also { verseH = it } else verseH, 2, chorus = false)
        if (vary(0.3f)) chorusRiff = makeRiff(chorusH, 4, chorus = true)
        if (vary(0.35f)) kickPattern = rng.pick(kickPatterns)
    }

    override fun updateLevels() {
        val part = section.part
        val drums = if (drumsIn) 1f else 0f
        chKick.target = drums
        chSnare.target = drums
        chHat.target = drums * above(0.08f)
        chToms.target = drums
        chCymbal.target = 1f
        chBass.target = drums
        chGtrL.target = 1f
        chGtrR.target = if (part == Part.INTRO && !drumsIn) 0f else above(0.25f)
        chLead.target = if (part == Part.SOLO) above(0.35f) else 0f
    }
}
