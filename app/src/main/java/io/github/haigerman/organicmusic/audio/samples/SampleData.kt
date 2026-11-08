package io.github.haigerman.organicmusic.audio.samples

import java.nio.ShortBuffer

/**
 * Eine Aufnahme innerhalb eines Instruments: gilt für die Tasten [lo]..[hi] und die
 * Anschlagstärken [v0]..[v1] (1–127). [rr] = Variante (Round-Robin), [root] = gemessener
 * Grundton (MIDI, mit Nachkommastellen), [pos]/[len] in Frames innerhalb der PCM-Daten.
 * [loopStart]/[loopEnd] (relativ zu [pos]) für gehaltene Töne, sonst -1.
 */
class Zone(
    val key: Int,
    val lo: Int,
    val hi: Int,
    val v0: Int,
    val v1: Int,
    val rr: Int,
    val root: Float,
    val pos: Int,
    val len: Int,
    val loopStart: Int,
    val loopEnd: Int,
) {
    val looped: Boolean get() = loopStart >= 0 && loopEnd > loopStart
}

/**
 * Alle Aufnahmen eines Instruments als 16-Bit-PCM (verschachtelt bei Stereo).
 * [pcm] ist in der App eine in den Speicher eingeblendete Cache-Datei (belastet den
 * Java-Heap nicht), in Tests ein gewöhnliches Array.
 */
class SampleData(
    val id: String,
    val sampleRate: Int,
    val channels: Int,
    val frames: Int,
    val release: Float,
    val note: String,
    val zones: List<Zone>,
    val pcm: ShortBuffer,
) {
    companion object {
        /**
         * Versatz des Decoders in Frames: wie weit der Synchronisations-Klick von [sync]
         * entfernt liegt (positiv = Decoder hat vorne zusätzliche Frames ausgegeben).
         * 0, wenn kein Klick gefunden wird.
         */
        fun syncShift(pcm: ShortBuffer, channels: Int, sync: Int, searchFrames: Int = 8192): Int {
            if (sync < 0) return 0
            var best = 0
            var bestV = 0
            val frames = minOf(searchFrames, pcm.limit() / channels)
            for (f in 0 until frames) {
                val v = kotlin.math.abs(pcm.get(f * channels).toInt())
                if (v > bestV) {
                    bestV = v
                    best = f
                }
            }
            return if (bestV > 8000) best - sync else 0
        }

        /** Liest die Kopf- und Zonenzeilen aus inst.txt (siehe tools/build_instruments.py). */
        fun parseHeader(text: String): Header {
            val head = HashMap<String, String>()
            val zones = ArrayList<Zone>()
            for (raw in text.lineSequence()) {
                val line = raw.trim()
                if (line.isEmpty()) continue
                val sp = line.indexOf(' ')
                val name = if (sp < 0) line else line.substring(0, sp)
                val rest = if (sp < 0) "" else line.substring(sp + 1)
                if (name == "zone") {
                    val p = rest.split(' ')
                    zones += Zone(
                        key = p[0].toInt(), lo = p[1].toInt(), hi = p[2].toInt(), v0 = p[3].toInt(), v1 = p[4].toInt(),
                        rr = p[5].toInt(), root = p[6].toFloat(), pos = p[7].toInt(), len = p[8].toInt(),
                        loopStart = p[9].toInt(), loopEnd = p[10].toInt(),
                    )
                } else {
                    head[name] = rest
                }
            }
            return Header(
                id = head["id"] ?: "?",
                sampleRate = head["sampleRate"]?.toInt() ?: 48000,
                channels = head["channels"]?.toInt() ?: 1,
                frames = head["frames"]?.toInt() ?: 0,
                release = head["release"]?.toFloat() ?: 0.3f,
                note = head["note"] ?: "",
                zones = zones,
                sync = head["sync"]?.toInt() ?: -1,
            )
        }
    }

    class Header(
        val id: String,
        val sampleRate: Int,
        val channels: Int,
        val frames: Int,
        val release: Float,
        val note: String,
        val zones: List<Zone>,
        /** Frame des Synchronisations-Klicks im Vorspann (-1 = keiner). */
        val sync: Int = -1,
    ) {
        fun withPcm(pcm: ShortBuffer) = SampleData(id, sampleRate, channels, frames, release, note, zones, pcm)
    }
}

/** Lädt Instrumente nach ID (App: Assets + MediaCodec + Cache; Tests: ffmpeg). */
fun interface SampleLoader {
    fun load(id: String): SampleData
}

/** Die für ein Stück geladenen Instrumente. */
class SampleBank(private val map: Map<String, SampleData>) {
    operator fun get(id: String): SampleData =
        map[id] ?: throw IllegalStateException("Instrument '$id' wurde nicht geladen")

    fun has(id: String) = id in map

    companion object {
        /** Lädt mehrere Instrumente parallel (das erste Dekodieren nutzt so alle Kerne). */
        fun load(loader: SampleLoader, ids: Collection<String>): SampleBank {
            val pool = java.util.concurrent.Executors.newFixedThreadPool(minOf(4, ids.size.coerceAtLeast(1)))
            try {
                val futures = ids.associateWith { id -> pool.submit<SampleData> { loader.load(id) } }
                return SampleBank(futures.mapValues { it.value.get() })
            } finally {
                pool.shutdown()
            }
        }
    }
}
