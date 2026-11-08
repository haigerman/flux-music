package io.github.haigerman.organicmusic.audio.music

import kotlin.math.abs

/** Tonleitern als Halbtonabstände vom Grundton. */
enum class Scale(val label: String, val steps: IntArray, val minor: Boolean) {
    MAJOR("Dur", intArrayOf(0, 2, 4, 5, 7, 9, 11), false),
    MINOR("Moll", intArrayOf(0, 2, 3, 5, 7, 8, 10), true),
    DORIAN("Dorisch", intArrayOf(0, 2, 3, 5, 7, 9, 10), true),
    MIXOLYDIAN("Mixolydisch", intArrayOf(0, 2, 4, 5, 7, 9, 10), false),
    LYDIAN("Lydisch", intArrayOf(0, 2, 4, 6, 7, 9, 11), false),
}

/** Deutsche Notennamen (mit H und B). */
val NOTE_NAMES = arrayOf("C", "Des", "D", "Es", "E", "F", "Fis", "G", "As", "A", "B", "H")

/** Gebräuchliche Namen der Molltonarten (cis-, fis-, gis-Moll statt des-, ges-, as-Moll). */
private val MINOR_NAMES = arrayOf("c", "cis", "d", "es", "e", "f", "fis", "g", "gis", "a", "b", "h")

/**
 * Tonart: Grundton (Tonklasse 0–11, C = 0) und Tonleiter. Stufen ("degrees") sind
 * nullbasiert und dürfen über die Oktave hinausgehen (7 = Grundton eine Oktave höher).
 */
class Key(val tonic: Int, val scale: Scale) {

    /** Halbtöne über C für die Stufe (kann > 11 oder < 0 sein). */
    fun pitch(degree: Int): Int {
        val oct = Math.floorDiv(degree, 7)
        val idx = Math.floorMod(degree, 7)
        return tonic + 12 * oct + scale.steps[idx]
    }

    /** MIDI-Note der Stufe in der Oktave, deren Grundton bei [tonicMidi] liegt. */
    fun midi(degree: Int, tonicMidi: Int): Int = tonicMidi + pitch(degree) - tonic

    /** MIDI-Note des Grundtons, der [near] am nächsten liegt. */
    fun tonicNear(near: Int): Int {
        val base = near - Math.floorMod(near - tonic, 12)
        return if (near - base > 6) base + 12 else base
    }

    /** Akkord auf der Stufe, aus [size] übereinandergeschichteten Terzen (3 = Dreiklang, 4 = Septakkord …). */
    fun chord(degree: Int, size: Int): Chord {
        val root = pitch(degree)
        val tones = IntArray(size) { pitch(degree + 2 * it) - root }
        return Chord(degree, Math.floorMod(root, 12), tones)
    }

    /** Alle MIDI-Noten der Tonleiter zwischen [low] und [high]. */
    fun notesBetween(low: Int, high: Int, pentatonic: Boolean = false): IntArray {
        val allowed = if (!pentatonic) scale.steps.toList() else pentatonicSteps()
        return (low..high).filter { Math.floorMod(it - tonic, 12) in allowed }.toIntArray()
    }

    /** Fünftonleiter, passend zur Tonart (ohne Halbtonschritte – klingt nie "falsch"). */
    private fun pentatonicSteps(): List<Int> =
        if (scale.minor) listOf(0, 3, 5, 7, 10) else listOf(0, 2, 4, 7, 9)

    val name: String
        get() {
            val n = NOTE_NAMES[Math.floorMod(tonic, 12)]
            return when (scale) {
                Scale.MAJOR -> "$n-Dur"
                Scale.MINOR -> "${MINOR_NAMES[Math.floorMod(tonic, 12)]}-Moll"
                else -> "$n-${scale.label}"
            }
        }

