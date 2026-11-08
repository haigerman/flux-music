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
 * Sport · Desert Rock: instrumentaler, schwerer Wüsten-/Post-Rock. Tief gestimmte
 * Gitarren (bis hinunter zum tiefen H) mit dickem Fuzz, schwere Riffs im Halftime-Gefühl,
 * dazwischen cleane Arpeggien voller Hall und Echo, zweistimmige Gitarrenmelodien und
 * lange Spannungsbögen bis zu einem massiven Höhepunkt. Kein Gesang, keine Blues-Soli.
 * Riffs und Melodien werden nach Stilregeln neu erfunden – nichts ist nachgespielt.
 */
class DesertSong(sr: Int, seed: Long, bpm: Float, intensity: Float, private val bank: SampleBank) : Song(sr, seed, bpm, intensity) {

    override val reverb = FdnReverb(sr, decaySeconds = 2.6f, dampHz = 4800f, predelayMs = 24f, size = 1.2f, early = 0.35f)
    override val outputGain = 0.66f

    // ---------- Band ----------
    private val kit = bank["drums"]
    private val kick = Sampler(sr, rng, kit, 4)
    private val snare = Sampler(sr, rng, kit, 6)
    private val hats = Sampler(sr, rng, kit, 8).apply {
        chokeGroups[42] = 1; chokeGroups[43] = 1; chokeGroups[46] = 1; chokeGroups[44] = 1
    }
    private val toms = Sampler(sr, rng, kit, 6)
    private val cymbals = Sampler(sr, rng, kit, 8)

    private fun guitar(voices: Int = 8) = Sampler(sr, rng, bank["guitar"], voices).apply { release = 0.1f; retrigger = 0.04f }
    private fun mutes() = Sampler(sr, rng, bank["guitar_mute"], 6).apply { release = 0.05f; retrigger = 0.02f }
    private val heavyLs = guitar()
    private val heavyLm = mutes()
    private val heavyRs = guitar()
    private val heavyRm = mutes()
    private val cleanS = guitar(12).apply { release = 0.6f; retrigger = 0.2f }
    private val leadAs = guitar(3).apply { release = 0.3f }
    private val leadBs = guitar(3).apply { release = 0.3f }
    private val bass = Sampler(sr, rng, bank["ebass"], 4).apply { release = 0.15f; retrigger = 0.05f }

    // Fuzz: viel Verstärkung, dicke Bässe; clean: kaum Verzerrung
    private val heavyL = GuitarRig(sr, listOf(heavyLs, heavyLm), drive = 26f, pan = -0.85f, presenceDb = 1.5f, bassDb = 3.5f)
    private val heavyR = GuitarRig(sr, listOf(heavyRs, heavyRm), drive = 28f, pan = 0.85f, presenceDb = 2f, bassDb = 3f)
    private val clean = GuitarRig(sr, listOf(cleanS), drive = 1.6f, pan = 0.25f, presenceDb = 1f, bassDb = 0f)
    private val leadA = GuitarRig(sr, listOf(leadAs), drive = 18f, pan = -0.3f, presenceDb = 2.5f, bassDb = 0f)
    private val leadB = GuitarRig(sr, listOf(leadBs), drive = 18f, pan = 0.3f, presenceDb = 2.5f, bassDb = 0f)

