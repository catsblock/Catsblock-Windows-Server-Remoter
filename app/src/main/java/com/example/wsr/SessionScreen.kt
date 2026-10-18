package com.example.wsr

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

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
    onUpdate: (Server) -> Unit,
    onExit: () -> Unit,
    onSwitch: (Server) -> Unit
) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    DisposableEffect(Unit) {
        val a = ctx as Activity
        a.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose { a.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }

    val storageDir = remember {
        (ctx.getExternalFilesDir(null) ?: ctx.filesDir).resolve("Shared").also { it.mkdirs() }.absolutePath
    }
    val client = remember(server.id) { FreeRdpClient().also { it.storageDir = storageDir } }
    var cur by remember(server.id) { mutableStateOf(server) }
    var state by remember(server.id) { mutableStateOf<State>(State.Connecting) }
    var log by remember(server.id) { mutableStateOf("Starting connection...") }
    var frame by remember(server.id) { mutableStateOf<Bitmap?>(null) }
    var cert by remember(server.id) { mutableStateOf<Pair<String, (Boolean) -> Unit>?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    var showKeys by remember { mutableStateOf(false) }
    var showPhoneKb by remember { mutableStateOf(false) }
    var showServers by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var gw by remember(server.id) { mutableIntStateOf(server.resW) }
    var gh by remember(server.id) { mutableIntStateOf(server.resH) }
    var restartMsg by remember(server.id) { mutableStateOf<String?>(null) }
    var cx by remember(server.id) { mutableIntStateOf(server.resW / 2) }
    var cy by remember(server.id) { mutableIntStateOf(server.resH / 2) }
    var srvCur by remember(server.id) { mutableStateOf<CurFrame?>(null) }
    var step by remember { mutableIntStateOf(0) }
    val custom = Extra.custom
    val connected = state is State.Connected

    LaunchedEffect(custom) {
        step = 0
        val a = custom
        if (a != null && a.steps.size > 1) {
            while (true) {
                delay(a.steps[step % a.steps.size].ms.toLong())
                step++
            }
        }
    }

    LaunchedEffect(cur) { onUpdate(cur) }

    val hasMic = ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED
    var micGranted by remember(server.id) { mutableStateOf(hasMic) }
    var ready by remember(server.id) { mutableStateOf(!server.mic || hasMic) }
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        micGranted = it
        ready = true
    }
    LaunchedEffect(server.id) {
        if (!ready) micLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    val onMove: (Float, Float) -> Unit = { dx, dy ->
        val ix = dx.toInt()
        val iy = dy.toInt()
        if (ix != 0 || iy != 0) {
            cx = (cx + ix).coerceIn(0, gw - 1)
            cy = (cy + iy).coerceIn(0, gh - 1)
            client.mouseMove(ix.toFloat(), iy.toFloat())
        }
    }

    val applyChange: (Server) -> Unit = { n ->
        val conn = n.resW != cur.resW || n.audio != cur.audio || n.mic != cur.mic ||
            n.storage != cur.storage || n.clipboard != cur.clipboard
        if (conn) {
            restartMsg = if (n.resW != cur.resW) "Changing the server screen resolution, please wait ..."
            else "Applying the new settings, please wait ..."
            showSettings = false
        }
        if (n.mic && !cur.mic && !micGranted) micLauncher.launch(Manifest.permission.RECORD_AUDIO)
        cur = n
    }

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

    DisposableEffect(
        server.id, retry, ready, cur.resW, cur.resH, cur.audio, cur.mic, cur.storage, cur.clipboard, micGranted
    ) {
        if (!ready) return@DisposableEffect onDispose { }
        state = State.Connecting
        log = "Starting connection..."
        cx = cur.resW / 2
        cy = cur.resH / 2
        client.connect(
            cur.copy(mic = cur.mic && micGranted),
            object : RdpListener {
                override fun onLog(msg: String) { log = msg }
                override fun onCertificate(fingerprint: String, accept: (Boolean) -> Unit) {
                    cert = fingerprint to accept
                }
                override fun onError(code: String) {
                    restartMsg = null
                    state = State.Error(code)
                }
                override fun onConnected(osName: String) {
                    restartMsg = null
                    state = State.Connected
                }
                override fun onFrame(bmp: Bitmap) { frame = bmp }
                override fun onClipboard(text: String) {
                    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("rdp", text))
                }
                override fun onResolution(w: Int, h: Int) {
                    gw = w
                    gh = h
                    cx = w / 2
                    cy = h / 2
                    if (w < cur.resW || h < cur.resH) {
                        val nm = Res.snapMax(w).coerceAtLeast(Res.MIN_W)
                        cur = cur.copy(maxW = nm, maxH = Res.h(nm))
                    }
                }
                override fun onOs(name: String) {
                    cur = cur.copy(os = name)
                }
                override fun onCursor(bmp: Bitmap?, hx: Int, hy: Int) {
                    srvCur = if (bmp == null) null else CurFrame(bmp, hx, hy, 0).also { Extra.addSeen(it) }
                }
            }
        )
        onDispose { client.disconnect() }
    }

    val padKeys = remember { mutableSetOf<Int>() }
    val stick = remember { floatArrayOf(0f, 0f) }
    val trig = remember { booleanArrayOf(false, false) }

    DisposableEffect(Prefs.controller, connected, client) {
        if (!Prefs.controller || !connected) return@DisposableEffect onDispose { }
        fun padKey(code: Int, on: Boolean) {
            if (on && padKeys.add(code)) client.key(code, true)
            if (!on && padKeys.remove(code)) client.key(code, false)
        }
        val left = if (cur.swapMouse) 1 else 0
        val right = if (cur.swapMouse) 0 else 1
        Gamepad.motion = motion@{ ev ->
            val joy = (ev.source and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
            if (!joy || ev.action != MotionEvent.ACTION_MOVE) return@motion false
            val lx = ev.getAxisValue(MotionEvent.AXIS_X)
            val ly = ev.getAxisValue(MotionEvent.AXIS_Y)
            padKey(KeyEvent.KEYCODE_W, ly < -0.5f)
            padKey(KeyEvent.KEYCODE_S, ly > 0.5f)
            padKey(KeyEvent.KEYCODE_A, lx < -0.5f)
            padKey(KeyEvent.KEYCODE_D, lx > 0.5f)
            var rx = ev.getAxisValue(MotionEvent.AXIS_Z)
            var ry = ev.getAxisValue(MotionEvent.AXIS_RZ)
            if (abs(rx) < 0.01f && abs(ry) < 0.01f) {
                rx = ev.getAxisValue(MotionEvent.AXIS_RX)
                ry = ev.getAxisValue(MotionEvent.AXIS_RY)
            }
            stick[0] = if (abs(rx) > 0.2f) rx else 0f
            stick[1] = if (abs(ry) > 0.2f) ry else 0f
            val lt = max(ev.getAxisValue(MotionEvent.AXIS_LTRIGGER), ev.getAxisValue(MotionEvent.AXIS_BRAKE)) > 0.5f
            val rt = max(ev.getAxisValue(MotionEvent.AXIS_RTRIGGER), ev.getAxisValue(MotionEvent.AXIS_GAS)) > 0.5f
            if (rt != trig[1]) {
                trig[1] = rt
                client.mouseButton(left, rt)
            }
            if (lt != trig[0]) {
                trig[0] = lt
                client.mouseButton(right, lt)
            }
            val hx = ev.getAxisValue(MotionEvent.AXIS_HAT_X)
            val hy = ev.getAxisValue(MotionEvent.AXIS_HAT_Y)
            padKey(KeyEvent.KEYCODE_DPAD_LEFT, hx < -0.5f)
            padKey(KeyEvent.KEYCODE_DPAD_RIGHT, hx > 0.5f)
            padKey(KeyEvent.KEYCODE_DPAD_UP, hy < -0.5f)
            padKey(KeyEvent.KEYCODE_DPAD_DOWN, hy > 0.5f)
            true
        }
        Gamepad.key = keyH@{ ev ->
            val pad = (ev.source and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
                (ev.source and InputDevice.SOURCE_DPAD) == InputDevice.SOURCE_DPAD
            if (!pad) return@keyH false
            val code = when (ev.keyCode) {
                KeyEvent.KEYCODE_BUTTON_A -> KeyEvent.KEYCODE_SPACE
                KeyEvent.KEYCODE_BUTTON_B -> KeyEvent.KEYCODE_CTRL_LEFT
                KeyEvent.KEYCODE_BUTTON_X -> KeyEvent.KEYCODE_E
                KeyEvent.KEYCODE_BUTTON_Y -> KeyEvent.KEYCODE_R
                KeyEvent.KEYCODE_BUTTON_L1 -> KeyEvent.KEYCODE_Q
                KeyEvent.KEYCODE_BUTTON_R1 -> KeyEvent.KEYCODE_F
                KeyEvent.KEYCODE_BUTTON_THUMBL -> KeyEvent.KEYCODE_SHIFT_LEFT
                KeyEvent.KEYCODE_BUTTON_THUMBR -> KeyEvent.KEYCODE_G
                KeyEvent.KEYCODE_BUTTON_START -> KeyEvent.KEYCODE_ESCAPE
                KeyEvent.KEYCODE_BUTTON_SELECT -> KeyEvent.KEYCODE_TAB
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> ev.keyCode
                else -> return@keyH false
            }
            if (ev.action == KeyEvent.ACTION_DOWN) {
                if (ev.repeatCount == 0) padKey(code, true)
            } else if (ev.action == KeyEvent.ACTION_UP) {
                padKey(code, false)
            }
            true
        }
        onDispose {
            Gamepad.motion = null
            Gamepad.key = null
            padKeys.toList().forEach { client.key(it, false) }
            padKeys.clear()
            stick[0] = 0f
            stick[1] = 0f
        }
    }

    LaunchedEffect(Prefs.controller, connected) {
        if (Prefs.controller && connected) {
            while (true) {
                if (stick[0] != 0f || stick[1] != 0f) onMove(stick[0] * 14f, stick[1] * 14f)
                delay(16)
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when (val st = state) {
            State.Connecting -> ConnectingScreen(
                title = if (restartMsg != null) "Restarting ..." else "Connecting to the server ...",
                status = restartMsg ?: log,
                button = if (restartMsg != null) "Cancel" else "Back to main menu",
                cancel = restartMsg != null
            ) {
                client.disconnect()
                onExit()
            }
            is State.Error -> ErrorScreen(st.code, onHome = { onExit() }, onRetry = { retry++ })
            State.Connected -> {
                Box(
                    Modifier.fillMaxSize()
                        .pointerInput(server.id, cur.swapMouse, Prefs.mouseButtons) {
                            touchpad(client, cur.swapMouse, Prefs.mouseButtons, onMove)
                        }
                ) {
                    Box(Modifier.fillMaxSize().graphicsLayer(scaleX = zoom, scaleY = zoom)) {
                        frame?.let {
                            Image(
                                it.asImageBitmap(), null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Canvas(Modifier.fillMaxSize()) {
                            val sc = minOf(size.width / gw.toFloat(), size.height / gh.toFloat())
                            val ox = (size.width - gw * sc) / 2f
                            val oy = (size.height - gh * sc) / 2f
                            val px = ox + cx * sc
                            val py = oy + cy * sc
                            val a = custom
                            val f = if (a != null && a.steps.isNotEmpty()) a.steps[step % a.steps.size] else srvCur
                            if (f != null) {
                                val s = (if (a != null) density else sc) * Prefs.cursorSize
                                drawImage(
                                    f.bmp.asImageBitmap(),
                                    dstOffset = IntOffset((px - f.hx * s).roundToInt(), (py - f.hy * s).roundToInt()),
                                    dstSize = IntSize(
                                        (f.bmp.width * s).roundToInt().coerceAtLeast(1),
                                        (f.bmp.height * s).roundToInt().coerceAtLeast(1)
                                    ),
                                    filterQuality = FilterQuality.Low
                                )
                            } else {
                                val k = density * 1.2f * Prefs.cursorSize
                                val p = Path().apply {
                                    moveTo(px, py)
                                    lineTo(px, py + 18f * k)
                                    lineTo(px + 5f * k, py + 14f * k)
                                    lineTo(px + 9f * k, py + 22f * k)
                                    lineTo(px + 12f * k, py + 21f * k)
                                    lineTo(px + 8f * k, py + 13f * k)
                                    lineTo(px + 14f * k, py + 13f * k)
                                    close()
                                }
                                drawPath(p, Color.White)
                                drawPath(p, Color.Black, style = Stroke(width = 1.5f * density * Prefs.cursorSize))
                            }
                        }
                    }
                }

                Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                    if (Extra.fpsShower || Extra.pingShower) {
                        StatsOverlay(client, cur.host, Modifier.align(Alignment.TopStart).padding(4.dp))
                    }
                    if (Prefs.joystick) {
                        Joystick(client, Modifier.align(Alignment.BottomStart).padding(16.dp))
                    }
                    if (Prefs.rightJoystick) {
                        RightJoystick(
                            onMove,
                            Modifier.align(Alignment.BottomEnd)
                                .padding(end = if (Prefs.mouseButtons) 200.dp else 140.dp, bottom = 16.dp)
                        )
                    }
                    if (Prefs.mainKeys) {
                        Row(
                            Modifier.align(Alignment.BottomCenter)
                                .padding(bottom = if (showKeys) 84.dp else 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            KeyHold("Q", KeyEvent.KEYCODE_Q, client)
                            KeyHold("E", KeyEvent.KEYCODE_E, client)
                            KeyHold("R", KeyEvent.KEYCODE_R, client)
                            KeyHold("F", KeyEvent.KEYCODE_F, client)
                            KeyHold("G", KeyEvent.KEYCODE_G, client)
                        }
                    }
                    if (Prefs.scrollButtons) {
                        Column(
                            Modifier.align(Alignment.CenterEnd).padding(end = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            ScrollKey(Icons.Rounded.KeyboardArrowUp, 1, client)
                            ScrollKey(Icons.Rounded.KeyboardArrowDown, -1, client)
                        }
                    }
                    if (Prefs.mouseButtons) {
                        val l = if (cur.swapMouse) 1 else 0
                        val r = if (cur.swapMouse) 0 else 1
                        Row(
                            Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 72.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            MouseKey("Left", l, client, cs.primaryContainer, cs.onPrimaryContainer)
                            MouseKey("Right", r, client, cs.secondaryContainer, cs.onSecondaryContainer)
                        }
                    }
                    if (Prefs.space) {
                        Box(
                            Modifier.align(Alignment.BottomEnd).padding(16.dp)
                                .size(width = 110.dp, height = 44.dp)
                                .clip(RoundedCornerShape(22.dp))
                                .background(cs.secondaryContainer.copy(alpha = 0.85f))
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
                            Text(
                                "Space",
                                color = cs.onSecondaryContainer,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Surface(
                        Modifier.align(Alignment.TopCenter).padding(top = 4.dp),
                        shape = CircleShape,
                        color = cs.surfaceContainerHigh.copy(alpha = Prefs.barAlpha.coerceIn(0.1f, 1f))
                    ) {
                        Row(
                            Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            BarIcon(Icons.Rounded.GridView) { showServers = true }
                            BarIcon(Icons.Rounded.ZoomIn) { zoom = if (zoom >= 2f) 1f else zoom + 0.5f }
                            BarIcon(Icons.Rounded.Settings) { showSettings = true }
                            BarIcon(Icons.Rounded.Keyboard) {
                                showKeys = !showKeys
                                if (!showKeys) showPhoneKb = false
                            }
                        }
                    }

                    if (showKeys) {
                        KeyPanel(
                            client,
                            showPhoneKb,
                            { showPhoneKb = !showPhoneKb },
                            Modifier.align(Alignment.BottomCenter)
                        )
                    }
                    if (showPhoneKb) PhoneKeyboard(client)
                }
            }
        }
    }

    cert?.let { (fp, cb) ->
        AlertDialog(
            onDismissRequest = {},
            icon = { Icon(Icons.Rounded.Warning, null) },
            title = { Text("Unverified server") },
            text = { Text("This server could not be verified.\nFingerprint: $fp\nConnect anyway?") },
            confirmButton = { TextButton({ cb(true); cert = null }) { Text("Connect") } },
            dismissButton = { TextButton({ cb(false); cert = null }) { Text("Cancel") } }
        )
    }

    if (showServers) {
        AlertDialog(
            onDismissRequest = { showServers = false },
            icon = { Icon(Icons.Rounded.GridView, null) },
            title = { Text("Switch server") },
            text = {
                Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    all.forEach {
                        val current = it.id == server.id
                        Row(
                            Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large)
                                .background(if (current) cs.secondaryContainer else Color.Transparent)
                                .clickable { showServers = false; onSwitch(it) }
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.Computer, null, tint = cs.primary)
                            Spacer(Modifier.width(12.dp))
                            Text(it.title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                            if (current) Icon(Icons.Rounded.Check, null, tint = cs.primary)
                        }
                    }
                }
            },
            confirmButton = { TextButton({ showServers = false }) { Text("Close") } }
        )
    }

    if (showSettings) {
        AlertDialog(
            onDismissRequest = { showSettings = false },
            icon = { Icon(Icons.Rounded.Settings, null) },
            title = { Text("Settings") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    SessionOptions(cur, applyChange)
                    SettingsPage(inDialog = true)
                }
            },
            confirmButton = { TextButton({ showSettings = false }) { Text("Close") } }
        )
    }
}

@Composable
private fun ExpressiveLoader(modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val t = rememberInfiniteTransition(label = "loader")
    val rot by t.animateFloat(
        0f, 360f,
        infiniteRepeatable(tween(4000, easing = LinearEasing)),
        label = "rot"
    )
    val morph by t.animateFloat(
        0.26f, 0.5f,
        infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "morph"
    )
    val pulse by t.animateFloat(
        0.82f, 1f,
        infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    )
    Box(modifier.size(88.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier.fillMaxSize()
                .graphicsLayer { rotationZ = rot; scaleX = pulse; scaleY = pulse }
                .clip(RoundedCornerShape((morph * 100).toInt()))
                .background(cs.primary)
        )
        Box(
            Modifier.size(40.dp)
                .graphicsLayer { rotationZ = -rot * 1.5f; scaleX = 1.9f - pulse; scaleY = 1.9f - pulse }
                .clip(RoundedCornerShape(((0.76f - morph) * 100).toInt()))
                .background(cs.primaryContainer)
        )
    }
}

@Composable
private fun ConnectingScreen(
    title: String,
    status: String,
    button: String,
    cancel: Boolean,
    onBack: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(cs.primaryContainer.copy(alpha = 0.7f), cs.background))
        )
    ) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = cs.onSurface,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(20.dp))
            ExpressiveLoader()
            Spacer(Modifier.height(20.dp))
            Surface(shape = MaterialTheme.shapes.large, color = cs.surfaceContainerHigh) {
                Text(
                    status,
                    Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    color = cs.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
            Spacer(Modifier.height(16.dp))
            FilledTonalButton(onBack, Modifier.height(48.dp)) {
                Icon(if (cancel) Icons.Rounded.Close else Icons.Rounded.Home, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(button)
            }
        }
    }
}

@Composable
private fun ErrorScreen(code: String, onHome: () -> Unit, onRetry: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(cs.errorContainer.copy(alpha = 0.7f), cs.background))
        )
    ) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Rounded.ErrorOutline, null, Modifier.size(48.dp), tint = cs.error)
            Spacer(Modifier.height(12.dp))
            Text(
                "The connection was interrupted. Error: $code",
                style = MaterialTheme.typography.bodyLarge,
                color = cs.onSurface,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onHome, Modifier.height(48.dp)) { Text("Home page") }
                Button(onRetry, Modifier.height(48.dp)) { Text("Try Again") }
            }
        }
    }
}

