package com.mantra.mpaacademy

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack

/** One exercise: rows of cells, each row played `loops` times, then the next row, round and round. */
class Exercise(val code: String, val name: String, val base: Int, @Volatile var loops: Int, val notes: String, val rows: List<List<String>>,
               val rhythm: String = name, val beats: Int = (rows.firstOrNull()?.size ?: 16) / 2)   // request 23: the traditional rhythm of bar 1 and its number of beats   // loops: 0 = forever; read live every cell

/** Pre-mixed strokes, 16 bit mono 48 kHz, the site's own samples. */
class Bank(ctx: Context, set: String) {
    val s = HashMap<String, ShortArray>()
    init {
        for ((k, f) in listOf("D" to "D", "T" to "T", "K" to "k", "P" to "P", "-" to "touch")) {
            val b = ctx.assets.open("$set/$f.wav").use { it.readBytes() }
            var o = 12
            var data = ShortArray(0)
            while (o + 8 <= b.size) {
                val id = String(b, o, 4)
                val n = (b[o + 4].toInt() and 255) or ((b[o + 5].toInt() and 255) shl 8) or ((b[o + 6].toInt() and 255) shl 16) or ((b[o + 7].toInt() and 255) shl 24)
                if (id == "data") {
                    val len = minOf(n, b.size - o - 8) / 2
                    data = ShortArray(len) { i -> ((b[o + 8 + i * 2].toInt() and 255) or (b[o + 9 + i * 2].toInt() shl 8)).toShort() }
                    break
                }
                o += 8 + n
            }
            s[k] = data
        }
    }
}

/** Streams the exercise to an AudioTrack; `cell` reports which cell is heard now (row, index, repeat). */
class Player(private val ex: Exercise, private val bank: Bank, speed: Int, private val startRow: Int = 0, private val onEnd: () -> Unit) {
    private val rate = 48000
    private val stepFrames = (rate * 60.0 / ex.base / speed).toInt()
    private val track: AudioTrack
    @Volatile private var stopped = false
    private class Ev(val frame: Long, val r: Int, val j: Int, val rep: Int)
    private val events = java.util.concurrent.ConcurrentLinkedQueue<Ev>()
    private var thread: Thread? = null

    init {
        val buf = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT) * 2
        track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(rate).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setBufferSizeInBytes(buf).setTransferMode(AudioTrack.MODE_STREAM).build()
    }

    fun start() {
        track.play()
        thread = Thread {
            val tail = IntArray(rate)           // sample tails running over into the next cell
            val out = ShortArray(stepFrames)
            var frame = 0L; var r = startRow.coerceIn(0, ex.rows.size - 1)   // request 12: continue in sequence from the phrase on screen
            while (!stopped) {
                var rep = 1
                while (ex.loops == 0 || rep <= ex.loops) {
                    val row = ex.rows[r]
                    for (j in row.indices) {
                        if (stopped) return@Thread
                        val smp = bank.s[row[j].uppercase()]
                        events.add(Ev(frame, r, j, rep))
                        if (smp != null) for (i in smp.indices) if (i < tail.size) tail[i] += smp[i]
                        for (i in 0 until stepFrames) out[i] = tail[i].coerceIn(-32768, 32767).toShort()
                        System.arraycopy(tail, stepFrames, tail, 0, tail.size - stepFrames)
                        java.util.Arrays.fill(tail, tail.size - stepFrames, tail.size, 0)
                        track.write(out, 0, stepFrames)
                        frame += stepFrames
                    }
                    rep++
                }
                r = (r + 1) % ex.rows.size
            }
        }.also { it.isDaemon = true; it.start() }
    }

    /** The cell the speaker plays now, or null. */
    fun heard(): Triple<Int, Int, Int>? {
        val head = track.playbackHeadPosition.toLong() and 0xFFFFFFFFL
        var cur: Ev? = null
        while (true) {
            val e = events.peek() ?: break
            if (e.frame <= head) { cur = e; val n = events.elementAtOrNull(1); if (n != null && n.frame <= head) events.poll() else break } else break
        }
        return cur?.let { Triple(it.r, it.j, it.rep) }
    }

    fun stop() {
        if (stopped) return
        stopped = true
        try { track.pause(); track.flush(); track.release() } catch (_: Exception) {}
    }
}
