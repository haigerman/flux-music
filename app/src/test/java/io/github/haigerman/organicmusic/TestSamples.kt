package io.github.haigerman.organicmusic

import io.github.haigerman.organicmusic.audio.samples.SampleData
import io.github.haigerman.organicmusic.audio.samples.SampleLoader
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.ShortBuffer
import java.util.concurrent.ConcurrentHashMap

/**
 * Lädt die Instrumente für Unit-Tests direkt aus app/src/main/assets und dekodiert sie mit
 * ffmpeg (muss installiert sein). Einmal geladene Instrumente bleiben für alle Tests im Speicher.
 */
object TestSamples : SampleLoader {
    private val cache = ConcurrentHashMap<String, SampleData>()
    private val root = File("src/main/assets/instruments")

    override fun load(id: String): SampleData = cache.getOrPut(id) {
        val header = SampleData.parseHeader(File(root, "$id/inst.txt").readText())
        val proc = ProcessBuilder(
            "ffmpeg", "-v", "error", "-i", File(root, "$id/samples.ogg").path,
            "-f", "s16le", "-ac", header.channels.toString(), "-ar", header.sampleRate.toString(), "-",
        ).start()
        val bytes = proc.inputStream.readBytes()
        proc.waitFor()
        val want = header.frames * header.channels
        val shorts = ShortArray(want)
        val sb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        sb.get(shorts, 0, minOf(want, sb.remaining()))
        header.withPcm(ShortBuffer.wrap(shorts))
    }
}
