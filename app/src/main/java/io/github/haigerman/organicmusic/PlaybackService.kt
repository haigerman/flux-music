package io.github.haigerman.organicmusic

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import io.github.haigerman.organicmusic.audio.AudioEngine
import io.github.haigerman.organicmusic.audio.Mood
import io.github.haigerman.organicmusic.data.MusicRepository
import io.github.haigerman.organicmusic.data.MusicState
import io.github.haigerman.organicmusic.data.NowPlaying
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Vordergrund-Dienst: komponiert und spielt weiter, auch wenn die App geschlossen
 * oder der Bildschirm aus ist.
 */
class PlaybackService : LifecycleService() {

    companion object {
        const val ACTION_PLAY = "io.github.haigerman.organicmusic.PLAY"
        const val ACTION_PAUSE = "io.github.haigerman.organicmusic.PAUSE"
        const val ACTION_NEW_PIECE = "io.github.haigerman.organicmusic.NEW_PIECE"
        private const val CHANNEL_ID = "playback"
        private const val NOTIFICATION_ID = 1
        private const val SAMPLE_RATE = 48000
        private const val TICK_MS = 100L
        private const val TIMER_FADE_MS = 30_000L

        fun play(context: Context) {
            ContextCompat.startForegroundService(
                context, Intent(context, PlaybackService::class.java).setAction(ACTION_PLAY),
            )
        }
    }

    private val engine by lazy { AudioEngine(SAMPLE_RATE) }
    private var playing = false
    private var foreground = false
    private var ticker: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var pausedByFocusLoss = false

    private var songMood: Mood? = null
    private var songPiece = -1
    private var notifiedText = ""

