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
    val os: String = "Windows"
) {
    val title get() = friendly.ifBlank { host }

    fun json(): JSONObject = JSONObject()
        .put("id", id).put("host", host).put("user", user).put("password", password)
        .put("friendly", friendly).put("admin", admin).put("swap", swapMouse)
        .put("gateway", gateway).put("audio", audio).put("mic", mic).put("camera", camera)
        .put("storage", storage).put("clipboard", clipboard).put("fav", favorite).put("os", os)

    companion object {
        fun from(o: JSONObject) = Server(
            o.getString("id"), o.getString("host"), o.getString("user"),
            o.getString("password"), o.getString("friendly"), o.getBoolean("admin"),
            o.getBoolean("swap"), o.getString("gateway"), o.getInt("audio"),
            o.getBoolean("mic"), o.getBoolean("camera"), o.getBoolean("storage"),
            o.getBoolean("clipboard"), o.getBoolean("fav"), o.getString("os")
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
    var joySize by mutableStateOf(Store.f("joySize", 90f))
    var barAlpha by mutableStateOf(Store.f("barAlpha", 0.35f))
    var dynamicColor by mutableStateOf(Store.b("dyn", true))
    var sensitivity by mutableStateOf(Store.f("sens", 1.5f))

    fun save() {
        Store.putB("joy", joystick)
        Store.putB("space", space)
        Store.putF("joySize", joySize)
        Store.putF("barAlpha", barAlpha)
        Store.putB("dyn", dynamicColor)
        Store.putF("sens", sensitivity)
    }
}