    private val drumBus = bus(
        eq = Eq(sr).highpass(35f).lowShelf(80f, 2.5f).peak(450f, 0.9f, -3f).highShelf(8000f, 1f),
        comp = Compressor(sr, thresholdDb = -16f, ratio = 4f, attackMs = 12f, releaseMs = 120f, makeupDb = 4f, mix = 0.65f),
        saturation = 0.18f,
    )
    private val guitarBus = bus(
        eq = Eq(sr).highpass(70f).peak(220f, 0.9f, -1.5f).peak(3500f, 1.2f, -1.5f),
        comp = Compressor(sr, thresholdDb = -16f, ratio = 2.5f, attackMs = 20f, releaseMs = 150f, makeupDb = 2f),
    )
    private val chKick = channel(kick, 1.0f, reverb = 0.06f, fade = 0.02f, bus = drumBus)
    private val chSnare = channel(snare, 0.9f, reverb = 0.35f, fade = 0.02f, bus = drumBus)
    private val chHat = channel(hats, 0.4f, reverb = 0.08f, fade = 0.05f, bus = drumBus)
    private val chToms = channel(toms, 0.95f, reverb = 0.3f, fade = 0.02f, bus = drumBus)
    private val chCymbal = channel(cymbals, 0.55f, reverb = 0.25f, fade = 0.02f, bus = drumBus)
    private val chHeavyL = channel(heavyL, 0.55f, reverb = 0.12f, fade = 0.08f, bus = guitarBus)
    private val chHeavyR = channel(heavyR, 0.55f, reverb = 0.12f, fade = 0.3f, bus = guitarBus)
    private val chClean = channel(clean, 0.7f, reverb = 0.45f, delay = 0.4f, fade = 2f, eq = Eq(sr).highpass(120f).highShelf(6000f, 1.5f))
    private val chLeadA = channel(leadA, 0.42f, reverb = 0.35f, delay = 0.3f, fade = 1.5f, eq = Eq(sr).highpass(180f))
    private val chLeadB = channel(leadB, 0.32f, reverb = 0.35f, delay = 0.3f, fade = 1.5f, eq = Eq(sr).highpass(180f))
    private val chBass = channel(
        bass, 1.0f, fade = 0.1f,
        eq = Eq(sr).highpass(28f).lowShelf(70f, 2f).peak(700f, 1.2f, 2.5f),
        comp = Compressor(sr, thresholdDb = -18f, ratio = 4f, attackMs = 8f, releaseMs = 120f, makeupDb = 3f),
    )

    // ---------- Material ----------
    /** Riff-Ereignis: Schritt (Sechzehntel in 2 Takten), Abstand zum Grundton, Länge, Art. */
    private class Ev(val step: Int, val interval: Int, val len: Int, val kind: Int)

    private companion object {
        const val CHORD = 0  // Powerchord klingen lassen
        const val CHUG = 1   // abgedämpfter Grundton
        const val NOTE = 2   // Einzelton auf der tiefen Saite

        // Intervalle über dem tiefen Grundton, die zum Wüsten-/Doom-Vokabular gehören
        val riffIntervals = intArrayOf(0, 3, 5, 7, 8, 10, 12)
    }

    private var tonic = 47  // tiefer Grundton der Gitarre (H1 … D2 = 35 … 38)
    private var riffA = emptyList<Ev>()
    private var riffB = emptyList<Ev>()
    private var arp = IntArray(16)
    private var cleanChords = intArrayOf(0, 8, 3, 10)
    private var leadPhrase = arrayOfNulls<Pair<Int, Int>>(64) // (Stufe, Länge) – zweistimmig gespielt
    private var currentRoot = 35
    private var halfTime = true
    private var fill: List<Pair<Int, Int>>? = null
    private var crashNext = false

    init {
        delayBeats = 0.75f
        delay.feedback = 0.45f
        delay.setDamping(2600f)
        chCymbal.target = 1f
        chToms.target = 1f
        masterEq = Eq(sr).highpass(25f).lowShelf(90f, 1f).highShelf(10000f, 0.5f)
        masterComp = Compressor(sr, thresholdDb = -12f, ratio = 2.5f, attackMs = 20f, releaseMs = 180f, kneeDb = 6f, makeupDb = 2f)
        masterSaturation = 0.12f
    }

    /** Tiefer Grundton: H1, C2, Cis2 oder D2 – wie bei tief gestimmten Gitarren. */
    private fun low(interval: Int) = tonic + interval