    private lateinit var audioManager: AudioManager
    private val focusRequest by lazy {
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            .setWillPauseWhenDucked(false)
            .setOnAudioFocusChangeListener { change ->
                when (change) {
                    AudioManager.AUDIOFOCUS_LOSS -> {
                        pausedByFocusLoss = false
                        MusicRepository.setPlaying(false)
                    }
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                        if (playing) pausedByFocusLoss = true
                        MusicRepository.setPlaying(false)
                    }
                    AudioManager.AUDIOFOCUS_GAIN -> {
                        if (pausedByFocusLoss) {
                            pausedByFocusLoss = false
                            play(this)
                        }
                    }
                }
            }
            .build()
    }

    /** Kopfhörer abgezogen → pausieren, damit nicht plötzlich der Lautsprecher losgeht. */
    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            MusicRepository.setPlaying(false)
        }
    }

    override fun onCreate() {
        super.onCreate()
        MusicRepository.init(this)
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        createChannel()
        ContextCompat.registerReceiver(
            this, noisyReceiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        lifecycleScope.launch {
            MusicRepository.state.collect { apply(it) }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_PLAY -> {
                goForeground(true)
                MusicRepository.setPlaying(true)
            }
            ACTION_PAUSE -> {
                MusicRepository.setPlaying(false)
                if (ticker?.isActive != true) stopSelf()
            }
            ACTION_NEW_PIECE -> {
                MusicRepository.newPiece()
                if (!playing) stopSelf()
            }
            else -> if (!playing) stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun apply(s: MusicState) {
        val set = s.current
        // Neues Stück bei Wechsel der Stimmung oder auf Wunsch – nur, wenn gerade gespielt wird
        // oder noch gar nichts geladen ist. Pausiert bleibt das alte Stück, bis es weitergeht.
        val needsSong = songMood != s.mood || songPiece != s.pieceRequest
        if (needsSong && (s.playing || songMood == null)) {
            engine.mixer.play(s.mood.create(SAMPLE_RATE, System.nanoTime(), set.bpm.toFloat(), set.intensity))
            songMood = s.mood
            songPiece = s.pieceRequest
        }
        engine.mixer.song?.let {
            it.targetBpm = set.bpm.toFloat()
            it.targetIntensity = set.intensity
        }
        if (s.playing && !playing) startPlayback()
        if (!s.playing && playing) stopPlayback()
    }

    private fun startPlayback() {
        playing = true
        audioManager.requestAudioFocus(focusRequest)
        acquireWakeLock()
        engine.start()
        goForeground(true)
        startTicker()
    }

    private fun stopPlayback() {
        playing = false
        engine.fadeOutAndStop()
        if (!pausedByFocusLoss) audioManager.abandonAudioFocusRequest(focusRequest)
        startTicker() // wartet das Ausblenden ab
        goForeground(false)
    }

    private fun startTicker() {
        if (ticker?.isActive == true) return
        ticker = lifecycleScope.launch {
            while (isActive) {
                val s = MusicRepository.state.value
                if (playing) {
                    var factor = 1f
                    if (s.timerEndsAt > 0) {
                        val remaining = s.timerEndsAt - System.currentTimeMillis()
                        if (remaining <= 0) {
                            MusicRepository.setPlaying(false)
                            factor = 0f
                        } else if (remaining < TIMER_FADE_MS) {
                            // sanftes, langes Ausblenden in den letzten 30 Sekunden
                            val x = remaining.toFloat() / TIMER_FADE_MS
                            factor = x * x
                        }
                    }
                    if (playing) engine.mixer.masterTarget = factor
                    publishNowPlaying()
                }
                if (!playing && !engine.isRunning) {
                    onFullyStopped()
                    break
                }
                delay(TICK_MS)
            }
        }
    }

    private fun publishNowPlaying() {
        val song = engine.mixer.song ?: return
        val info = engine.mixer.info ?: return
        val bpm = song.bpm
        // Was gerade gerendert wird, ist erst nach der Puffer-Latenz zu hören
        val latencyBeats = engine.latencyFrames.toDouble() / SAMPLE_RATE * bpm / 60.0
        MusicRepository.publish(NowPlaying(info, engine.mixer.beatPosition - latencyBeats, System.nanoTime(), bpm))
        val text = notificationText()
        if (text != notifiedText) goForeground(true)
    }

    private fun onFullyStopped() {
        releaseWakeLock()
        // Benachrichtigung bleibt (wegwischbar) zum Fortsetzen stehen; Dienst darf enden.
        stopSelf()
    }

    // ---------- Benachrichtigung ----------

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Wiedergabe", NotificationManager.IMPORTANCE_LOW).apply {
                setShowBadge(false)
            },
        )
    }

    private fun notificationText(): String {
        val info = MusicRepository.nowPlaying.value?.info
        return if (info == null || info.key.isEmpty()) "Wird komponiert …" else "${info.key} · ${info.part}"
    }

    private fun buildNotification(isPlaying: Boolean): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val toggle = if (isPlaying) {
            NotificationCompat.Action(
                0, "Pause",
                PendingIntent.getService(
                    this, 1, Intent(this, PlaybackService::class.java).setAction(ACTION_PAUSE),
                    PendingIntent.FLAG_IMMUTABLE,
                ),
            )
        } else {
            NotificationCompat.Action(
                0, "Fortsetzen",
                PendingIntent.getForegroundService(
                    this, 2, Intent(this, PlaybackService::class.java).setAction(ACTION_PLAY),
                    PendingIntent.FLAG_IMMUTABLE,
                ),
            )
        }
        val newPiece = NotificationCompat.Action(
            0, "Neues Stück",
            PendingIntent.getService(
                this, 3, Intent(this, PlaybackService::class.java).setAction(ACTION_NEW_PIECE),
                PendingIntent.FLAG_IMMUTABLE,
            ),
        )
        val mood = MusicRepository.state.value.mood
        val text = notificationText()
        notifiedText = text
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_music)
            .setContentTitle(if (isPlaying) "${mood.emoji} ${mood.title}" else "Pausiert · ${mood.title}")
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(isPlaying)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(toggle)
            .apply { if (isPlaying) addAction(newPiece) }
            .build()
    }

    private fun goForeground(isPlaying: Boolean) {
        val n = buildNotification(isPlaying)
        if (isPlaying) {
            ServiceCompat.startForeground(
                this, NOTIFICATION_ID, n,
                if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0,
            )
            foreground = true
        } else {
            if (foreground) {
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH)
                foreground = false
            }
            try {
                getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, n)
            } catch (_: SecurityException) {
            }
        }
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "organicmusic:playback").apply {
            setReferenceCounted(false)
            acquire(6 * 60 * 60 * 1000L)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    override fun onDestroy() {
        unregisterReceiver(noisyReceiver)
        engine.release()
        releaseWakeLock()
        if (playing) MusicRepository.setPlaying(false)
        super.onDestroy()
    }
}
