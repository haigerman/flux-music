package io.github.haigerman.organicmusic.data

import android.content.Context
import android.content.SharedPreferences
import io.github.haigerman.organicmusic.audio.Mood
import io.github.haigerman.organicmusic.audio.Style
import io.github.haigerman.organicmusic.audio.songs.SongInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Tempo und Intensität – je Stil gespeichert. */
data class MoodSettings(val bpm: Int, val intensity: Float)

data class MusicState(
    val mood: Mood = Mood.FOCUS,
    /** Gewählter Stil je Stimmung. */
    val styles: Map<Mood, Style> = emptyMap(),
    val settings: Map<Style, MoodSettings> = emptyMap(),
    val playing: Boolean = false,
    /** 0 = kein Timer */
    val timerMinutes: Int = 0,
    /** System.currentTimeMillis(), wann der Timer abläuft; 0 = keiner aktiv */
    val timerEndsAt: Long = 0L,
    /** Wird bei "Neues Stück" hochgezählt. */
    val pieceRequest: Int = 0,
) {
    fun styleOf(m: Mood): Style = styles[m] ?: m.styles.first()
    val style: Style get() = styleOf(mood)
    fun settings(s: Style): MoodSettings = settings[s] ?: MoodSettings(s.bpmDefault, s.intensityDefault)
    val current: MoodSettings get() = settings(style)
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

    /** Werden gerade Instrumente geladen (beim ersten Mal inkl. Dekodieren)? */
    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    fun setLoading(v: Boolean) {
        _loading.value = v
    }

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences("music", Context.MODE_PRIVATE)
        prefs = p
        val settings = Style.entries.associateWith { s ->
            MoodSettings(
                bpm = p.getInt("bpm_${s.key}", s.bpmDefault).coerceIn(s.bpmMin, s.bpmMax),
                intensity = p.getFloat("int_${s.key}", s.intensityDefault).coerceIn(0f, 1f),
            )
        }
        val styles = Mood.entries.associateWith { m ->
            val id = p.getString("style_${m.id}", null)
            m.styles.firstOrNull { it.id == id } ?: m.styles.first()
        }
        _state.value = MusicState(
            mood = Mood.byId(p.getString("mood", null)),
            styles = styles,
            settings = settings,
            timerMinutes = p.getInt("timer", 0),
        )
    }

    fun selectMood(m: Mood) {
        _state.update { it.copy(mood = m) }
        save()
    }

    fun selectStyle(s: Style) {
        _state.update { it.copy(mood = s.mood, styles = it.styles + (s.mood to s)) }
        save()
    }

    fun setBpm(bpm: Int) {
        _state.update { st ->
            val s = st.style
            st.copy(settings = st.settings + (s to st.current.copy(bpm = bpm.coerceIn(s.bpmMin, s.bpmMax))))
        }
        save()
    }

    fun setIntensity(v: Float) {
        _state.update { st -> st.copy(settings = st.settings + (st.style to st.current.copy(intensity = v.coerceIn(0f, 1f)))) }
        save()
    }

    fun resetTempo() {
        setBpm(_state.value.style.bpmDefault)
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
            s.styles.forEach { (m, st) -> putString("style_${m.id}", st.id) }
            s.settings.forEach { (st, v) ->
                putInt("bpm_${st.key}", v.bpm)
                putFloat("int_${st.key}", v.intensity)
            }
            putInt("timer", s.timerMinutes)
            apply()
        }
    }
}
