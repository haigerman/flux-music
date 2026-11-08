package io.github.haigerman.organicmusic.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.haigerman.organicmusic.audio.Mood
import io.github.haigerman.organicmusic.data.MusicRepository
import io.github.haigerman.organicmusic.data.MusicState
import io.github.haigerman.organicmusic.data.NowPlaying
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.roundToInt

private val timerOptions = listOf(0, 15, 30, 45, 60, 90)

/** Beschriftung der Intensität je Stimmung: (niedrig, hoch). */
private val Mood.intensityLabels: Pair<String, String>
    get() = when (this) {
        Mood.SPORT -> "locker" to "volle Power"
        Mood.FOCUS -> "nur Klavier" to "mit Beat & Melodie"
        Mood.FLOW -> "reduziert" to "voller Groove"
        Mood.RELAX -> "Meer & Flächen" to "mehr Klavier"
        Mood.SLEEP -> "fast still" to "mehr Bewegung"
    }

private val Mood.hint: String?
    get() = when (this) {
        Mood.SPORT -> "Tipp Laufen: Tempo ≈ Schritte pro Minute (meist 160–180)"
        Mood.SLEEP -> "Tipp: Schlaf-Timer unten einstellen – die Musik blendet sanft aus"
        else -> null
    }

/** Ob die Stimmung einen deutlichen Beat hat (sonst "atmet" die Anzeige langsam). */
private val Mood.rhythmic: Boolean get() = this == Mood.SPORT || this == Mood.FOCUS || this == Mood.FLOW

@Composable
fun MusicScreen(onPlayPause: () -> Unit) {
    val state by MusicRepository.state.collectAsStateWithLifecycle()
    val now by MusicRepository.nowPlaying.collectAsStateWithLifecycle()
    val deep by animateColorAsState(state.mood.deep, tween(900), label = "deep")

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Night, deep))),
    ) {
        Column(Modifier.fillMaxSize()) {
            Header(Modifier.statusBarsPadding())
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(Mood.entries, key = { it.id }) { mood ->
                    MoodCard(mood, selected = mood == state.mood) { MusicRepository.selectMood(mood) }
                }
                item(key = "controls") { Controls(state) }
            }
            BottomPanel(state, now, onPlayPause, Modifier.navigationBarsPadding())
        }
    }
}

@Composable
private fun Header(modifier: Modifier = Modifier) {
    Column(modifier.padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 8.dp)) {
        Text("Organic Music", fontSize = 30.sp, fontWeight = FontWeight.Light, color = TextMain)
        Text(
            "Live komponiert – passend zu deiner Stimmung",
            fontSize = 14.sp,
            color = TextSoft,
        )
    }
}

@Composable
private fun MoodCard(mood: Mood, selected: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(if (selected) CardOn else Card.copy(alpha = 0.7f), label = "bg")
    val border by animateColorAsState(if (selected) mood.accent.copy(alpha = 0.8f) else Color.Transparent, label = "border")
    Surface(
        color = bg,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.5.dp, border),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(if (selected) mood.accent.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.06f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(mood.emoji, fontSize = 22.sp)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(mood.title, fontSize = 17.sp, color = TextMain)
                Text(mood.subtitle, fontSize = 13.sp, color = TextSoft)
            }
            Box(
                Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .border(2.dp, if (selected) mood.accent else TextSoft.copy(alpha = 0.4f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) Box(Modifier.size(10.dp).clip(CircleShape).background(mood.accent))
            }
        }
    }
}

