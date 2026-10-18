package com.example.wsr

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket

class CurFrame(val bmp: Bitmap, val hx: Int, val hy: Int, val ms: Int)

class CursorAnim(val steps: List<CurFrame>) {
    val images: List<CurFrame> = steps.distinctBy { it.bmp }
}

object Extra {
    private lateinit var sp: SharedPreferences
    var fpsShower by mutableStateOf(false)
    var pingShower by mutableStateOf(false)
    var curName by mutableStateOf("")
    var custom by mutableStateOf<CursorAnim?>(null)
    val seen = mutableStateListOf<CurFrame>()

    fun init(c: Context) {
        sp = c.getSharedPreferences("wsr_extra", 0)
        fpsShower = sp.getBoolean("fps", false)
        pingShower = sp.getBoolean("ping", false)
        curName = sp.getString("curName", "") ?: ""
        val f = File(c.filesDir, "custom_cursor.bin")
        if (f.exists()) custom = CursorParser.parse(f.readBytes())
        if (custom == null) curName = ""
    }

    fun save() {
        sp.edit().putBoolean("fps", fpsShower).putBoolean("ping", pingShower)
            .putString("curName", curName).apply()
    }

    fun setCursor(c: Context, name: String, bytes: ByteArray): Boolean {
        val a = CursorParser.parse(bytes) ?: return false
        File(c.filesDir, "custom_cursor.bin").writeBytes(bytes)
        custom = a
        curName = name
        save()
        return true
    }

    fun clearCursor(c: Context) {
        File(c.filesDir, "custom_cursor.bin").delete()
        custom = null
        curName = ""
        save()
    }

    fun addSeen(f: CurFrame) {
        if (seen.size < 80 && seen.none { it.bmp.sameAs(f.bmp) }) seen.add(f)
    }
}

object Ping {
    fun measure(host: String, port: Int): Int = try {
        val t = System.nanoTime()
        Socket().use { it.connect(InetSocketAddress(host, port), 2000) }
        ((System.nanoTime() - t) / 1_000_000L).toInt()
    } catch (_: Throwable) {
        -1
    }
}

object CursorParser {
    private fun u16(b: ByteArray, o: Int) = (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8)
    private fun u32(b: ByteArray, o: Int) = u16(b, o) or (u16(b, o + 2) shl 16)

    fun parse(b: ByteArray): CursorAnim? = try {
        if (b.size > 12 && String(b, 0, 4) == "RIFF" && String(b, 8, 4) == "ACON") ani(b)
        else icon(b, 100)?.let { CursorAnim(listOf(it)) }
    } catch (_: Throwable) {
        null
    }

    private fun icon(b: ByteArray, ms: Int): CurFrame? {
        if (b.size < 22) return null
        val type = u16(b, 2)
        val count = u16(b, 4)
        if ((type != 1 && type != 2) || count < 1) return null
        var best = 0
        var bestArea = -1
        for (i in 0 until count) {
            val o = 6 + 16 * i
            if (o + 16 > b.size) break
            var w = b[o].toInt() and 0xFF
            var h = b[o + 1].toInt() and 0xFF
            if (w == 0) w = 256
            if (h == 0) h = 256
            if (w * h >= bestArea) {
                bestArea = w * h
                best = i
            }
        }
        val o = 6 + 16 * best
        val hx = if (type == 2) u16(b, o + 4) else 0
        val hy = if (type == 2) u16(b, o + 6) else 0
        val c = b.copyOf()
        c[2] = 1
        for (i in 0 until count) {
            val q = 6 + 16 * i
            if (q + 8 <= c.size) {
                c[q + 4] = 0
                c[q + 5] = 0
                c[q + 6] = 0
                c[q + 7] = 0
            }
        }
        val bmp = BitmapFactory.decodeByteArray(c, 0, c.size) ?: return null
        return CurFrame(bmp, hx, hy, ms)
    }

    private fun ani(b: ByteArray): CursorAnim? {
        var p = 12
        var disp = 10
        var nSteps = 0
        var rate: IntArray? = null
        var seq: IntArray? = null
        val frames = ArrayList<ByteArray>()
        while (p + 8 <= b.size) {
            val id = String(b, p, 4)
            val sz = u32(b, p + 4)
            val d = p + 8
            if (sz < 0 || d + sz > b.size) break
            when (id) {
                "anih" -> {
                    nSteps = u32(b, d + 8)
                    disp = u32(b, d + 28)
                }
                "rate" -> rate = IntArray(sz / 4) { u32(b, d + it * 4) }
                "seq " -> seq = IntArray(sz / 4) { u32(b, d + it * 4) }
                "LIST" -> if (sz >= 4 && String(b, d, 4) == "fram") {
                    var q = d + 4
                    val end = d + sz
                    while (q + 8 <= end) {
                        val sid = String(b, q, 4)
                        val ssz = u32(b, q + 4)
                        if (ssz < 0 || q + 8 + ssz > end) break
                        if (sid == "icon") frames.add(b.copyOfRange(q + 8, q + 8 + ssz))
                        q += 8 + ssz + (ssz and 1)
                    }
                }
            }
            p = d + sz + (sz and 1)
        }
        val imgs = frames.map { icon(it, 100) }
        if (imgs.isEmpty() || imgs.all { it == null }) return null
        val n = seq?.size ?: if (nSteps > 0) nSteps else imgs.size
        val steps = ArrayList<CurFrame>()
        for (i in 0 until n) {
            val idx = seq?.getOrNull(i) ?: i
            val f = imgs.getOrNull(idx % imgs.size) ?: continue
            val j = rate?.getOrNull(i) ?: disp
            steps.add(CurFrame(f.bmp, f.hx, f.hy, maxOf(16, j * 1000 / 60)))
        }
        return if (steps.isEmpty()) null else CursorAnim(steps)
    }
}
