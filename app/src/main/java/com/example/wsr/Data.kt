package com.example.wsr

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class Server(
    val id: String = UUID.randomUUID().toString(),
    val host: String = "",
    val user: String = "",
    val password: String = "",
    val friendly: String = "",
    val admin: Boolean = false,
    val swapMouse: Boolean = false,
    val gateway: String = "",
    val audio: Int = 0,
    val mic: Boolean = false,
    val camera: Boolean = false,
    val storage: Boolean = false,
    val clipboard: Boolean = true,
    val favorite: Boolean = false,
    val os: String = "Windows",
    val resW: Int = 1280,
    val resH: Int = 720,
    val maxW: Int = 2560,
    val maxH: Int = 1440
) {
    val title get() = friendly.ifBlank { host }

    fun json(): JSONObject = JSONObject()
        .put("id", id).put("host", host).put("user", user).put("password", password)
        .put("friendly", friendly).put("admin", admin).put("swap", swapMouse)
        .put("gateway", gateway).put("audio", audio).put("mic", mic).put("camera", camera)
        .put("storage", storage).put("clipboard", clipboard).put("fav", favorite).put("os", os)
        .put("resW", resW).put("resH", resH).put("maxW", maxW).put("maxH", maxH)

    companion object {
        fun from(o: JSONObject) = Server(
            id = o.getString("id"),
            host = o.getString("host"),
            user = o.getString("user"),
            password = o.getString("password"),
            friendly = o.getString("friendly"),
            admin = o.getBoolean("admin"),
            swapMouse = o.getBoolean("swap"),
            gateway = o.getString("gateway"),
            audio = o.getInt("audio"),
            mic = o.getBoolean("mic"),
            camera = o.getBoolean("camera"),
            storage = o.getBoolean("storage"),
            clipboard = o.getBoolean("clipboard"),
            favorite = o.getBoolean("fav"),
            os = o.getString("os"),
            resW = o.optInt("resW", 1280),
            resH = o.optInt("resH", 720),
            maxW = o.optInt("maxW", 2560),
            maxH = o.optInt("maxH", 1440)
        )
    }
}

object Store {
    private lateinit var sp: SharedPreferences

    fun init(c: Context) {
        sp = c.getSharedPreferences("wsr", 0)
    }

    var agreed: Boolean
        get() = sp.getBoolean("agreed", false)
        set(v) = sp.edit().putBoolean("agreed", v).apply()

    fun servers(): List<Server> {
        val a = JSONArray(sp.getString("servers", "[]"))
        return (0 until a.length()).map { Server.from(a.getJSONObject(it)) }
    }

    fun save(l: List<Server>) {
        val a = JSONArray()
        l.forEach { a.put(it.json()) }
        sp.edit().putString("servers", a.toString()).apply()
    }

    fun f(k: String, d: Float) = sp.getFloat(k, d)
    fun b(k: String, d: Boolean) = sp.getBoolean(k, d)
    fun putF(k: String, v: Float) = sp.edit().putFloat(k, v).apply()
    fun putB(k: String, v: Boolean) = sp.edit().putBoolean(k, v).apply()
}

object Prefs {
    var joystick by mutableStateOf(Store.b("joy", true))
    var space by mutableStateOf(Store.b("space", true))
    var scrollButtons by mutableStateOf(Store.b("scroll", false))
    var mouseButtons by mutableStateOf(Store.b("mouseBtns", false))
    var joySize by mutableStateOf(Store.f("joySize", 90f))
    var cursorSize by mutableStateOf(Store.f("cursorSize", 1f))
    var barAlpha by mutableStateOf(Store.f("barAlpha", 0.35f))
    var dynamicColor by mutableStateOf(Store.b("dyn", true))
    var sensitivity by mutableStateOf(Store.f("sens", 1.5f))

    fun save() {
        Store.putB("joy", joystick)
        Store.putB("space", space)
        Store.putB("scroll", scrollButtons)
        Store.putB("mouseBtns", mouseButtons)
        Store.putF("joySize", joySize)
        Store.putF("cursorSize", cursorSize)
        Store.putF("barAlpha", barAlpha)
        Store.putB("dyn", dynamicColor)
        Store.putF("sens", sensitivity)
    }
}
