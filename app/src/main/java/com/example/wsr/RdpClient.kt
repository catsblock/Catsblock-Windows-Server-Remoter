package com.example.wsr

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

interface RdpListener {
    fun onLog(msg: String)
    fun onCertificate(fingerprint: String, accept: (Boolean) -> Unit)
    fun onError(code: String)
    fun onConnected(osName: String)
    fun onFrame(bmp: Bitmap)
    fun onClipboard(text: String) {}
}

interface RdpClient {
    fun connect(s: Server, l: RdpListener)
    fun disconnect()
    fun mouseMove(dx: Float, dy: Float)
    fun mouseButton(button: Int, down: Boolean)
    fun scroll(amount: Int)
    fun key(androidKeyCode: Int, down: Boolean)
    fun pasteText(text: String)
    fun setClipboard(text: String)
}

class FreeRdpClient : RdpClient {
    private val timeoutSec = 300
    private val main = Handler(Looper.getMainLooper())
    private var listener: RdpListener? = null
    private var bitmap: Bitmap? = null
    @Volatile private var live = false
    @Volatile private var ticking = false
    @Volatile private var connected = false
    @Volatile private var stage = ""
    @Volatile private var lastClip = ""
    private var startTime = 0L

    private external fun nativeRun(
        host: String, port: Int, user: String, pass: String, gateway: String,
        admin: Boolean, audio: Int, w: Int, h: Int, bmp: Bitmap
    )
    private external fun nativeStop()
    private external fun nativeMove(dx: Int, dy: Int)
    private external fun nativeButton(btn: Int, down: Boolean)
    private external fun nativeWheel(up: Boolean)
    private external fun nativeKey(sc: Int, down: Boolean)
    private external fun nativeUnicode(code: Int, down: Boolean)
    private external fun nativeSetClip(text: String)

    fun jLog(s: String) {
        stage = s
        val sec = (System.currentTimeMillis() - startTime) / 1000
        main.post { if (live && ticking) listener?.onLog("$s\n${sec}s / ${timeoutSec}s") }
    }

    fun jError(s: String) {
        ticking = false
        val l = listener
        main.post { if (live) l?.onError("$s (stage: $stage)") }
    }

    fun jOk() {
        ticking = false
        connected = true
        main.post { if (live) listener?.onConnected("Windows") }
    }

    fun jFrame() {
        val b = bitmap ?: return
        val c = b.copy(Bitmap.Config.ARGB_8888, false)
        c.setHasAlpha(false)
        main.post { if (live) listener?.onFrame(c) }
    }

    fun jClip(text: String) {
        lastClip = text
        main.post { if (live) listener?.onClipboard(text) }
    }

    fun jCert(fp: String): Boolean {
        val latch = CountDownLatch(1)
        val ok = AtomicBoolean(false)
        main.post {
            val l = listener
            if (l == null) latch.countDown()
            else l.onCertificate(fp) { ok.set(it); latch.countDown() }
        }
        latch.await(timeoutSec.toLong(), TimeUnit.SECONDS)
        return ok.get()
    }

    override fun connect(s: Server, l: RdpListener) {
        listener = l
        live = true
        ticking = true
        connected = false
        startTime = System.currentTimeMillis()
        stage = "Starting connection"
        l.onLog("$stage\n0s / ${timeoutSec}s")

        val ticker = object : Runnable {
            override fun run() {
                if (!ticking || !live) return
                val sec = (System.currentTimeMillis() - startTime) / 1000
                l.onLog("$stage\n${sec}s / ${timeoutSec}s")
                main.postDelayed(this, 1000)
            }
        }
        main.postDelayed(ticker, 1000)

        var host = s.host.trim()
        var port = 3389
        val idx = host.lastIndexOf(':')
        if (idx > 0 && host.substring(idx + 1).isNotEmpty() && host.substring(idx + 1).all { it.isDigit() }) {
            port = host.substring(idx + 1).toInt()
            host = host.substring(0, idx)
        }
        val bmp = Bitmap.createBitmap(1280, 720, Bitmap.Config.ARGB_8888)
        bmp.setHasAlpha(false)
        bitmap = bmp
        val gw = s.gateway.trim()
        val audio = s.audio.coerceIn(0, 2)

        Thread {
            try {
                System.loadLibrary("wsr")
                nativeRun(host, port, s.user, s.password, gw, s.admin, audio, 1280, 720, bmp)
            } catch (e: Throwable) {
                ticking = false
                if (live) main.post { l.onError("${e.javaClass.simpleName}: ${e.message} (stage: $stage)") }
            }
        }.start()
    }

