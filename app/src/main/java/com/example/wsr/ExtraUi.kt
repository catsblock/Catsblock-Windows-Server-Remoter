package com.example.wsr

import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NearMe
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream

private const val MAX_CURSOR_BYTES = 2 * 1024 * 1024

private fun InputStream.readLimited(max: Int): ByteArray? {
    val out = ByteArrayOutputStream()
    val buf = ByteArray(8192)
    var total = 0
    while (true) {
        val n = read(buf)
        if (n < 0) break
        total += n
        if (total > max) return null
        out.write(buf, 0, n)
    }
    return out.toByteArray()
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExtraSettings() {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    var showGallery by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val name = ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
                val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (i >= 0 && c.moveToFirst()) c.getString(i) else null
            } ?: "cursor"
            val bytes = try {
                ctx.contentResolver.openInputStream(uri)?.use { it.readLimited(MAX_CURSOR_BYTES) }
            } catch (_: Exception) {
                null
            }
            if (bytes == null || !Extra.setCursor(ctx, name, bytes)) {
                Toast.makeText(ctx, "Invalid cursor file. Choose a .cur or .ani file under 2 MB.", Toast.LENGTH_LONG).show()
            }
        }
    }

    SectionTitle("Cursor")
    SettingsGroup {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Rounded.NearMe, null, tint = cs.primary)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text("Custom cursor", style = MaterialTheme.typography.bodyLarge)
                Text(
                    if (Extra.curName.isEmpty()) "Using the server's cursors. Supports .cur and .ani files."
                    else Extra.curName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant
                )
            }
        }
        FlowRow(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilledTonalButton({ picker.launch(arrayOf("*/*")) }) { Text("Choose file", maxLines = 1) }
            if (Extra.curName.isNotEmpty()) {
                OutlinedButton({ Extra.clearCursor(ctx) }) { Text("Remove", maxLines = 1) }
            }
            OutlinedButton({ showGallery = true }) {
                Icon(Icons.Rounded.Visibility, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Preview", maxLines = 1)
            }
        }
    }
    SectionTitle("Session overlay")
    SettingsGroup {
        SettingSwitch(Icons.Rounded.Speed, "Show FPS", Extra.fpsShower) {
            Extra.fpsShower = it; Extra.save()
        }
        SettingSwitch(Icons.Rounded.NetworkCheck, "Show ping", Extra.pingShower) {
            Extra.pingShower = it; Extra.save()
        }
    }
    if (showGallery) CursorGallery { showGallery = false }
}

@Composable
private fun Tile(f: CurFrame) {
    Box(
        Modifier.size(72.dp).clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center
    ) {
        Image(
            f.bmp.asImageBitmap(), null,
            Modifier.size(56.dp),
            filterQuality = FilterQuality.None
        )
    }
}

@Composable
fun CursorGallery(onClose: () -> Unit) {
    val custom = Extra.custom?.images ?: emptyList()
    val seen = Extra.seen.toList()
    AlertDialog(
        onDismissRequest = onClose,
        icon = { Icon(Icons.Rounded.Visibility, null) },
        title = { Text("Cursor preview") },
        text = {
            LazyVerticalGrid(
                GridCells.Adaptive(72.dp),
                Modifier.heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (custom.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) { SectionTitle("Your file (${custom.size})") }
                    items(custom) { Tile(it) }
                }
                item(span = { GridItemSpan(maxLineSpan) }) { SectionTitle("From the server (${seen.size})") }
                if (seen.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            "Connect to a server to collect its cursors.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                items(seen) { Tile(it) }
            }
        },
        confirmButton = { TextButton(onClose) { Text("Close") } }
    )
}

@Composable
fun StatsOverlay(client: RdpClient, host: String, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    var fps by remember { mutableIntStateOf(0) }
    var ping by remember { mutableIntStateOf(-1) }
    val hp = host.trim()
    val idx = hp.lastIndexOf(':')
    val hasPort = idx > 0 && hp.substring(idx + 1).let { it.isNotEmpty() && it.all(Char::isDigit) }
    val h = if (hasPort) hp.substring(0, idx) else hp
    val p = if (hasPort) hp.substring(idx + 1).toIntOrNull()?.takeIf { it in 1..65535 } ?: 3389 else 3389

    if (Extra.fpsShower) {
        LaunchedEffect(Unit) {
            while (true) {
                delay(1000)
                fps = client.fps()
            }
        }
    }
    if (Extra.pingShower) {
        LaunchedEffect(h, p) {
            while (true) {
                ping = withContext(Dispatchers.IO) { Ping.measure(h, p) }
                delay(2000)
            }
        }
    }
    Surface(modifier, shape = MaterialTheme.shapes.small, color = cs.surfaceContainerHigh.copy(alpha = 0.8f)) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            if (Extra.fpsShower) Text("FPS: $fps", style = MaterialTheme.typography.labelLarge)
            if (Extra.pingShower) {
                Text(
                    if (ping < 0) "Ping: -- ms" else "Ping: $ping ms",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}
