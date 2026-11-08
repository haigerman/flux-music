package io.github.haigerman.organicmusic.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Process

/**
 * Spielt den [MusicMixer] kontinuierlich über einen AudioTrack ab (eigener Audio-Thread).
 */
class AudioEngine(val sampleRate: Int) {
    val mixer = MusicMixer(sampleRate)

    @Volatile
    private var running = false

    @Volatile
    private var stopWhenSilent = false
    private var thread: Thread? = null

    /** Wie viele Frames gerendert, aber noch nicht hörbar sind (für die Puls-Anzeige). */
    @Volatile
    var latencyFrames = 0
        private set

    val isRunning: Boolean get() = thread?.isAlive == true

    /** Startet (bzw. setzt fort) und blendet ein. */
    fun start() {
        stopWhenSilent = false
        mixer.masterTarget = 1f
        if (isRunning) return
        mixer.setMasterImmediate(0f)
        mixer.masterTarget = 1f
        running = true
        thread = Thread(::loop, "music-audio").also { it.start() }
    }

    /** Blendet aus und beendet danach den Audio-Thread. */
    fun fadeOutAndStop() {
        mixer.masterTarget = 0f
        stopWhenSilent = true
    }

    fun release() {
        running = false
        thread?.join(500)
        thread = null
    }

    private fun loop() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
        val frames = 1024
        val minBytes = AudioTrack.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_FLOAT,
        )
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .build(),
            )
            .setBufferSizeInBytes(maxOf(minBytes, frames * 2 * 4 * 8))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        val buf = FloatArray(frames * 2)
        var written = 0L
        try {
            track.play()
            while (running) {
                mixer.render(buf, frames)
                track.write(buf, 0, buf.size, AudioTrack.WRITE_BLOCKING)
                written += frames
                val head = track.playbackHeadPosition.toLong() and 0xFFFFFFFFL
                latencyFrames = (written - head).coerceIn(0L, sampleRate.toLong()).toInt()
                if (stopWhenSilent && mixer.isSilent) break
            }
        } finally {
            try {
                track.stop()
            } catch (_: IllegalStateException) {
            }
            track.release()
            running = false
        }
    }
}