@Composable
private fun BarIcon(icon: ImageVector, onClick: () -> Unit) {
    IconButton(onClick, Modifier.size(34.dp)) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(19.dp))
    }
}

@Composable
private fun MouseKey(label: String, button: Int, client: RdpClient, container: Color, content: Color) {
    Box(
        Modifier.size(width = 76.dp, height = 48.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(container.copy(alpha = 0.85f))
            .pointerInput(button) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    client.mouseButton(button, true)
                    waitForUpOrCancellation()
                    client.mouseButton(button, false)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = content, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun KeyHold(label: String, code: Int, client: RdpClient) {
    val cs = MaterialTheme.colorScheme
    Box(
        Modifier.size(46.dp).clip(CircleShape)
            .background(cs.tertiaryContainer.copy(alpha = 0.85f))
            .pointerInput(code) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    client.key(code, true)
                    waitForUpOrCancellation()
                    client.key(code, false)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = cs.onTertiaryContainer, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ScrollKey(icon: ImageVector, dir: Int, client: RdpClient) {
    val cs = MaterialTheme.colorScheme
    Box(
        Modifier.size(48.dp).clip(CircleShape).background(cs.surfaceContainerHigh.copy(alpha = 0.75f))
            .pointerInput(dir) {
                detectTapGestures(onPress = {
                    client.scroll(dir)
                    coroutineScope {
                        val j = launch {
                            delay(350)
                            while (true) {
                                client.scroll(dir)
                                delay(70)
                            }
                        }
                        awaitRelease()
                        j.cancel()
                    }
                })
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = cs.onSurface)
    }
}

private suspend fun PointerInputScope.touchpad(
    client: RdpClient,
    swap: Boolean,
    buttonsMode: Boolean,
    onMove: (Float, Float) -> Unit
) {
    val left = if (swap) 1 else 0
    val right = if (swap) 0 else 1
    var taps = 0
    var job: Job? = null
    coroutineScope {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)

            if (buttonsMode) {
                var p = down.position
                while (true) {
                    val c = awaitPointerEvent().changes.first()
                    if (!c.pressed) break
                    val d = (c.position - p) * Prefs.sensitivity
                    onMove(d.x, d.y)
                    p = c.position
                    c.consume()
                }
                return@awaitEachGesture
            }

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
                    onMove(d.x, d.y)
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
    val cs = MaterialTheme.colorScheme
    val size = Prefs.joySize.dp
    var knob by remember { mutableStateOf(Offset.Zero) }
    val active = remember { mutableSetOf<Int>() }

    fun update(code: Int, on: Boolean) {
        if (on && active.add(code)) client.key(code, true)
        if (!on && active.remove(code)) client.key(code, false)
    }

    Box(
        modifier.size(size).clip(CircleShape).background(cs.surfaceContainerHigh.copy(alpha = 0.55f))
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
                .size(size / 3).clip(CircleShape).background(cs.primary.copy(alpha = 0.9f))
        )
    }
}

@Composable
private fun RightJoystick(onMove: (Float, Float) -> Unit, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    val size = Prefs.joySize.dp
    var knob by remember { mutableStateOf(Offset.Zero) }
    var active by remember { mutableStateOf(false) }

    LaunchedEffect(active) {
        while (active) {
            onMove(knob.x * 0.12f * Prefs.sensitivity, knob.y * 0.12f * Prefs.sensitivity)
            delay(16)
        }
    }

    Box(
        modifier.size(size).clip(CircleShape).background(cs.surfaceContainerHigh.copy(alpha = 0.55f))
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { active = true },
                    onDragEnd = { active = false; knob = Offset.Zero },
                    onDragCancel = { active = false; knob = Offset.Zero }
                ) { change, _ ->
                    val c = Offset(this.size.width / 2f, this.size.height / 2f)
                    val d = change.position - c
                    val t = this.size.width / 3f
                    knob = Offset(d.x.coerceIn(-t, t), d.y.coerceIn(-t, t))
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier.offset { IntOffset(knob.x.toInt(), knob.y.toInt()) }
                .size(size / 3).clip(CircleShape).background(cs.tertiary.copy(alpha = 0.9f))
        )
    }
}

@Composable
private fun PhoneKeyboard(client: RdpClient) {
    val focus = remember { FocusRequester() }
    val kb = LocalSoftwareKeyboardController.current
    var text by remember { mutableStateOf("") }

    fun tap(code: Int) {
        client.key(code, true)
        client.key(code, false)
    }

    LaunchedEffect(Unit) {
        focus.requestFocus()
        kb?.show()
    }
    DisposableEffect(Unit) {
        onDispose { kb?.hide() }
    }

    BasicTextField(
        value = text,
        onValueChange = { v ->
            if (v.isNotEmpty()) client.pasteText(v)
            text = ""
        },
        modifier = Modifier.size(1.dp).focusRequester(focus).onPreviewKeyEvent { ev ->
            if (ev.type == KeyEventType.KeyDown && ev.key == Key.Backspace) {
                tap(KeyEvent.KEYCODE_DEL)
                true
            } else if (ev.type == KeyEventType.KeyDown && ev.key == Key.Enter) {
                tap(KeyEvent.KEYCODE_ENTER)
                true
            } else {
                false
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
        keyboardActions = KeyboardActions(onSend = { tap(KeyEvent.KEYCODE_ENTER) })
    )
}

@Composable
private fun KeyPanel(
    client: RdpClient,
    phoneOn: Boolean,
    onPhone: () -> Unit,
    modifier: Modifier
) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
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

    Surface(
        modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = cs.surfaceContainer.copy(alpha = 0.95f)
    ) {
        LazyRow(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                FilterChip(phoneOn, onPhone, { Text("Phone keyboard") })
            }
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
