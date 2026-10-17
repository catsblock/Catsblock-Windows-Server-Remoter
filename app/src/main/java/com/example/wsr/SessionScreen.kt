package com.example.wsr

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
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
    val cs = MaterialTheme.colorScheme
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
    var showPhoneKb by remember { mutableStateOf(false) }
    var showServers by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var cx by remember { mutableIntStateOf(640) }
    var cy by remember { mutableIntStateOf(360) }

    val onMove: (Float, Float) -> Unit = { dx, dy ->
        val ix = dx.toInt()
        val iy = dy.toInt()
        cx = (cx + ix).coerceIn(0, 1279)
        cy = (cy + iy).coerceIn(0, 719)
        client.mouseMove(ix.toFloat(), iy.toFloat())
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

    DisposableEffect(server.id, retry) {
        state = State.Connecting
        log = "Starting connection..."
        cx = 640
        cy = 360
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
            State.Connecting -> ConnectingScreen(log) {
                client.disconnect()
                onExit()
            }
            is State.Error -> ErrorScreen(st.code, onHome = { onExit() }, onRetry = { retry++ })
            State.Connected -> {
                Box(
                    Modifier.fillMaxSize()
                        .pointerInput(server.id, server.swapMouse, Prefs.mouseButtons) {
                            touchpad(client, server.swapMouse, Prefs.mouseButtons, onMove)
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
                            val sc = minOf(size.width / 1280f, size.height / 720f)
                            val ox = (size.width - 1280f * sc) / 2f
                            val oy = (size.height - 720f * sc) / 2f
                            val px = ox + cx * sc
                            val py = oy + cy * sc
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

                Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                    if (Prefs.joystick) {
                        Joystick(client, Modifier.align(Alignment.BottomStart).padding(16.dp))
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
                        val l = if (server.swapMouse) 1 else 0
                        val r = if (server.swapMouse) 0 else 1
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
            text = { SettingsPage(inDialog = true) },
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
private fun ConnectingScreen(status: String, onBack: () -> Unit) {
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
                "Connecting to the server ...",
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
                Icon(Icons.Rounded.Home, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Back to main menu")
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
