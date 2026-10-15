package com.example.wsr

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private sealed interface State {
    object Connecting : State
    object Connected : State
    class Error(val code: String) : State
}

private enum class G { UP, MOVE, HOLD }

@Composable
fun SessionScreen(
    server: Server,
    all: List<Server>,
    onOs: (String) -> Unit,
    onExit: () -> Unit,
    onSwitch: (Server) -> Unit
) {
    val ctx = LocalContext.current
    DisposableEffect(Unit) {
        val a = ctx as Activity
        a.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose { a.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }

    val client = remember(server.id) { FreeRdpClient() }
    var state by remember(server.id) { mutableStateOf<State>(State.Connecting) }
    var log by remember(server.id) { mutableStateOf("Starting connection...") }
    var frame by remember(server.id) { mutableStateOf<Bitmap?>(null) }
    var cert by remember(server.id) { mutableStateOf<Pair<String, (Boolean) -> Unit>?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    var showKeys by remember { mutableStateOf(false) }
    var showServers by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var zoom by remember { mutableFloatStateOf(1f) }

    BackHandler {
        client.disconnect()
        onExit()
    }

    DisposableEffect(client) {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val l = ClipboardManager.OnPrimaryClipChangedListener {
            cm.primaryClip?.getItemAt(0)?.text?.let { client.setClipboard(it.toString()) }
        }
        cm.addPrimaryClipChangedListener(l)
        onDispose { cm.removePrimaryClipChangedListener(l) }
    }

    DisposableEffect(server.id, retry) {
        state = State.Connecting
        log = "Starting connection..."
        client.connect(server, object : RdpListener {
            override fun onLog(msg: String) { log = msg }
            override fun onCertificate(fingerprint: String, accept: (Boolean) -> Unit) { cert = fingerprint to accept }
            override fun onError(code: String) { state = State.Error(code) }
            override fun onConnected(osName: String) { onOs(osName); state = State.Connected }
            override fun onFrame(bmp: Bitmap) { frame = bmp }
            override fun onClipboard(text: String) {
                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("rdp", text))
            }
        })
        onDispose { client.disconnect() }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when (val st = state) {
            State.Connecting -> Text(
                log,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center).padding(24.dp)
            )
            is State.Error -> Column(
                Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "The connection was interrupted. Error: ${st.code}",
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton({ onExit() }) { Text("Home page") }
                    Button({ retry++ }) { Text("Try Again") }
                }
            }
            State.Connected -> {
                frame?.let {
                    Image(
                        it.asImageBitmap(), null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                            .graphicsLayer(scaleX = zoom, scaleY = zoom)
                            .pointerInput(server.id) { touchpad(client, server.swapMouse) }
                    )
                }
                if (Prefs.joystick) {
                    Joystick(client, Modifier.align(Alignment.BottomStart).padding(16.dp))
                }
                if (Prefs.space) {
                    Box(
                        Modifier.align(Alignment.BottomEnd).padding(16.dp)
                            .size(width = 110.dp, height = 44.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.8f))
                            .pointerInput(Unit) {
                                awaitEachGesture {
                                    awaitFirstDown(requireUnconsumed = false)
                                    client.key(KeyEvent.KEYCODE_SPACE, true)
                                    waitForUpOrCancellation()
                                    client.key(KeyEvent.KEYCODE_SPACE, false)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Space", color = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                }
                Row(
                    Modifier.align(Alignment.TopCenter).fillMaxWidth().height(32.dp)
                        .background(Color.Black.copy(alpha = Prefs.barAlpha)),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BarIcon(Icons.Default.GridView) { showServers = true }
                    BarIcon(Icons.Default.ZoomIn) { zoom = if (zoom >= 2f) 1f else zoom + 0.5f }
                    BarIcon(Icons.Default.Settings) { showSettings = true }
                    BarIcon(Icons.Default.Keyboard) { showKeys = !showKeys }
                }
                if (showKeys) KeyPanel(client, Modifier.align(Alignment.BottomCenter))
            }
        }
    }

    cert?.let { (fp, cb) ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Unverified server") },
            text = { Text("This server could not be verified.\nFingerprint: $fp\nConnect anyway?") },
            confirmButton = { TextButton({ cb(true); cert = null }) { Text("Connect") } },
            dismissButton = { TextButton({ cb(false); cert = null }) { Text("Cancel") } }
        )
    }

    if (showServers) {
        AlertDialog(
            onDismissRequest = { showServers = false },
            title = { Text("Switch server") },
            text = {
                Column {
                    all.forEach {
                        TextButton({ showServers = false; onSwitch(it) }) { Text(it.title) }
                    }
                }
            },
            confirmButton = { TextButton({ showServers = false }) { Text("Close") } }
        )
    }

    if (showSettings) {
        AlertDialog(
            onDismissRequest = { showSettings = false },
            title = { Text("Settings") },
            text = { SettingsPage() },
            confirmButton = { TextButton({ showSettings = false }) { Text("Close") } }
        )
    }
}

@Composable
private fun BarIcon(icon: ImageVector, onClick: () -> Unit) {
    IconButton(onClick, Modifier.size(28.dp)) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp))
    }
}

