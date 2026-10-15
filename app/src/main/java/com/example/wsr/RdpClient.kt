package com.example.wsr

import android.graphics.Bitmap

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

class FreeRdpClient : RdpClient {
    private var listener: RdpListener? = null

    override fun connect(s: Server, l: RdpListener) {
        listener = l
        l.onLog("Connecting to ${s.host}...")
        l.onError("0x00000001 (FreeRDP native library not linked)")
    }

    override fun disconnect() {
        listener = null
    }

    override fun mouseMove(dx: Float, dy: Float) {}
    override fun mouseButton(button: Int, down: Boolean) {}
    override fun scroll(amount: Int) {}
    override fun key(androidKeyCode: Int, down: Boolean) {}
    override fun pasteText(text: String) {}
}