    /** Verwandte Tonart für den nächsten Teil: Quinte, Quarte, Paralleltonart oder gleich. */
    fun related(choice: Int): Key = when (choice) {
        0 -> Key(Math.floorMod(tonic + 7, 12), scale)
        1 -> Key(Math.floorMod(tonic + 5, 12), scale)
        2 -> Key(Math.floorMod(tonic + 2, 12), scale)
        3 -> Key(Math.floorMod(tonic - 2, 12), scale)
        else -> this
    }
}

/** Akkord: Stufe, Grundton-Tonklasse und Intervalle (Halbtöne über dem Grundton). */
class Chord(val degree: Int, val rootPc: Int, val tones: IntArray) {

    val name: String
        get() {
            val third = tones.getOrNull(1)?.let { Math.floorMod(it, 12) } ?: 4
            val fifth = tones.getOrNull(2)?.let { Math.floorMod(it, 12) } ?: 7
            val seventh = tones.getOrNull(3)?.let { Math.floorMod(it, 12) }
            val minor = third == 3
            val quality = when {
                seventh == null -> when {
                    minor && fifth == 6 -> "dim"
                    !minor && fifth == 8 -> "+"
                    minor -> "m"
                    else -> ""
                }
                minor && fifth == 6 -> if (seventh == 9) "dim7" else "m7♭5"
                minor -> if (seventh == 11) "m(maj7)" else "m7"
                else -> if (seventh == 11) "maj7" else "7"
            }
            val nine = if (tones.size >= 5) when (quality) {
                "maj7" -> "maj9"
                "7" -> "9"
                "m7" -> "m9"
                else -> quality
            } else quality
            return NOTE_NAMES[rootPc] + nine
        }

    /** MIDI-Note des Grundtons nahe [near] (höchstens 6 Halbtöne entfernt). */
    fun rootNear(near: Int): Int {
        val base = near - Math.floorMod(near - rootPc, 12)
        return if (near - base > 6) base + 12 else base
    }

    /** Tiefste MIDI-Note des Grundtons, die ≥ [low] ist. */
    fun rootAtOrAbove(low: Int): Int = low + Math.floorMod(rootPc - low, 12)

    /**
     * Wählt eine Lage (Umkehrung) zwischen [low] und [high], die möglichst wenig von
     * [prev] abweicht – so wandern die Stimmen weich von Akkord zu Akkord.
     */
    fun voicing(low: Int, high: Int, prev: IntArray?, skipRootIfLarge: Boolean = false): IntArray {
        var t = tones
        if (skipRootIfLarge && t.size >= 5) t = t.copyOfRange(1, t.size) // Grundton macht der Bass
        val n = t.size
        var best: IntArray? = null
        var bestScore = Int.MAX_VALUE
        val center = (low + high) / 2
        for (inv in 0 until n) {
            for (shift in 0..1) {
                val cand = IntArray(n)
                var prevNote = Int.MIN_VALUE
                for (i in 0 until n) {
                    val pc = Math.floorMod(rootPc + t[(inv + i) % n], 12)
                    val note = if (i == 0) low + Math.floorMod(pc - low, 12) + 12 * shift
                    else prevNote + 1 + Math.floorMod(pc - (prevNote + 1), 12)
                    cand[i] = note
                    prevNote = note
                }
                if (cand.last() > high + 2) continue
                val score = if (prev != null && prev.size == n) {
                    var s = 0
                    for (i in 0 until n) s += abs(cand[i] - prev[i])
                    s
                } else if (prev != null && prev.isNotEmpty()) {
                    abs(cand.average().toInt() - prev.average().toInt()) * n
                } else {
                    abs(cand.average().toInt() - center) * n
                }
                if (score < bestScore) {
                    bestScore = score
                    best = cand
                }
            }
        }
        return best ?: IntArray(n) { low + Math.floorMod(rootPc + t[it] - low, 12) }
    }

    /** Akkordtöne als Tonklassen. */
    fun pcs(): IntArray = IntArray(tones.size) { Math.floorMod(rootPc + tones[it], 12) }

    fun contains(midi: Int): Boolean = Math.floorMod(midi, 12) in pcs()
}
