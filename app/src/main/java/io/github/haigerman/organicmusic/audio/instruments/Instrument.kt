package io.github.haigerman.organicmusic.audio.instruments

/**
 * Ein Instrument erzeugt fortlaufend Samples. Noten werden mit kleiner Verzögerung
 * ([delay] in Samples) ausgelöst – so lassen sich Anschläge "menschlich" verschieben
 * oder Akkorde wie auf einer Gitarre leicht gebrochen anschlagen.
 */
abstract class Instrument(protected val sr: Int) {
    /** Addiert [n] Stereo-Samples auf [l]/[r] (ab Index 0). */
    abstract fun render(l: FloatArray, r: FloatArray, n: Int)

    /** Lässt alle Stimmen sofort verstummen. */
    abstract fun reset()
}

/** Schnelle tanh-Näherung für weiche Sättigung. */
fun softSat(x: Float): Float {
    val c = x.coerceIn(-3f, 3f)
    return c * (27f + c * c) / (27f + 9f * c * c)
}

/** Panorama -1 (links) … +1 (rechts) → Verstärkung links/rechts (konstante Leistung). */
fun panL(p: Float): Float = kotlin.math.sqrt(((1f - p) * 0.5f).coerceIn(0f, 1f))
fun panR(p: Float): Float = kotlin.math.sqrt(((1f + p) * 0.5f).coerceIn(0f, 1f))
