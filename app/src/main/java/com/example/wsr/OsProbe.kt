package com.example.wsr

import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import java.security.cert.X509Certificate

object OsProbe {
    private val SIG = "NTLMSSP".toByteArray() + byteArrayOf(0)

    fun detect(host: String, port: Int): String? =
        try { probe(host, port) } catch (_: Throwable) { null }

    private fun der(tag: Int, body: ByteArray): ByteArray {
        val n = body.size
        val head = when {
            n < 128 -> byteArrayOf(tag.toByte(), n.toByte())
            n < 256 -> byteArrayOf(tag.toByte(), 0x81.toByte(), n.toByte())
            else -> byteArrayOf(tag.toByte(), 0x82.toByte(), (n shr 8).toByte(), n.toByte())
        }
        return head + body
    }

    private fun readFully(i: InputStream, b: ByteArray) {
        var p = 0
        while (p < b.size) {
            val n = i.read(b, p, b.size - p)
            if (n < 0) throw java.io.IOException("EOF")
            p += n
        }
    }

    private fun find(b: ByteArray, len: Int): Int {
        for (i in 0..len - SIG.size) {
            var ok = true
            for (j in SIG.indices) if (b[i + j] != SIG[j]) { ok = false; break }
            if (ok) return i
        }
        return -1
    }

    private fun probe(host: String, port: Int): String? {
        val sock = Socket()
        sock.connect(InetSocketAddress(host, port), 8000)
        sock.soTimeout = 8000
        try {
            val out = sock.getOutputStream()
            val inp = sock.getInputStream()
            out.write(
                byteArrayOf(
                    3, 0, 0, 0x13, 0x0E, 0xE0.toByte(), 0, 0, 0, 0, 0,
                    1, 0, 8, 0, 3, 0, 0, 0
                )
            )
            out.flush()
            val h = ByteArray(4)
            readFully(inp, h)
            val total = ((h[2].toInt() and 0xFF) shl 8) or (h[3].toInt() and 0xFF)
            if (total < 19) return null
            val rest = ByteArray(total - 4)
            readFully(inp, rest)
            val n = rest.size
            if (rest[n - 8].toInt() != 2) return null
            if ((rest[n - 4].toInt() and 2) == 0) return null

            val tm = object : X509TrustManager {
                override fun checkClientTrusted(c: Array<X509Certificate>?, a: String?) {}
                override fun checkServerTrusted(c: Array<X509Certificate>?, a: String?) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            }
            val ctx = SSLContext.getInstance("TLS")
            ctx.init(null, arrayOf<TrustManager>(tm), null)
            val ssl = ctx.socketFactory.createSocket(sock, host, port, true) as SSLSocket
            ssl.soTimeout = 8000
            ssl.startHandshake()

            val ntlm = "NTLMSSP".toByteArray() + byteArrayOf(0) +
                byteArrayOf(1, 0, 0, 0) +
                byteArrayOf(0x05, 0x82.toByte(), 0x08, 0xE2.toByte()) +
                byteArrayOf(0, 0, 0, 0, 40, 0, 0, 0) +
                byteArrayOf(0, 0, 0, 0, 40, 0, 0, 0) +
                byteArrayOf(10, 0, 0x63, 0x45, 0, 0, 0, 0x0F)
            val token = der(0xA0, der(0x04, ntlm))
            val negoData = der(0xA1, der(0x30, der(0x30, token)))
            val ts = der(0x30, byteArrayOf(0xA0.toByte(), 3, 2, 1, 2) + negoData)
            val so = ssl.outputStream
            so.write(ts)
            so.flush()

            val si = ssl.inputStream
            val buf = ByteArray(8192)
            var len = 0
            var idx = -1
            while (len < buf.size) {
                val r = try { si.read(buf, len, buf.size - len) } catch (_: SocketTimeoutException) { -1 }
                if (r <= 0) break
                len += r
                idx = find(buf, len)
                if (idx >= 0 && len >= idx + 56) break
                idx = -1
            }
            if (idx < 0) return null
            if ((buf[idx + 23].toInt() and 0x02) == 0) return null
            val major = buf[idx + 48].toInt() and 0xFF
            val minor = buf[idx + 49].toInt() and 0xFF
            val build = (buf[idx + 50].toInt() and 0xFF) or ((buf[idx + 51].toInt() and 0xFF) shl 8)
            return name(major, minor, build)
        } finally {
            try { sock.close() } catch (_: Exception) {}
        }
    }

    private fun name(major: Int, minor: Int, b: Int): String = when {
        major == 10 && b >= 26100 -> "Windows 11 / Server 2025 · build $b"
        major == 10 && b >= 22000 -> "Windows 11 · build $b"
        major == 10 && b == 20348 -> "Windows Server 2022 · build $b"
        major == 10 && b >= 19041 -> "Windows 10 · build $b"
        major == 10 && b == 17763 -> "Windows 10 / Server 2019 · build $b"
        major == 10 && b == 14393 -> "Windows 10 / Server 2016 · build $b"
        major == 10 -> "Windows 10 · build $b"
        major == 6 && minor == 3 -> "Windows 8.1 / Server 2012 R2"
        major == 6 && minor == 2 -> "Windows 8 / Server 2012"
        major == 6 && minor == 1 -> "Windows 7 / Server 2008 R2"
        else -> "Windows $major.$minor · build $b"
    }
}
