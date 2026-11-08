package io.github.haigerman.organicmusic.audio

import io.github.haigerman.organicmusic.audio.samples.SampleBank
import io.github.haigerman.organicmusic.audio.samples.SampleLoader
import io.github.haigerman.organicmusic.audio.songs.AmbientSong
import io.github.haigerman.organicmusic.audio.songs.DesertSong
import io.github.haigerman.organicmusic.audio.songs.FlowSong
import io.github.haigerman.organicmusic.audio.songs.FocusSong
import io.github.haigerman.organicmusic.audio.songs.RockSong
import io.github.haigerman.organicmusic.audio.songs.Song
import io.github.haigerman.organicmusic.audio.songs.SportSong

/** Stimmungen bzw. Zwecke. Jede hat einen oder mehrere [Style]s. */
enum class Mood(
    val id: String,
    val title: String,
    val subtitle: String,
    val emoji: String,
) {
    SPORT("sport", "Sport", "Treibende Beats, Rock oder Desert Rock fürs Training", "🔥"),
    FOCUS("focus", "Fokus", "Ruhiger Lo-Fi-Groove zum Arbeiten & Lernen", "🎧"),
    FLOW("flow", "Kreativ", "Leichter, positiver House-Groove", "🎨"),
    RELAX("relax", "Entspannen", "Sanfte Klangflächen & Klavier", "🌿"),
    SLEEP("sleep", "Einschlafen", "Tiefe, langsame Klänge mit Meeresrauschen", "🌙");

    val styles: List<Style> get() = Style.entries.filter { it.mood == this }

    companion object {
        fun byId(id: String?): Mood = entries.firstOrNull { it.id == id } ?: FOCUS
    }
}

/** Spielart innerhalb einer Stimmung – jede mit eigenem Komponisten und Tempobereich. */
enum class Style(
    val mood: Mood,
    val id: String,
    val title: String,
    val bpmMin: Int,
    val bpmMax: Int,
    val bpmDefault: Int,
    val intensityDefault: Float,
    /** Benötigte Sample-Instrumente (assets/instruments/<id>). */
    val instruments: List<String>,
) {
    SPORT_GROOVE(Mood.SPORT, "groove", "Bass & Groove", 110, 175, 128, 0.75f, listOf("drums", "claps")),
    SPORT_ROCK(Mood.SPORT, "rock", "Rock", 100, 170, 128, 0.75f, listOf("drums", "ebass", "guitar", "guitar_mute")),
    SPORT_DESERT(Mood.SPORT, "desert", "Desert Rock", 70, 140, 100, 0.75f, listOf("drums", "ebass", "guitar", "guitar_mute")),
    FOCUS(Mood.FOCUS, "lofi", "Lo-Fi", 65, 100, 82, 0.5f, listOf("drums_lofi", "wurli", "upright", "glock", "flute", "violas")),
    FLOW(
        Mood.FLOW, "house", "House", 96, 124, 112, 0.6f,
        listOf("drums", "perc", "claps", "piano", "ebass", "marimba", "flute", "violins", "violas"),
    ),
    RELAX(Mood.RELAX, "ambient", "Ambient", 50, 80, 64, 0.5f, listOf("piano", "violins", "violas", "cellos", "contrabass", "harp", "glock")),
    SLEEP(Mood.SLEEP, "ambient", "Ambient", 40, 60, 50, 0.3f, listOf("piano", "violas", "cellos", "contrabass", "harp"));

    /** Standard für den Regler "Variabilität" (0 = gleichbleibend wie bisher). */
    val variationDefault: Float get() = 0.5f

    /** Schlüssel für gespeicherte Einstellungen (der erste Stil nutzt den Namen der Stimmung). */
    val key: String get() = if (this == mood.styles.first()) mood.id else "${mood.id}_$id"

    fun create(sampleRate: Int, seed: Long, bpm: Float, intensity: Float, bank: SampleBank, variation: Float = 0f): Song = when (this) {
        SPORT_GROOVE -> SportSong(sampleRate, seed, bpm, intensity, bank)
        SPORT_ROCK -> RockSong(sampleRate, seed, bpm, intensity, bank)
        SPORT_DESERT -> DesertSong(sampleRate, seed, bpm, intensity, bank)
        FOCUS -> FocusSong(sampleRate, seed, bpm, intensity, bank)
        FLOW -> FlowSong(sampleRate, seed, bpm, intensity, bank)
        RELAX -> AmbientSong(sampleRate, seed, bpm, intensity, bank, sleep = false)
        SLEEP -> AmbientSong(sampleRate, seed, bpm, intensity, bank, sleep = true)
    }.also { it.targetVariation = variation }

    /** Lädt die Instrumente (falls nötig) und erstellt das Stück. */
    fun create(sampleRate: Int, seed: Long, bpm: Float, intensity: Float, loader: SampleLoader, variation: Float = 0f): Song =
        create(sampleRate, seed, bpm, intensity, SampleBank.load(loader, instruments), variation)

    /** Erstellt ein Stück mit Standard-Tempo und -Intensität. */
    fun create(sampleRate: Int, seed: Long, loader: SampleLoader): Song =
        create(sampleRate, seed, bpmDefault.toFloat(), intensityDefault, loader)
}
