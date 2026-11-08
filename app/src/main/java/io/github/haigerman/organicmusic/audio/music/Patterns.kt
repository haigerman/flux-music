package io.github.haigerman.organicmusic.audio.music

import io.github.haigerman.organicmusic.audio.dsp.Rng
import kotlin.math.abs

/** Eine Note in einer Phrase: Beginn und Länge in Sechzehnteln. */
class Note(val step: Int, val length: Int, val midi: Int, val vel: Float)

/**
 * Erfindet Melodien nach einfachen Regeln, wie sie auch Komponisten nutzen:
 * auf betonten Zählzeiten Akkordtöne, dazwischen meist kleine Schritte, gelegentlich
 * ein Sprung. Die zweite Hälfte wiederholt Rhythmus und Kontur der ersten
 * (Frage → Antwort) und endet auf einem ruhigen Akkordton.
 */
object Melody {

    /** Rhythmus für [steps] Sechzehntel mit etwa [notes] Tönen. */
    fun rhythm(
        rng: Rng,
        steps: Int,
        notes: Int,
        syncopation: Float,
        maxLength: Int = 8,
        startOnOne: Boolean = true,
    ): List<Pair<Int, Int>> {
        val weights = FloatArray(steps) { s ->
            when {
                s % 8 == 0 -> 1f
                s % 4 == 0 -> 0.7f
                s % 2 == 0 -> 0.25f + 0.35f * syncopation
                else -> 0.03f + 0.25f * syncopation
            }
        }
        val chosen = sortedSetOf<Int>()
        if (startOnOne) chosen += 0 else chosen += rng.pick(intArrayOf(2, 4, 6))
        var guard = 0
        while (chosen.size < notes.coerceAtMost(steps) && guard++ < 500) {
            val s = rng.weighted(weights)
            chosen += s
            weights[s] = 0f
        }
        val list = chosen.toList()
        return list.mapIndexed { i, s ->
            val next = if (i + 1 < list.size) list[i + 1] else steps
            val room = next - s
            val len = if (i == list.size - 1) room.coerceAtMost(maxLength * 2) else room.coerceAtMost(maxLength)
            s to maxOf(1, if (room > 2 && rng.chance(0.25f)) len - 1 else len)
        }
    }

    /**
     * Phrase über 4 Takte (64 Sechzehntel); [chords] enthält den Akkord jedes Takts.
     * [prevLast] verbindet weich an die letzte Phrase an.
     */
    fun phrase(
        rng: Rng,
        key: Key,
        chords: List<Chord>,
        low: Int,
        high: Int,
        notesPerHalf: Int,
        syncopation: Float,
        pentatonic: Boolean,
        prevLast: Int? = null,
        maxLength: Int = 8,
        velocity: Float = 0.8f,
    ): List<Note> {
        val scale = key.notesBetween(low, high, pentatonic)
        if (scale.isEmpty()) return emptyList()
        val half = rhythm(rng, 32, notesPerHalf, syncopation, maxLength)
        val center = (low + high) / 2

        fun chordAtStep(step: Int) = chords[(step / 16).coerceIn(0, chords.size - 1)]
        fun nearestChordTone(target: Int, c: Chord): Int {
            var best = scale[0]
            var bestD = Int.MAX_VALUE
            for (n in low..high) if (c.contains(n)) {
                val d = abs(n - target)
                if (d < bestD) { bestD = d; best = n }
            }
            return best
        }
        fun idxOf(n: Int): Int {
            var bi = 0
            for (i in scale.indices) if (abs(scale[i] - n) < abs(scale[bi] - n)) bi = i
            return bi
        }

        // Erste Hälfte: frei erfinden
        val first = ArrayList<Note>()
        val moves = ArrayList<Int>()
        var cur = nearestChordTone(prevLast ?: (center + rng.int(-3, 3)), chordAtStep(0))
        var idx = idxOf(cur)
        for ((k, rl) in half.withIndex()) {
            val (s, len) = rl
            if (k > 0) {
                val r = rng.float()
                var mv = when {
                    r < 0.32f -> 1
                    r < 0.64f -> -1
                    r < 0.78f -> 2
                    r < 0.90f -> -2
                    r < 0.95f -> 0
                    else -> if (rng.chance(0.5f)) 3 else -3
                }
                // Richtung umkehren, wenn wir zu weit von der Mitte wegdriften
                if (scale[idx] > center + 5 && mv > 0) mv = -mv
                if (scale[idx] < center - 5 && mv < 0) mv = -mv
                idx = (idx + mv).coerceIn(0, scale.size - 1)
                if (s % 8 == 0) {
                    cur = nearestChordTone(scale[idx], chordAtStep(s))
                    idx = idxOf(cur)
                }
                moves += mv
            }
            cur = scale[idx]
            val accent = if (s % 4 == 0) 1f else 0.82f
            first += Note(s, len, cur, velocity * accent * rng.range(0.9f, 1.05f))
        }

        // Zweite Hälfte: gleicher Rhythmus, gleiche Kontur – an die Akkorde angepasst
        val second = ArrayList<Note>()
        idx = idxOf(nearestChordTone(first[0].midi, chordAtStep(32)))
        for ((k, rl) in half.withIndex()) {
            val (s0, len) = rl
            val s = s0 + 32
            if (k > 0) idx = (idx + moves[k - 1]).coerceIn(0, scale.size - 1)
            var note = scale[idx]
            val last = k == half.size - 1
            if (last) {
                // ruhiger Abschluss: Akkordton, bevorzugt der Grundton
                val c = chordAtStep(s)
                val root = c.rootNear(note)
                note = if (root in low..high && rng.chance(0.6f)) root else nearestChordTone(note, c)
            } else if (s % 8 == 0) {
                note = nearestChordTone(note, chordAtStep(s))
            }
            idx = idxOf(note)
            val accent = if (s % 4 == 0) 1f else 0.82f
            second += Note(s, len, note, velocity * accent * rng.range(0.9f, 1.05f))
        }
        return first + second
    }

    /**
     * Arpeggio-Muster über 16 Schritte: Indizes in die (über zwei Oktaven erweiterte)
     * Akkordlage, -1 = Pause.
     */
    fun arpPattern(rng: Rng, chordSize: Int, density: Float): IntArray {
        val span = chordSize * 2
        val type = rng.int(4)
        val pat = IntArray(16)
        for (s in 0 until 16) {
            pat[s] = when (type) {
                0 -> s % span // aufwärts
                1 -> { // auf und ab
                    val cyc = span * 2 - 2
                    val p = s % cyc
                    if (p < span) p else cyc - p
                }
                2 -> if (s % 2 == 0) 0 else 1 + (s / 2) % (span - 1) // Pendel mit Grundton
                else -> rng.int(span) // feste Zufallsfigur – wiederholt sich wie ein Riff
            }
        }
        // Lücken lassen die Figur atmen
        for (s in 0 until 16) {
            val strong = s % 4 == 0
            if (!strong && rng.chance((1f - density) * 0.7f)) pat[s] = -1
        }
        return pat
    }

    /** Bildet einen Arp-Index auf eine MIDI-Note ab. */
    fun arpNote(voicing: IntArray, index: Int): Int {
        val n = voicing.size
        return voicing[index % n] + 12 * (index / n)
    }
}
