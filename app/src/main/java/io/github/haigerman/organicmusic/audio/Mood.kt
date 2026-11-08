package io.github.haigerman.organicmusic.audio

import io.github.haigerman.organicmusic.audio.songs.AmbientSong
import io.github.haigerman.organicmusic.audio.songs.FlowSong
import io.github.haigerman.organicmusic.audio.songs.FocusSong
import io.github.haigerman.organicmusic.audio.songs.Song
import io.github.haigerman.organicmusic.audio.songs.SportSong

/** Stimmungen bzw. Zwecke – jede hat ihren eigenen Komponisten. */
enum class Mood(
    val id: String,
    val title: String,
    val subtitle: String,
    val emoji: String,
    val bpmMin: Int,
    val bpmMax: Int,
    val bpmDefault: Int,
    val intensityDefault: Float,
) {
    SPORT("sport", "Sport", "Treibende Beats für Training & Laufen", "🔥", 110, 175, 128, 0.75f),
    FOCUS("focus", "Fokus", "Ruhiger Lo-Fi-Groove zum Arbeiten & Lernen", "🎧", 65, 100, 82, 0.5f),
    FLOW("flow", "Kreativ", "Leichter, positiver House-Groove", "🎨", 96, 124, 112, 0.6f),
    RELAX("relax", "Entspannen", "Sanfte Klangflächen & Klavier", "🌿", 50, 80, 64, 0.5f),
    SLEEP("sleep", "Einschlafen", "Tiefe, langsame Klänge mit Meeresrauschen", "🌙", 40, 60, 50, 0.3f);

    fun create(sampleRate: Int, seed: Long, bpm: Float, intensity: Float): Song = when (this) {
        SPORT -> SportSong(sampleRate, seed, bpm, intensity)
        FOCUS -> FocusSong(sampleRate, seed, bpm, intensity)
        FLOW -> FlowSong(sampleRate, seed, bpm, intensity)
        RELAX -> AmbientSong(sampleRate, seed, bpm, intensity, sleep = false)
        SLEEP -> AmbientSong(sampleRate, seed, bpm, intensity, sleep = true)
    }

    companion object {
        fun byId(id: String?): Mood = entries.firstOrNull { it.id == id } ?: FOCUS
    }
}