private suspend fun PointerInputScope.touchpad(client: RdpClient, swap: Boolean) {
    val left = if (swap) 1 else 0
    val right = if (swap) 0 else 1
    var taps = 0
    var job: Job? = null
    coroutineScope {
        awaitEachGesture {
            val down = awaitFirstDown()
            val r = withTimeoutOrNull(2000) {
                var res = G.UP
                while (true) {
                    val c = awaitPointerEvent().changes.first()
                    if (!c.pressed) break
                    if ((c.position - down.position).getDistance() > 24f) {
                        res = G.MOVE
                        break
                    }
                }
                res
            } ?: G.HOLD

            if (r == G.HOLD) client.mouseButton(left, true)

            if (r != G.UP) {
                var prev = down.position
                while (true) {
                    val c = awaitPointerEvent().changes.first()
                    if (!c.pressed) break
                    val d = (c.position - prev) * Prefs.sensitivity
                    client.mouseMove(d.x, d.y)
                    prev = c.position
                    c.consume()
                }
                if (r == G.HOLD) client.mouseButton(left, false)
            } else {
                taps++
                job?.cancel()
                job = launch {
                    delay(300)
                    val btn = if (taps >= 3) right else left
                    client.mouseButton(btn, true)
                    client.mouseButton(btn, false)
                    taps = 0
                }
            }
        }
    }
}

@Composable
private fun Joystick(client: RdpClient, modifier: Modifier) {
    val size = Prefs.joySize.dp
    var knob by remember { mutableStateOf(Offset.Zero) }
    val active = remember { mutableSetOf<Int>() }

    fun update(code: Int, on: Boolean) {
        if (on && active.add(code)) client.key(code, true)
        if (!on && active.remove(code)) client.key(code, false)
    }

    Box(
        modifier.size(size).clip(CircleShape).background(Color.White.copy(alpha = 0.2f))
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = {
                        knob = Offset.Zero
                        listOf(
                            KeyEvent.KEYCODE_W, KeyEvent.KEYCODE_A,
                            KeyEvent.KEYCODE_S, KeyEvent.KEYCODE_D
                        ).forEach { update(it, false) }
                    },
                    onDragCancel = {
                        knob = Offset.Zero
                        listOf(
                            KeyEvent.KEYCODE_W, KeyEvent.KEYCODE_A,
                            KeyEvent.KEYCODE_S, KeyEvent.KEYCODE_D
                        ).forEach { update(it, false) }
                    }
                ) { change, _ ->
                    val c = Offset(this.size.width / 2f, this.size.height / 2f)
                    val d = change.position - c
                    val t = this.size.width / 6f
                    knob = Offset(d.x.coerceIn(-t * 2, t * 2), d.y.coerceIn(-t * 2, t * 2))
                    update(KeyEvent.KEYCODE_W, d.y < -t)
                    update(KeyEvent.KEYCODE_S, d.y > t)
                    update(KeyEvent.KEYCODE_A, d.x < -t)
                    update(KeyEvent.KEYCODE_D, d.x > t)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier.offset { IntOffset(knob.x.toInt(), knob.y.toInt()) }
                .size(size / 3).clip(CircleShape).background(Color.White.copy(alpha = 0.6f))
        )
    }
}

@Composable
private fun KeyPanel(client: RdpClient, modifier: Modifier) {
    val ctx = LocalContext.current
    val keys = listOf(
        "Win" to KeyEvent.KEYCODE_META_LEFT,
        "Esc" to KeyEvent.KEYCODE_ESCAPE,
        "Tab" to KeyEvent.KEYCODE_TAB,
        "Ctrl" to KeyEvent.KEYCODE_CTRL_LEFT,
        "Alt" to KeyEvent.KEYCODE_ALT_LEFT,
        "Shift" to KeyEvent.KEYCODE_SHIFT_LEFT,
        "Del" to KeyEvent.KEYCODE_FORWARD_DEL,
        "Enter" to KeyEvent.KEYCODE_ENTER,
        "Back" to KeyEvent.KEYCODE_DEL,
        "←" to KeyEvent.KEYCODE_DPAD_LEFT,
        "↑" to KeyEvent.KEYCODE_DPAD_UP,
        "↓" to KeyEvent.KEYCODE_DPAD_DOWN,
        "→" to KeyEvent.KEYCODE_DPAD_RIGHT
    )
    val combos = listOf(
        "Ctrl+C" to KeyEvent.KEYCODE_C,
        "Ctrl+V" to KeyEvent.KEYCODE_V,
        "Ctrl+X" to KeyEvent.KEYCODE_X,
        "Ctrl+Z" to KeyEvent.KEYCODE_Z,
        "Ctrl+Y" to KeyEvent.KEYCODE_Y,
        "Ctrl+A" to KeyEvent.KEYCODE_A
    )
    val modifiers = listOf(
        KeyEvent.KEYCODE_CTRL_LEFT,
        KeyEvent.KEYCODE_ALT_LEFT,
        KeyEvent.KEYCODE_SHIFT_LEFT
    )
    val held = remember { mutableStateListOf<Int>() }

    Surface(modifier.fillMaxWidth(), color = Color.Black.copy(alpha = 0.7f)) {
        LazyRow(Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            item {
                AssistChip(
                    {
                        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.primaryClip?.getItemAt(0)?.text?.let { client.pasteText(it.toString()) }
                    },
                    { Text("Paste clipboard") }
                )
            }
            items(combos) { (n, code) ->
                AssistChip(
                    {
                        client.key(KeyEvent.KEYCODE_CTRL_LEFT, true)
                        client.key(code, true)
                        client.key(code, false)
                        client.key(KeyEvent.KEYCODE_CTRL_LEFT, false)
                    },
                    { Text(n) }
                )
            }
            items(keys) { (n, code) ->
                FilterChip(
                    code in held,
                    {
                        if (code in modifiers) {
                            if (code in held) {
                                held.remove(code)
                                client.key(code, false)
                            } else {
                                held.add(code)
                                client.key(code, true)
                            }
                        } else {
                            client.key(code, true)
                            client.key(code, false)
                        }
                    },
                    { Text(n) }
                )
            }
        }
    }
}
