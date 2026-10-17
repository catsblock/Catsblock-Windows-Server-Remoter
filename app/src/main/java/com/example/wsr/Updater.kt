package com.example.wsr

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.PI
import kotlin.math.sin

object Updater {
    private const val API = "https://api.github.com/repos/catsblock/Catsblock-Windows-Server-Remoter/releases/latest"
    private const val ACTION = "com.example.wsr.INSTALL_RESULT"

    class Info(val code: Int, val name: String, val url: String, val size: Long)

    fun check(): Info? {
        val c = URL(API).openConnection() as HttpURLConnection
        c.connectTimeout = 15000
        c.readTimeout = 15000
        c.setRequestProperty("Accept", "application/vnd.github+json")
        if (c.responseCode != 200) return null
        val o = JSONObject(c.inputStream.bufferedReader().readText())
        val tag = o.getString("tag_name").removePrefix("v")
        val code = tag.substringAfterLast('.').toIntOrNull() ?: return null
        val assets = o.getJSONArray("assets")
        for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            if (a.getString("name").endsWith(".apk")) {
                return Info(code, tag, a.getString("browser_download_url"), a.getLong("size"))
            }
        }
        return null
    }

    fun download(info: Info, file: File, onProgress: (Long) -> Unit) {
        val c = URL(info.url).openConnection() as HttpURLConnection
        c.connectTimeout = 20000
        c.readTimeout = 30000
        if (c.responseCode != 200) throw IOException("HTTP ${c.responseCode}")
        var total = 0L
        c.inputStream.use { input ->
            file.outputStream().use { out ->
                val buf = ByteArray(65536)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    total += n
                    onProgress(total)
                }
            }
        }
        if (info.size > 0 && total != info.size) throw IOException("Incomplete download")
    }

    fun install(ctx: Context, file: File, onResult: (Boolean, String) -> Unit) {
        val app = ctx.applicationContext
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                when (i.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)) {
                    PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                        val act = if (Build.VERSION.SDK_INT >= 33) {
                            i.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            i.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                        }
                        act?.let {
                            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            c.startActivity(it)
                        }
                    }
                    PackageInstaller.STATUS_SUCCESS -> {
                        try { app.unregisterReceiver(this) } catch (_: Exception) {}
                        onResult(true, "")
                    }
                    else -> {
                        try { app.unregisterReceiver(this) } catch (_: Exception) {}
                        onResult(false, i.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "Install failed")
                    }
                }
            }
        }
        ContextCompat.registerReceiver(app, receiver, IntentFilter(ACTION), ContextCompat.RECEIVER_NOT_EXPORTED)

        val pi = app.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        val id = pi.createSession(params)
        val intent = Intent(ACTION).setPackage(app.packageName)
        val pending = PendingIntent.getBroadcast(
            app, id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        pi.openSession(id).use { s ->
            file.inputStream().use { input ->
                s.openWrite("app.apk", 0, file.length()).use { out ->
                    input.copyTo(out)
                    s.fsync(out)
                }
            }
            s.commit(pending.intentSender)
        }
    }
}

private fun mb(b: Long) = String.format("%.1f", b / 1048576.0)

@Composable
private fun WavyProgress(progress: Float, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val t = rememberInfiniteTransition(label = "wave")
    val phase by t.animateFloat(
        0f, (2 * PI).toFloat(),
        infiniteRepeatable(tween(1400, easing = LinearEasing)),
        label = "phase"
    )
    Canvas(modifier.fillMaxWidth().height(20.dp)) {
        val w = size.width
        val mid = size.height / 2f
        val stroke = 5.dp.toPx()
        val amp = size.height / 2f - stroke
        val wave = 28.dp.toPx()
        val end = w * progress.coerceIn(0f, 1f)
        if (end < w) {
            drawLine(
                cs.secondaryContainer,
                Offset(end, mid), Offset(w, mid),
                strokeWidth = stroke, cap = StrokeCap.Round
            )
        }
        if (end > 0f) {
            val p = Path()
            var x = 0f
            p.moveTo(0f, mid + amp * sin(-phase))
            while (x < end) {
                x = (x + 2f).coerceAtMost(end)
                p.lineTo(x, mid + amp * sin((x / wave) * 2f * PI.toFloat() - phase))
            }
            drawPath(p, cs.primary, style = Stroke(stroke, cap = StrokeCap.Round))
        }
    }
}