    override fun disconnect() {
        live = false
        ticking = false
        connected = false
        try {
            nativeStop()
        } catch (_: Throwable) {
        }
    }

    override fun setClipboard(text: String) {
        if (!connected || text == lastClip) return
        lastClip = text
        Thread { try { nativeSetClip(text) } catch (_: Throwable) {} }.start()
    }

    override fun mouseMove(dx: Float, dy: Float) {
        if (!live) return
        try { nativeMove(dx.toInt(), dy.toInt()) } catch (_: Throwable) {}
    }

    override fun mouseButton(button: Int, down: Boolean) {
        if (!live) return
        try { nativeButton(button, down) } catch (_: Throwable) {}
    }

    override fun scroll(amount: Int) {
        if (!live) return
        try { nativeWheel(amount > 0) } catch (_: Throwable) {}
    }

    private val letters = intArrayOf(
        0x1E, 0x30, 0x2E, 0x20, 0x12, 0x21, 0x22, 0x23, 0x17, 0x24, 0x25, 0x26, 0x32,
        0x31, 0x18, 0x19, 0x10, 0x13, 0x1F, 0x14, 0x16, 0x2F, 0x11, 0x2D, 0x15, 0x2C
    )

    private fun scancode(code: Int): Int = when (code) {
        in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z -> letters[code - KeyEvent.KEYCODE_A]
        in KeyEvent.KEYCODE_1..KeyEvent.KEYCODE_9 -> 0x02 + (code - KeyEvent.KEYCODE_1)
        KeyEvent.KEYCODE_0 -> 0x0B
        KeyEvent.KEYCODE_SPACE -> 0x39
        KeyEvent.KEYCODE_ENTER -> 0x1C
        KeyEvent.KEYCODE_ESCAPE -> 0x01
        KeyEvent.KEYCODE_TAB -> 0x0F
        KeyEvent.KEYCODE_DEL -> 0x0E
        KeyEvent.KEYCODE_FORWARD_DEL -> 0x153
        KeyEvent.KEYCODE_DPAD_LEFT -> 0x14B
        KeyEvent.KEYCODE_DPAD_UP -> 0x148
        KeyEvent.KEYCODE_DPAD_RIGHT -> 0x14D
        KeyEvent.KEYCODE_DPAD_DOWN -> 0x150
        KeyEvent.KEYCODE_CTRL_LEFT -> 0x1D
        KeyEvent.KEYCODE_SHIFT_LEFT -> 0x2A
        KeyEvent.KEYCODE_ALT_LEFT -> 0x38
        KeyEvent.KEYCODE_META_LEFT -> 0x15B
        else -> 0
    }

    override fun key(androidKeyCode: Int, down: Boolean) {
        if (!live) return
        val sc = scancode(androidKeyCode)
        if (sc != 0) try { nativeKey(sc, down) } catch (_: Throwable) {}
    }

    override fun pasteText(text: String) {
        if (!live) return
        Thread {
            try {
                for (ch in text) {
                    if (ch == '\n') {
                        nativeKey(0x1C, true); nativeKey(0x1C, false)
                    } else {
                        nativeUnicode(ch.code, true)
                        nativeUnicode(ch.code, false)
                    }
                }
            } catch (_: Throwable) {
            }
        }.start()
    }
}
