package io.github.haigerman.organicmusic

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import io.github.haigerman.organicmusic.data.MusicRepository
import io.github.haigerman.organicmusic.ui.MusicScreen
import io.github.haigerman.organicmusic.ui.OrganicTheme

class MainActivity : ComponentActivity() {

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* egal – Wiedergabe läuft auch ohne */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MusicRepository.init(this)
        enableEdgeToEdge()
        setContent {
            OrganicTheme {
                MusicScreen(onPlayPause = ::togglePlayback)
            }
        }
    }

    private fun togglePlayback() {
        if (MusicRepository.state.value.playing) {
            MusicRepository.setPlaying(false)
        } else {
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            PlaybackService.play(this)
        }
    }
}
