package io.github.haigerman.organicmusic.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import io.github.haigerman.organicmusic.audio.Mood

val Night = Color(0xFF0E1620)
val Card = Color(0xFF17222D)
val CardOn = Color(0xFF1F2E3B)
val TextMain = Color(0xFFEAF1F1)
val TextSoft = Color(0xFFB3C2C8)

/** Akzentfarbe je Stimmung. */
val Mood.accent: Color
    get() = when (this) {
        Mood.SPORT -> Color(0xFFFF8A65)
        Mood.FOCUS -> Color(0xFF82B1FF)
        Mood.FLOW -> Color(0xFFF2C94C)
        Mood.RELAX -> Color(0xFF7FD1AE)
        Mood.SLEEP -> Color(0xFFB39DDB)
    }

/** Dunkler Farbton für den Hintergrundverlauf. */
val Mood.deep: Color
    get() = when (this) {
        Mood.SPORT -> Color(0xFF3A1E1A)
        Mood.FOCUS -> Color(0xFF16263E)
        Mood.FLOW -> Color(0xFF35301A)
        Mood.RELAX -> Color(0xFF123229)
        Mood.SLEEP -> Color(0xFF221C3A)
    }

private val colors = darkColorScheme(
    primary = Color(0xFFE8C77A),
    onPrimary = Color(0xFF2A1E05),
    background = Night,
    surface = Card,
    onSurface = TextMain,
    onSurfaceVariant = TextSoft,
    surfaceVariant = CardOn,
)

@Composable
fun OrganicTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, content = content)
}