    /** Ein schweres Riff über zwei Takte: Grundton-Pedal mit Akkorden darüber, viel Raum. */
    private fun makeRiff(heavy: Boolean): List<Ev> {
        val ev = ArrayList<Ev>()
        var s = 0
        val grid = if (heavy) 4 else 2 // im Halftime-Teil gröber
        while (s < 32) {
            val r = rng.float()
            when {
                s == 0 || s == 16 -> {
                    // Taktanfang: großer Akkord auf dem Grundton (oder der Quarte im 2. Takt)
                    val iv = if (s == 16 && rng.chance(0.5f)) rng.pick(intArrayOf(5, 8, 10, 3)) else 0
                    val len = rng.pick(intArrayOf(4, 6, 8, if (heavy) 12 else 6))
                    ev += Ev(s, iv, len, CHORD)
                    s += len
                }
                r < 0.35f -> {
                    // stampfende, abgedämpfte Grundtöne
                    val n = rng.int(2, 4)
                    for (k in 0 until n) if (s + k * 2 < 32) ev += Ev(s + k * 2, 0, 2, CHUG)
                    s += n * 2
                }
                r < 0.65f -> {
                    val iv = rng.pick(riffIntervals)
                    val len = rng.pick(intArrayOf(2, 4, 4, 6))
                    ev += Ev(s, iv, len, CHORD)
                    s += len
                }
                r < 0.85f -> {
                    // kurzer Lauf auf der tiefen Saite
                    val run = rng.pick(listOf(intArrayOf(0, 3, 5), intArrayOf(7, 5, 3), intArrayOf(0, 1, 0), intArrayOf(10, 7, 5)))
                    for ((k, iv) in run.withIndex()) if (s + k * 2 < 32) ev += Ev(s + k * 2, iv, 2, NOTE)
                    s += run.size * 2
                }
                else -> s += grid // Pause – Luft ist Teil des Stils
            }
        }
        return ev.filter { it.step < 32 }
    }

    /** Langsame, singende Melodie für die zweistimmigen Gitarren (Stufen der Moll-Tonleiter). */
    private fun makeLead(): Array<Pair<Int, Int>?> {
        val out = arrayOfNulls<Pair<Int, Int>>(64)
        var deg = rng.pick(intArrayOf(7, 9, 11))
        var s = 0
        while (s < 64) {
            val len = rng.pick(intArrayOf(4, 6, 8, 8, 12))
            out[s] = deg to len
            deg = (deg + rng.pick(intArrayOf(-2, -1, -1, 1, 1, 2, 3))).coerceIn(4, 13)
            s += len
            if (rng.chance(0.15f)) s += 2
        }
        // Phrasenende auf Grundton oder Quinte
        val lastStart = (0 until 64).last { out[it] != null }
        out[lastStart] = (if (rng.chance(0.5f)) 7 else 11) to (64 - lastStart)
        return out
    }

    override fun newPiece(first: Boolean) {
        tonic = if (first) rng.pick(intArrayOf(35, 36, 37, 38)) else rng.pick(intArrayOf(35, 36, 37, 38).filter { it != tonic }.toIntArray())
        key = Key(Math.floorMod(tonic, 12), if (rng.chance(0.6f)) Scale.MINOR else Scale.DORIAN)
        progression = intArrayOf(0)
        chordBars = 1
        swing = 0f
        riffA = makeRiff(heavy = false)
        riffB = makeRiff(heavy = true)
        cleanChords = rng.pick(listOf(intArrayOf(0, 8, 3, 10), intArrayOf(0, 5, 8, 7), intArrayOf(0, 10, 8, 10), intArrayOf(0, 3, 10, 5)))
        arp = IntArray(16) { s ->
            when (rng.int(4)) {
                0 -> s % 6
                1 -> intArrayOf(0, 2, 4, 1, 3, 5, 4, 2)[s % 8]
                2 -> if (s % 2 == 0) 0 else 1 + (s / 2) % 5
                else -> rng.int(6)
            }
        }
        leadPhrase = makeLead()
        halfTime = rng.chance(0.7f)
    }