@Composable
fun UpdateHost() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycle = (ctx as ComponentActivity).lifecycle
    val file = remember { File(ctx.cacheDir, "update.apk") }
    var info by remember { mutableStateOf<Updater.Info?>(null) }
    var phase by remember { mutableIntStateOf(0) }
    var done by remember { mutableLongStateOf(0L) }
    var err by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val r = withContext(Dispatchers.IO) { runCatching { Updater.check() }.getOrNull() }
        if (r != null && r.code > BuildConfig.VERSION_CODE) info = r
    }

    fun install() {
        if (!ctx.packageManager.canRequestPackageInstalls()) {
            phase = 2
            return
        }
        phase = 3
        Updater.install(ctx, file) { ok, msg ->
            if (!ok) {
                err = msg
                phase = 4
            }
        }
    }

    fun download() {
        val i = info ?: return
        phase = 1
        done = 0
        scope.launch(Dispatchers.IO) {
            try {
                Updater.download(i, file) { done = it }
                install()
            } catch (e: Exception) {
                err = e.message ?: "Download failed"
                phase = 4
            }
        }
    }

    DisposableEffect(phase) {
        val o = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME && phase == 2 && ctx.packageManager.canRequestPackageInstalls()) {
                install()
            }
        }
        lifecycle.addObserver(o)
        onDispose { lifecycle.removeObserver(o) }
    }

    val i = info
    if (i == null || phase == -1) return
    val locked = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)

    when (phase) {
        0 -> AlertDialog(
            onDismissRequest = { phase = -1 },
            icon = { Icon(Icons.Rounded.SystemUpdate, null) },
            title = { Text("The app has received a new update.") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("This app has received a new update. Update to receive more features and bug fixes.")
                    Text(
                        "${mb(i.size)} MB — Version: ${i.name}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            },
            confirmButton = { TextButton({ download() }) { Text("Download") } },
            dismissButton = { TextButton({ phase = -1 }) { Text("After") } }
        )
        1 -> AlertDialog(
            onDismissRequest = {},
            properties = locked,
            icon = { Icon(Icons.Rounded.SystemUpdate, null) },
            title = { Text("Updating...") },
            text = {
                Column {
                    Text("We are updating your app. You will need to enable app installation if you haven't already enabled this permission.")
                    Spacer(Modifier.height(20.dp))
                    WavyProgress(if (i.size > 0) done.toFloat() / i.size else 0f)
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${mb(done)} / ${mb(i.size)} MB downloaded", style = MaterialTheme.typography.labelMedium)
                        Text("Version: ${i.name}", style = MaterialTheme.typography.labelMedium)
                    }
                }
            },
            confirmButton = {}
        )
        2 -> AlertDialog(
            onDismissRequest = {},
            properties = locked,
            icon = { Icon(Icons.Rounded.SystemUpdate, null) },
            title = { Text("Updating...") },
            text = { Text("Download finished. Allow this app to install other apps, then come back to finish the update.") },
            confirmButton = {
                TextButton({
                    ctx.startActivity(
                        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
                    )
                }) { Text("Open settings") }
            },
            dismissButton = { TextButton({ phase = -1 }) { Text("After") } }
        )
        3 -> AlertDialog(
            onDismissRequest = {},
            properties = locked,
            icon = { Icon(Icons.Rounded.SystemUpdate, null) },
            title = { Text("Updating...") },
            text = {
                Column {
                    Text("Installing version ${i.name}...")
                    Spacer(Modifier.height(20.dp))
                    WavyProgress(1f)
                }
            },
            confirmButton = {}
        )
        else -> AlertDialog(
            onDismissRequest = { phase = -1 },
            icon = { Icon(Icons.Rounded.SystemUpdate, null) },
            title = { Text("Update failed") },
            text = { Text(err) },
            confirmButton = { TextButton({ download() }) { Text("Try again") } },
            dismissButton = { TextButton({ phase = -1 }) { Text("After") } }
        )
    }
}