@Composable
private fun Controls(state: MusicState) {
    val mood = state.mood
    val set = state.current
    val accent = mood.accent
    val sliderColors = SliderDefaults.colors(
        thumbColor = accent,
        activeTrackColor = accent,
        inactiveTrackColor = Color.White.copy(alpha = 0.15f),
    )
    Surface(color = Card.copy(alpha = 0.85f), shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
            // Tempo
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Tempo", fontSize = 15.sp, color = TextMain, modifier = Modifier.weight(1f))
                Text("${set.bpm} BPM", fontSize = 15.sp, color = accent, fontWeight = FontWeight.Medium)
            }
            Slider(
                value = set.bpm.toFloat(),
                onValueChange = { MusicRepository.setBpm(it.roundToInt()) },
                valueRange = mood.bpmMin.toFloat()..mood.bpmMax.toFloat(),
                colors = sliderColors,
            )
            mood.hint?.let { Text(it, fontSize = 12.sp, color = TextSoft) }

            Spacer(Modifier.height(10.dp))

            // Intensität
            Text("Intensität", fontSize = 15.sp, color = TextMain)
            Slider(
                value = set.intensity,
                onValueChange = { MusicRepository.setIntensity(it) },
                colors = sliderColors,
            )
            Row {
                Text(mood.intensityLabels.first, fontSize = 12.sp, color = TextSoft, modifier = Modifier.weight(1f))
                Text(mood.intensityLabels.second, fontSize = 12.sp, color = TextSoft)
            }

            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    onClick = { MusicRepository.newPiece() },
                    border = BorderStroke(1.dp, accent.copy(alpha = 0.6f)),
                ) {
                    Text("🎲  Neues Stück", color = TextMain)
                }
                Spacer(Modifier.weight(1f))
                AnimatedVisibility(
                    visible = set.bpm != mood.bpmDefault,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    TextButton(onClick = { MusicRepository.resetTempo() }) {
                        Text("Tempo zurücksetzen", color = TextSoft, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun BottomPanel(state: MusicState, now: NowPlaying?, onPlayPause: () -> Unit, modifier: Modifier = Modifier) {
    val accent = state.mood.accent
    Column(
        modifier
            .fillMaxWidth()
            .background(Night.copy(alpha = 0.88f))
            .padding(top = 12.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        NowPlayingLine(state, now)
        Spacer(Modifier.height(10.dp))
        PlayButton(state, now, onPlayPause)
        Spacer(Modifier.height(6.dp))
        TimerText(state)
        Spacer(Modifier.height(8.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Box(Modifier.height(32.dp), contentAlignment = Alignment.Center) {
                    Text("⏾ Timer", fontSize = 13.sp, color = TextSoft, modifier = Modifier.padding(end = 2.dp))
                }
            }
            items(timerOptions) { m ->
                FilterChip(
                    selected = state.timerMinutes == m,
                    onClick = { MusicRepository.setTimer(m) },
                    label = { Text(if (m == 0) "Aus" else "$m Min") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = accent.copy(alpha = 0.25f),
                        selectedLabelColor = accent,
                    ),
                )
            }
        }
    }
}

@Composable
private fun NowPlayingLine(state: MusicState, now: NowPlaying?) {
    val info = now?.info
    val text = if (state.playing && info != null && info.key.isNotEmpty()) {
        "${info.key}  ·  ${info.chord}  ·  ${info.part}"
    } else if (state.playing) {
        "Wird komponiert …"
    } else {
        "${state.mood.emoji}  ${state.mood.title}"
    }
    Text(text, fontSize = 14.sp, color = TextMain.copy(alpha = 0.9f))
}

@Composable
private fun TimerText(state: MusicState) {
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(state.timerEndsAt, state.playing) {
        while (state.playing && state.timerEndsAt > 0) {
            nowMs = System.currentTimeMillis()
            delay(1000)
        }
    }
    val text = when {
        state.playing && state.timerEndsAt > 0 -> {
            val rem = ((state.timerEndsAt - nowMs) / 1000).coerceAtLeast(0)
            "Endet in %d:%02d".format(rem / 60, rem % 60)
        }
        state.playing -> "Läuft endlos – jedes Stück ist neu"
        else -> "Tippe zum Starten"
    }
    Text(text, fontSize = 13.sp, color = TextSoft)
}

/** Play-Knopf, dessen Ring im Takt der Musik pulsiert (bzw. bei ruhigen Stimmungen langsam atmet). */
@Composable
private fun PlayButton(state: MusicState, now: NowPlaying?, onClick: () -> Unit) {
    val accent by animateColorAsState(state.mood.accent, tween(600), label = "accent")
    var beat by remember { mutableDoubleStateOf(0.0) }
    LaunchedEffect(state.playing, now) {
        if (!state.playing || now == null || now.bpm <= 0f) return@LaunchedEffect
        while (true) {
            withFrameNanos { t -> beat = now.beat + (t - now.atNanos) / 1e9 * now.bpm / 60.0 }
        }
    }
    val playing = state.playing && now != null
    val frac = (beat - floor(beat)).toFloat()
    val pulse = when {
        !playing -> 0f
        state.mood.rhythmic -> exp(-frac * 5f)
        else -> (0.5f - 0.5f * cos(2 * PI * beat / 4.0).toFloat()) // ein Atemzug pro Takt
    }
    val beatInBar = Math.floorMod(floor(beat).toInt(), 4)

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(100.dp)
                .clip(CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val r = size.minDimension / 2
                if (playing) {
                    drawCircle(accent.copy(alpha = 0.08f + 0.18f * pulse), radius = r * (0.8f + 0.2f * pulse))
                }
                drawCircle(accent, radius = r * 0.66f)
                drawCircle(accent.copy(alpha = 0.4f), radius = r * 0.76f, style = Stroke(width = 2.dp.toPx()))
                val c = center
                if (state.playing) {
                    val w = r * 0.12f
                    val h = r * 0.44f
                    drawRoundRect(Night, Offset(c.x - w * 1.9f, c.y - h / 2), Size(w * 1.2f, h), CornerRadius(w / 2))
                    drawRoundRect(Night, Offset(c.x + w * 0.7f, c.y - h / 2), Size(w * 1.2f, h), CornerRadius(w / 2))
                } else {
                    val s = r * 0.4f
                    val path = Path().apply {
                        moveTo(c.x - s * 0.45f, c.y - s * 0.6f)
                        lineTo(c.x + s * 0.65f, c.y)
                        lineTo(c.x - s * 0.45f, c.y + s * 0.6f)
                        close()
                    }
                    drawPath(path, Night)
                }
            }
        }
        // vier Punkte = vier Zählzeiten im Takt
        if (state.mood.rhythmic) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                for (i in 0 until 4) {
                    val on = playing && i == beatInBar
                    Box(
                        Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (on) accent else TextSoft.copy(alpha = 0.25f)),
                    )
                }
            }
        } else {
            Spacer(Modifier.height(12.dp))
        }
    }
}
