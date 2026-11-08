package io.github.haigerman.organicmusic.data

import android.content.Context
import android.content.SharedPreferences
import io.github.haigerman.organicmusic.audio.Mood
import io.github.haigerman.organicmusic.audio.songs.SongInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Tempo und Intensität – je Stimmung gespeichert. */
data class MoodSettings(val bpm: Int, val intensity: Float)

data class MusicState(
    val mood: Mood = Mood.FOCUS,
    val settings: Map<Mood, MoodSettings> = emptyMap(),
    val playing: Boolean = false,
    /** 0 = kein Timer */
    val timerMinutes: Int = 0,
    /** System.currentTimeMillis(), wann der Timer abläuft; 0 = keiner aktiv */
    val timerEndsAt: Long = 0L,
    /** Wird bei "Neues Stück" hochgezählt. */
    val pieceRequest: Int = 0,
) {
    fun settings(m: Mood): MoodSettings = settings[m] ?: MoodSettings(m.bpmDefault, m.intensityDefault)
    val current: MoodSettings get() = settings(mood)
}

/** Was gerade erklingt – vom Dienst veröffentlicht, für Anzeige und Puls. */
data class NowPlaying(
    val info: SongInfo,
    /** Position in Vierteln zum Zeitpunkt [atNanos] (System.nanoTime). */
    val beat: Double,
    val atNanos: Long,
    val bpm: Float,
)

/** Zentrale, app-weite Quelle für den Zustand – geteilt von UI und Dienst. */
object MusicRepository {
    private val _state = MutableStateFlow(MusicState())
    val state: StateFlow<MusicState> = _state.asStateFlow()

    private val _nowPlaying = MutableStateFlow<NowPlaying?>(null)
    val nowPlaying: StateFlow<NowPlaying?> = _nowPlaying.asStateFlow()

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences("music", Context.MODE_PRIVATE)
        prefs = p
        val settings = Mood.entries.associateWith { m ->
            MoodSettings(
                bpm = p.getInt("bpm_${m.id}", m.bpmDefault).coerceIn(m.bpmMin, m.bpmMax),
                intensity = p.getFloat("int_${m.id}", m.intensityDefault).coerceIn(0f, 1f),
            )
        }
        _state.value = MusicState(
            mood = Mood.byId(p.getString("mood", null)),
            settings = settings,
            timerMinutes = p.getInt("timer", 0),
        )
    }

    fun selectMood(m: Mood) {
        _state.update { it.copy(mood = m) }
        save()
    }

    fun setBpm(bpm: Int) {
        _state.update { s ->
            val m = s.mood
            s.copy(settings = s.settings + (m to s.current.copy(bpm = bpm.coerceIn(m.bpmMin, m.bpmMax))))
        }
        save()
    }

    fun setIntensity(v: Float) {
        _state.update { s -> s.copy(settings = s.settings + (s.mood to s.current.copy(intensity = v.coerceIn(0f, 1f)))) }
        save()
    }

    fun resetTempo() {
        setBpm(_state.value.mood.bpmDefault)
    }

    fun newPiece() {
        _state.update { it.copy(pieceRequest = it.pieceRequest + 1) }
    }

    fun setPlaying(playing: Boolean) {
        _state.update { s ->
            if (s.playing == playing) s
            else s.copy(
                playing = playing,
                timerEndsAt = if (playing && s.timerMinutes > 0) System.currentTimeMillis() + s.timerMinutes * 60_000L else 0L,
            )
        }
        if (!playing) _nowPlaying.value = _nowPlaying.value?.copy(bpm = 0f)
    }

    fun setTimer(minutes: Int) {
        _state.update { s ->
            s.copy(
                timerMinutes = minutes,
                timerEndsAt = if (s.playing && minutes > 0) System.currentTimeMillis() + minutes * 60_000L else 0L,
            )
        }
        save()
    }

    fun publish(np: NowPlaying?) {
        _nowPlaying.value = np
    }

    private fun save() {
        val s = _state.value
        prefs?.edit()?.apply {
            putString("mood", s.mood.id)
            s.settings.forEach { (m, v) ->
                putInt("bpm_${m.id}", v.bpm)
                putFloat("int_${m.id}", v.intensity)
            }
            putInt("timer", s.timerMinutes)
            apply()
        }
    }
}