    override fun makePlan(): List<Section> = listOf(
        Section(Part.INTRO, 8),     // cleane Arpeggien
        Section(Part.BUILD, 8),     // Schlagzeug auf Toms und Becken, Spannung
        Section(Part.RIFF, 16),     // schweres Riff A
        Section(Part.THEME, 16),    // zweistimmige Melodie über dem Riff
        Section(Part.BREAK, 8),     // Atempause: clean
        Section(Part.BUILD, 8),
        Section(Part.DROP, 16),     // massives Halftime-Riff B
        Section(Part.OUTRO, 4),     // ausklingender Akkord
    )

    override fun keyName(): String = key.name

    override fun chordName(): String = NOTE_NAMES[Math.floorMod(currentRoot, 12)] + "5"

    private val heavyPart: Boolean
        get() = section.part == Part.RIFF || section.part == Part.THEME || section.part == Part.DROP

    override fun onSection(part: Part) {
        if (part == Part.RIFF || part == Part.DROP || part == Part.THEME) crashNext = true
        if (part == Part.OUTRO) {
            // letzter, lang ausklingender Akkord
            heavyChord(0, 0.95f, 64f, 0)
            cymbals.noteOn(49, 0.9f, -1)
            kick.noteOn(36, 0.9f, -1)
            bass.noteOn(low(0) - 12, 0.85f, stepsToSamples(48f))
        }
    }

    override fun onBar() {
        val part = section.part
        if (crashNext && part != Part.OUTRO) {
            cymbals.noteOn(49, rng.range(0.8f, 0.95f), -1, human(3f))
            if (part == Part.DROP) cymbals.noteOn(57, rng.range(0.6f, 0.75f), -1, ms(12f))
            crashNext = false
        }
        val phraseEnd = barsLeftInSection == 1 || barInSection % 4 == 3
        fill = if (heavyPart && phraseEnd && rng.chance(0.4f + 0.5f * intensity + 0.3f * variation)) {
            rng.pick(listOf(
                listOf(8 to 1, 10 to 1, 12 to 2, 14 to 2),
                listOf(12 to 0, 13 to 0, 14 to 1, 15 to 2),
                listOf(8 to 0, 10 to 1, 12 to 1, 13 to 2, 14 to 2, 15 to 2),
            ))
        } else null
        if (fill != null && barsLeftInSection == 1) crashNext = true
        // Clean-Akkorde: einer pro Takt
        if (!heavyPart) currentRoot = low(cleanChords[barInSection % cleanChords.size])
    }

