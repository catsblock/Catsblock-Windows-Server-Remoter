package com.example.wsr

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.Executors
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

interface RdpListener {
    fun onLog(msg: String)
    fun onCertificate(fingerprint: String, accept: (Boolean) -> Unit)
    fun onError(code: String)
    fun onConnected(osName: String)
    fun onFrame(bmp: Bitmap)
}

interface RdpClient {
    fun connect(s: Server, l: RdpListener)
    fun disconnect()
    fun mouseMove(dx: Float, dy: Float)
    fun mouseButton(button: Int, down: Boolean)
    fun scroll(amount: Int)
    fun key(androidKeyCode: Int, down: Boolean)
    fun pasteText(text: String)
}

typealias FreeRdpClient = VncClient

class VncClient : RdpClient {
    private val timeoutMs = 300000
    private val main = Handler(Looper.getMainLooper())
    private val sender = Executors.newSingleThreadExecutor()
    private val lock = Any()
    @Volatile private var running = false
    @Volatile private var stage = ""
    @Volatile private var ticking = false
    private var startTime = 0L
    private var socket: Socket? = null
    private var out: DataOutputStream? = null
    private var width = 0
    private var height = 0
    private var mx = 0f
    private var my = 0f
    private var mask = 0

    override fun connect(s: Server, l: RdpListener) {
        running = true
        startTime = System.currentTimeMillis()
        ticking = true
        setStage(l, "Starting connection")
        val ticker = object : Runnable {
            override fun run() {
                if (!ticking || !running) return
                val sec = (System.currentTimeMillis() - startTime) / 1000
                l.onLog("$stage\n${sec}s / ${timeoutMs / 1000}s")
                main.postDelayed(this, 1000)
            }
        }
        main.postDelayed(ticker, 1000)
        Thread {
            try {
                run(s, l)
            } catch (e: Exception) {
                ticking = false
                if (running) {
                    val msg = "${e.javaClass.simpleName}: ${e.message} (stage: $stage)"
                    main.post { l.onError(msg) }
                }
            }
        }.start()
    }

    private fun setStage(l: RdpListener, text: String) {
        stage = text
        val sec = (System.currentTimeMillis() - startTime) / 1000
        main.post { l.onLog("$text\n${sec}s / ${timeoutMs / 1000}s") }
    }

    private fun run(s: Server, l: RdpListener) {
        var host = s.host.trim()
        var port = 5900
        val idx = host.lastIndexOf(':')
        if (idx > 0 && host.substring(idx + 1).isNotEmpty() && host.substring(idx + 1).all { it.isDigit() }) {
            port = host.substring(idx + 1).toInt()
            host = host.substring(0, idx)
        }

        setStage(l, "Resolving host $host")
        val address = InetAddress.getByName(host)

        setStage(l, "Connecting to ${address.hostAddress}:$port")
        val sock = Socket()
        sock.connect(InetSocketAddress(address, port), timeoutMs)
        sock.tcpNoDelay = true
        sock.soTimeout = timeoutMs
        socket = sock
        val input = DataInputStream(sock.getInputStream().buffered())
        val output = DataOutputStream(sock.getOutputStream())
        out = output

        setStage(l, "Connected to server. Waiting for protocol version")
        val ver = ByteArray(12)
        input.readFully(ver)
        setStage(l, "Server version: ${String(ver).trim()}. Negotiating protocol")
        output.write("RFB 003.008\n".toByteArray())
        output.flush()

        setStage(l, "Waiting for security types")
        val n = input.readUnsignedByte()
        if (n == 0) {
            val len = input.readInt()
            val msg = ByteArray(len)
            input.readFully(msg)
            throw Exception(String(msg))
        }
        val types = ByteArray(n)
        input.readFully(types)
        val choose = when {
            types.contains(1.toByte()) -> 1
            types.contains(2.toByte()) -> 2
            else -> throw Exception("Unsupported security type")
        }
        setStage(l, if (choose == 1) "Security: none" else "Security: password")
        output.writeByte(choose)
        output.flush()

        if (choose == 2) {
            setStage(l, "Waiting for authentication challenge")
            val challenge = ByteArray(16)
            input.readFully(challenge)
            setStage(l, "Sending password")
            output.write(encrypt(s.password, challenge))
            output.flush()
        }
        setStage(l, "Waiting for authentication result")
        val result = input.readInt()
        if (result != 0) {
            val len = input.readInt()
            val msg = ByteArray(len)
            input.readFully(msg)
            throw Exception("Authentication failed: ${String(msg)}")
        }

        setStage(l, "Authenticated. Initializing session")
        output.writeByte(1)
        output.flush()
        width = input.readUnsignedShort()
        height = input.readUnsignedShort()
        input.skipBytes(16)
        val nameLen = input.readInt()
        input.skipBytes(nameLen)
        mx = width / 2f
        my = height / 2f

        setStage(l, "Configuring display ${width}x$height")
        val pf = ByteArray(20)
        pf[0] = 0
        pf[4] = 32
        pf[5] = 24
        pf[6] = 0
        pf[7] = 1
        pf[8] = 0; pf[9] = 255.toByte()
        pf[10] = 0; pf[11] = 255.toByte()
        pf[12] = 0; pf[13] = 255.toByte()
        pf[14] = 16
        pf[15] = 8
        pf[16] = 0
        synchronized(lock) {
            output.write(pf)
            output.write(byteArrayOf(2, 0, 0, 1, 0, 0, 0, 0))
            output.flush()
        }

        val fb = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        setStage(l, "Waiting for first screen image")
        requestUpdate(false)

        var first = true
        while (running) {
            when (input.readUnsignedByte()) {
                0 -> {
                    input.readUnsignedByte()
                    val rects = input.readUnsignedShort()
                    for (i in 0 until rects) {
                        val x = input.readUnsignedShort()
                        val y = input.readUnsignedShort()
                        val w = input.readUnsignedShort()
                        val h = input.readUnsignedShort()
                        val enc = input.readInt()
                        if (enc != 0) throw Exception("Unsupported encoding $enc")
                        if (first) setStage(l, "Receiving screen data")
                        val buf = ByteArray(w * h * 4)
                        input.readFully(buf)
                        val px = IntArray(w * h)
                        for (p in px.indices) {
                            px[p] = (0xFF shl 24) or
                                ((buf[p * 4 + 2].toInt() and 0xFF) shl 16) or
                                ((buf[p * 4 + 1].toInt() and 0xFF) shl 8) or
                                (buf[p * 4].toInt() and 0xFF)
                        }
                        if (x + w <= width && y + h <= height) fb.setPixels(px, 0, w, x, y, w, h)
                    }
                    val copy = fb.copy(Bitmap.Config.ARGB_8888, false)
                    if (first) {
                        first = false
                        ticking = false
                        sock.soTimeout = 0
                        main.post { if (running) l.onConnected("Windows") }
                    }
                    main.post { if (running) l.onFrame(copy) }
                    requestUpdate(true)
                }
                1 -> throw Exception("Colour map not supported")
                2 -> {}
                3 -> {
                    input.skipBytes(3)
                    val len = input.readInt()
                    input.skipBytes(len)
                }
                else -> throw Exception("Unknown server message")
            }
        }
    }

