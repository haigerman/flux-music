package io.github.haigerman.organicmusic.audio

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import io.github.haigerman.organicmusic.audio.samples.SampleData
import io.github.haigerman.organicmusic.audio.samples.SampleLoader
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.util.concurrent.ConcurrentHashMap

/**
 * Lädt Instrumente aus assets/instruments/<id>/. Die Ogg-Datei wird beim ersten Mal mit
 * MediaCodec in eine PCM-Datei im Cache dekodiert; danach wird diese nur noch in den
 * Speicher eingeblendet (mmap) – das geht sofort und belastet den Java-Heap nicht.
 */
class AssetSampleLoader(context: Context) : SampleLoader {
    private val app = context.applicationContext
    private val dir = File(app.cacheDir, "pcm").apply { mkdirs() }
    private val loaded = ConcurrentHashMap<String, SampleData>()
    private val version: Long = try {
        @Suppress("DEPRECATION")
        app.packageManager.getPackageInfo(app.packageName, 0).let {
            if (android.os.Build.VERSION.SDK_INT >= 28) it.longVersionCode else it.versionCode.toLong()
        }
    } catch (_: Exception) {
        0L
    }

    override fun load(id: String): SampleData = loaded.getOrPut(id) { loadUncached(id) }

    private fun loadUncached(id: String): SampleData {
        val header = app.assets.open("instruments/$id/inst.txt").bufferedReader().use { SampleData.parseHeader(it.readText()) }
        val assetLen = app.assets.openFd("instruments/$id/samples.ogg").use { it.length }
        val file = File(dir, "$id-$version-$assetLen.pcm")
        val bytes = header.frames.toLong() * header.channels * 2
        if (!file.exists() || file.length() != bytes) {
            // alte Fassungen dieses Instruments entfernen
            dir.listFiles { f -> f.name.startsWith("$id-") && f != file }?.forEach { it.delete() }
            decode(id, header, file, bytes)
        }
        val buf = RandomAccessFile(file, "r").use { raf ->
            raf.channel.map(FileChannel.MapMode.READ_ONLY, 0, bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        }
        return header.withPcm(buf)
    }

    private fun decode(id: String, header: SampleData.Header, target: File, bytes: Long) {
        val tmp = File(dir, target.name + ".tmp")
        val extractor = MediaExtractor()
        app.assets.openFd("instruments/$id/samples.ogg").use { afd ->
            extractor.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
        }
        extractor.selectTrack(0)
        val format = extractor.getTrackFormat(0)
        val codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
        codec.configure(format, null, null, 0)
        codec.start()
        var written = 0L
        var floatOut = false
        try {
            BufferedOutputStream(FileOutputStream(tmp), 1 shl 16).use { out ->
                val info = MediaCodec.BufferInfo()
                var inputDone = false
                var outputDone = false
                val chunk = ByteArray(1 shl 15)
                while (!outputDone) {
                    if (!inputDone) {
                        val ii = codec.dequeueInputBuffer(5000)
                        if (ii >= 0) {
                            val ib = codec.getInputBuffer(ii)!!
                            val n = extractor.readSampleData(ib, 0)
                            if (n < 0) {
                                codec.queueInputBuffer(ii, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            } else {
                                codec.queueInputBuffer(ii, 0, n, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }
                    val oi = codec.dequeueOutputBuffer(info, 5000)
                    if (oi == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        val of = codec.outputFormat
                        floatOut = of.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                            of.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                    } else if (oi >= 0) {
                        val ob = codec.getOutputBuffer(oi)!!
                        ob.position(info.offset)
                        ob.limit(info.offset + info.size)
                        if (floatOut) {
                            val fb = ob.order(ByteOrder.nativeOrder()).asFloatBuffer()
                            while (fb.hasRemaining() && written < bytes) {
                                val s = (fb.get().coerceIn(-1f, 1f) * 32767f).toInt()
                                out.write(s and 0xFF)
                                out.write((s shr 8) and 0xFF)
                                written += 2
                            }
                        } else {
                            while (ob.hasRemaining()) {
                                val n = minOf(chunk.size, ob.remaining())
                                ob.get(chunk, 0, n)
                                val take = minOf(n.toLong(), bytes - written).toInt()
                                if (take > 0) out.write(chunk, 0, take)
                                written += take.coerceAtLeast(0)
                            }
                        }
                        codec.releaseOutputBuffer(oi, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    }
                }
                // sicherheitshalber auf die erwartete Länge auffüllen
                val pad = ByteArray(4096)
                while (written < bytes) {
                    val n = minOf(pad.size.toLong(), bytes - written).toInt()
                    out.write(pad, 0, n)
                    written += n
                }
            }
        } finally {
            codec.stop()
            codec.release()
            extractor.release()
        }
        align(header, tmp, target, bytes)
    }

    /**
     * Gleicht einen möglichen Versatz des Decoders aus: Der Klick im Vorspann muss genau auf
     * Frame [SampleData.Header.sync] liegen. Liegt er woanders, wird die Datei entsprechend
     * verschoben – so sitzt jeder Anschlag sample-genau.
     */
    private fun align(header: SampleData.Header, tmp: File, target: File, bytes: Long) {
        val ch = header.channels
        val probe = ByteArray(minOf(bytes, 8192L * ch).toInt())
        java.io.FileInputStream(tmp).use { it.read(probe) }
        val shift = SampleData.syncShift(
            java.nio.ByteBuffer.wrap(probe).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer(), ch, header.sync,
        )
        if (shift == 0 || kotlin.math.abs(shift) > 1024) {
            if (!tmp.renameTo(target)) {
                tmp.copyTo(target, overwrite = true)
                tmp.delete()
            }
            return
        }
        val frameBytes = 2L * ch
        java.io.FileInputStream(tmp).use { inp ->
            BufferedOutputStream(FileOutputStream(target), 1 shl 16).use { out ->
                if (shift > 0) {
                    inp.skip(shift * frameBytes) // Decoder hat vorne zu viel ausgegeben
                } else {
                    out.write(ByteArray((-shift * frameBytes).toInt())) // zu wenig: vorne auffüllen
                }
                val buf = ByteArray(1 shl 16)
                var left = bytes - (if (shift < 0) -shift * frameBytes else 0L)
                while (left > 0) {
                    val n = inp.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
                    if (n <= 0) break
                    out.write(buf, 0, n)
                    left -= n
                }
                while (left > 0) {
                    val n = minOf(buf.size.toLong(), left).toInt()
                    java.util.Arrays.fill(buf, 0, n, 0)
                    out.write(buf, 0, n)
                    left -= n
                }
            }
        }
        tmp.delete()
    }
}