    override fun onStep(s: Int) {
        val part = section.part
        val fillNow = fill?.let { f -> s >= f.first().first } ?: false

        // ---------------- Schlagzeug ----------------
        when (part) {
            Part.INTRO, Part.BREAK -> {
                // nur Becken-Schwellungen in der zweiten Hälfte der Atempause
                if (part == Part.BREAK && barInSection >= 4 && s % 4 == 0 && chCymbal.on) {
                    cymbals.noteOn(51, 0.2f + 0.05f * barInSection, -1, human(4f))
                }
            }
            Part.BUILD -> {
                val p = sectionProgress(s)
                // tribal: Toms auf den Vierteln, Ride, gegen Ende immer dichter
                if (s % 4 == 0) toms.noteOn(if (s % 8 == 0) 41 else 48, 0.45f + 0.45f * p, -1, human(4f))
                if (p > 0.5f && s % 2 == 0) toms.noteOn(41, 0.3f + 0.4f * p, -1, human(4f))
                if (s % 4 == 2) cymbals.noteOn(51, 0.35f + 0.35f * p, -1, human(3f))
                if (s == 0) kick.noteOn(36, 0.6f + 0.35f * p, -1)
                if (barsLeftInSection == 1 && s >= 8) snare.noteOn(38, 0.4f + 0.04f * s, -1, human(2f))
            }
            Part.OUTRO -> {}
            else -> if (chKick.on) {
                val half = part == Part.DROP && halfTime
                if (!fillNow) {
                    // Halftime: Bassdrum auf 1 und "und" von 2, Snare auf 3
                    val kickHit = if (half) s == 0 || s == 6 || s == 10 else s == 0 || s == 8 || (s == 10 && rng.chance(0.4f))
                    if (kickHit) kick.noteOn(36, rng.range(0.85f, 0.97f), -1, human(3f))
                    val snareHit = if (half) s == 8 else s == 4 || s == 12
                    if (snareHit) snare.noteOn(if (part == Part.DROP) 40 else 38, rng.range(0.85f, 0.97f), -1, ms(6f) + human(4f))
                    if (part == Part.DROP || part == Part.THEME) {
                        // breite Ride/Crash-Teppiche statt enger Hi-Hats
                        if (s % 4 == 0) cymbals.noteOn(if (part == Part.DROP && s == 0) 57 else 51, rng.range(0.5f, 0.68f), -1, human(3f))
                    } else if (s % 2 == 0) {
                        hats.noteOn(if (s % 4 == 0) 43 else 42, (if (s % 4 == 0) 0.7f else 0.5f) * rng.range(0.9f, 1.08f), -1, human(3f))
                    }
                }
                fill?.forEach { (st, drum) ->
                    if (st == s) {
                        val v = 0.6f + 0.3f * (s / 15f)
                        when (drum) {
                            0 -> snare.noteOn(38, v, -1, human(3f))
                            1 -> toms.noteOn(48, v, -1, human(3f))
                            else -> toms.noteOn(41, v, -1, human(3f))
                        }
                    }
                }
            }
        }

        // ---------------- Gitarren ----------------
        if (heavyPart) {
            val riff = if (part == Part.DROP) riffB else riffA
            val pos = (barInSection % 2) * 16 + s
            for (e in riff) if (e.step == pos) {
                currentRoot = low(e.interval)
                when (e.kind) {
                    CHORD -> heavyChord(e.interval, rng.range(0.85f, 0.97f), e.len.toFloat() + 0.5f, human(4f))
                    CHUG -> chug(rng.range(0.8f, 0.92f), human(3f))
                    NOTE -> heavyNote(e.interval, rng.range(0.8f, 0.92f), e.len.toFloat())
                }
                // Bass folgt dem Riff eine Oktave tiefer, legt aber lange Töne darunter
                if (chBass.on) bass.noteOn(low(e.interval) - 12, rng.range(0.78f, 0.9f), stepsToSamples(e.len.toFloat()), human(3f))
            }
            // zweistimmige Melodie (Terzen/Sexten der Tonleiter) im Thema und im Höhepunkt
            if ((part == Part.THEME || part == Part.DROP) && chLeadA.on) {
                leadPhrase[(barInSection % 4) * 16 + s]?.let { (deg, len) ->
                    val a = key.midi(deg, tonic + 24)
                    val b = key.midi(deg + 2, tonic + 24)
                    val l = stepsToSamples(len - 0.2f)
                    val slide = if (rng.chance(0.3f)) 1f else 0f
                    leadAs.noteOn(a, 0.8f, l, human(6f), bendFrom = slide, bendMs = 90f, vibrato = 0.18f)
                    if (chLeadB.on) leadBs.noteOn(b, 0.7f, l, ms(9f) + human(6f), bendFrom = slide, bendMs = 90f, vibrato = 0.18f)
                }
            }
        } else if (part != Part.OUTRO && chClean.on) {
            // cleane Arpeggien über offenen, schwebenden Akkorden (Grundton, Quinte, None, Terz)
            val root = currentRoot + 12
            val minor3 = if (key.scale.minor) 3 else 4
            val pool = intArrayOf(root, root + 7, root + 14, root + 12 + minor3, root + 19, root + 24)
            val idx = arp[s]
            if (s % 2 == 0 || rng.chance(0.25f)) {
                cleanS.noteOn(pool[idx % pool.size], rng.range(0.45f, 0.65f) * (if (s % 4 == 0) 1f else 0.85f), stepsToSamples(6f), human(8f))
            }
            // in der Steigerung schwellen die schweren Gitarren mit langen Akkorden an
            if (part == Part.BUILD && s == 0 && barInSection >= section.bars / 2) {
                heavyChord(cleanChords[barInSection % cleanChords.size], 0.45f + 0.5f * sectionProgress(s), 16f, 0)
            }
            // Bass hält in der Steigerung den Grundton
            if (part == Part.BUILD && s == 0 && chBass.on) bass.noteOn(currentRoot - 12, 0.7f, stepsToSamples(15.5f))
        }
    }

