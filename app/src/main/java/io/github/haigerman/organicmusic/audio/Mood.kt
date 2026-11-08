package io.github.haigerman.organicmusic.audio

import io.github.haigerman.organicmusic.audio.songs.AmbientSong
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
    SPORT("sport", "Sport", "Treibende Beats oder Rock für Training & Laufen", "🔥"),
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
) {
    SPORT_GROOVE(Mood.SPORT, "groove", "Bass & Groove", 110, 175, 128, 0.75f),
    SPORT_ROCK(Mood.SPORT, "rock", "Rock", 100, 170, 128, 0.75f),
    FOCUS(Mood.FOCUS, "lofi", "Lo-Fi", 65, 100, 82, 0.5f),
    FLOW(Mood.FLOW, "house", "House", 96, 124, 112, 0.6f),
    RELAX(Mood.RELAX, "ambient", "Ambient", 50, 80, 64, 0.5f),
    SLEEP(Mood.SLEEP, "ambient", "Ambient", 40, 60, 50, 0.3f);

    /** Schlüssel für gespeicherte Einstellungen (der erste Stil nutzt den Namen der Stimmung). */
    val key: String get() = if (this == mood.styles.first()) mood.id else "${mood.id}_$id"

    fun create(sampleRate: Int, seed: Long, bpm: Float, intensity: Float): Song = when (this) {
        SPORT_GROOVE -> SportSong(sampleRate, seed, bpm, intensity)
        SPORT_ROCK -> RockSong(sampleRate, seed, bpm, intensity)
        FOCUS -> FocusSong(sampleRate, seed, bpm, intensity)
        FLOW -> FlowSong(sampleRate, seed, bpm, intensity)
        RELAX -> AmbientSong(sampleRate, seed, bpm, intensity, sleep = false)
        SLEEP -> AmbientSong(sampleRate, seed, bpm, intensity, sleep = true)
    }

    /** Erstellt ein Stück mit Standard-Tempo und -Intensität. */
    fun create(sampleRate: Int, seed: Long): Song = create(sampleRate, seed, bpmDefault.toFloat(), intensityDefault)
}