    private fun encrypt(password: String, challenge: ByteArray): ByteArray {
        val key = ByteArray(8)
        val pw = password.toByteArray(Charsets.ISO_8859_1)
        for (i in 0 until 8) {
            val b = if (i < pw.size) pw[i].toInt() and 0xFF else 0
            key[i] = Integer.reverse(b).ushr(24).toByte()
        }
        val c = Cipher.getInstance("DES/ECB/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "DES"))
        return c.doFinal(challenge)
    }

    private fun send(b: ByteArray) {
        sender.execute {
            try {
                synchronized(lock) {
                    out?.write(b)
                    out?.flush()
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun requestUpdate(incremental: Boolean) {
        val b = java.nio.ByteBuffer.allocate(10)
        b.put(3)
        b.put(if (incremental) 1 else 0)
        b.putShort(0)
        b.putShort(0)
        b.putShort(width.toShort())
        b.putShort(height.toShort())
        send(b.array())
    }

    private fun pointer() {
        val b = java.nio.ByteBuffer.allocate(6)
        b.put(5)
        b.put(mask.toByte())
        b.putShort(mx.toInt().toShort())
        b.putShort(my.toInt().toShort())
        send(b.array())
    }

    override fun disconnect() {
        running = false
        ticking = false
        try {
            socket?.close()
        } catch (_: Exception) {
        }
        socket = null
        out = null
    }

    override fun mouseMove(dx: Float, dy: Float) {
        if (width == 0) return
        mx = (mx + dx).coerceIn(0f, (width - 1).toFloat())
        my = (my + dy).coerceIn(0f, (height - 1).toFloat())
        pointer()
    }

    override fun mouseButton(button: Int, down: Boolean) {
        val bit = if (button == 0) 1 else 4
        mask = if (down) mask or bit else mask and bit.inv()
        pointer()
    }

    override fun scroll(amount: Int) {
        val bit = if (amount > 0) 8 else 16
        val saved = mask
        mask = saved or bit
        pointer()
        mask = saved
        pointer()
    }

    private fun sendKey(sym: Int, down: Boolean) {
        val b = java.nio.ByteBuffer.allocate(8)
        b.put(4)
        b.put(if (down) 1 else 0)
        b.putShort(0)
        b.putInt(sym)
        send(b.array())
    }

    private fun keysym(code: Int): Int = when (code) {
        in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z -> 'a'.code + (code - KeyEvent.KEYCODE_A)
        in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> '0'.code + (code - KeyEvent.KEYCODE_0)
        KeyEvent.KEYCODE_SPACE -> 0x20
        KeyEvent.KEYCODE_ENTER -> 0xFF0D
        KeyEvent.KEYCODE_ESCAPE -> 0xFF1B
        KeyEvent.KEYCODE_TAB -> 0xFF09
        KeyEvent.KEYCODE_DEL -> 0xFF08
        KeyEvent.KEYCODE_FORWARD_DEL -> 0xFFFF
        KeyEvent.KEYCODE_DPAD_LEFT -> 0xFF51
        KeyEvent.KEYCODE_DPAD_UP -> 0xFF52
        KeyEvent.KEYCODE_DPAD_RIGHT -> 0xFF53
        KeyEvent.KEYCODE_DPAD_DOWN -> 0xFF54
        KeyEvent.KEYCODE_CTRL_LEFT -> 0xFFE3
        KeyEvent.KEYCODE_SHIFT_LEFT -> 0xFFE1
        KeyEvent.KEYCODE_ALT_LEFT -> 0xFFE9
        KeyEvent.KEYCODE_META_LEFT -> 0xFFEB
        else -> 0
    }

    override fun key(androidKeyCode: Int, down: Boolean) {
        val sym = keysym(androidKeyCode)
        if (sym != 0) sendKey(sym, down)
    }

    override fun pasteText(text: String) {
        for (ch in text) {
            val sym = when {
                ch == '\n' -> 0xFF0D
                ch == '\t' -> 0xFF09
                ch.code < 256 -> ch.code
                else -> 0x01000000 + ch.code
            }
            sendKey(sym, true)
            sendKey(sym, false)
        }
    }
}