    /** Powerchord (Grundton, Quinte, Oktave) auf beiden schweren Gitarren. */
    private fun heavyChord(interval: Int, vel: Float, lenSteps: Float, delay: Int) {
        val root = low(interval)
        val shape = intArrayOf(root, root + 7, root + 12)
        for ((g, m) in listOf(heavyLs to heavyLm, heavyRs to heavyRm)) {
            if (g === heavyRs && !chHeavyR.on) continue
            g.releaseAll(0.03f)
            m.releaseAll(0.02f)
            val d = delay + if (g === heavyRs) ms(rng.range(5f, 12f)) else 0
            for ((i, n) in shape.withIndex()) g.noteOn(n, vel * rng.range(0.92f, 1.04f), stepsToSamples(lenSteps), d + ms(8f * i))
        }
    }

    private fun chug(vel: Float, delay: Int) {
        val root = low(0)
        for ((g, m) in listOf(heavyLs to heavyLm, heavyRs to heavyRm)) {
            if (g === heavyRs && !chHeavyR.on) continue
            g.releaseAll(0.03f)
            m.releaseAll(0.02f)
            val d = delay + if (m === heavyRm) ms(rng.range(4f, 9f)) else 0
            m.noteOn(root, vel, stepsToSamples(1.6f), d)
            m.noteOn(root + 7, vel * 0.9f, stepsToSamples(1.6f), d + ms(3f))
        }
    }

    private fun heavyNote(interval: Int, vel: Float, lenSteps: Float) {
        for (g in listOf(heavyLs, heavyRs)) {
            if (g === heavyRs && !chHeavyR.on) continue
            g.releaseAll(0.03f)
            g.noteOn(low(interval), vel, stepsToSamples(lenSteps - 0.2f), if (g === heavyRs) ms(7f) else human(3f))
        }
    }

    init {
        for (c in listOf(chHeavyR, chLeadB)) c.optional = true
    }

    /** Variabilität: neue Riffs, andere Arpeggien und Melodien, Wechsel des Halftime-Gefühls. */
    override fun onPhrase() {
        if (section.part != Part.DROP && vary(0.4f)) riffA = makeRiff(heavy = false)
        if (section.part != Part.RIFF && vary(0.35f)) riffB = makeRiff(heavy = true)
        if (vary(0.4f)) arp = IntArray(16) { s -> if (rng.chance(0.5f)) s % 6 else rng.int(6) }
        if (vary(0.4f)) leadPhrase = makeLead()
        if (vary(0.2f)) halfTime = !halfTime
        if (vary(0.3f)) cleanChords = rng.pick(listOf(intArrayOf(0, 8, 3, 10), intArrayOf(0, 5, 8, 7), intArrayOf(0, 10, 8, 10), intArrayOf(0, 3, 10, 5)))
    }

    override fun updateLevels() {
        val part = section.part
        val drums = when (part) {
            Part.INTRO, Part.BREAK -> 0f
            else -> 1f
        }
        chKick.target = drums
        chSnare.target = drums
        chHat.target = drums
        chToms.target = 1f
        chCymbal.target = 1f
        chBass.target = when (part) {
            Part.INTRO -> 0f
            Part.BREAK -> 0.6f
            else -> 1f
        }
        chHeavyL.target = 1f
        chHeavyR.target = above(0.2f)
        chClean.target = when (part) {
            Part.INTRO, Part.BREAK, Part.BUILD -> 1f
            else -> 0f
        }
        chLeadA.target = if (part == Part.THEME || part == Part.DROP) above(0.35f) else 0f
        chLeadB.target = if (part == Part.THEME || part == Part.DROP) above(0.55f) else 0f
    }
}
