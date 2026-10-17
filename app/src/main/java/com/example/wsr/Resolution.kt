package com.example.wsr

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

object Res {
    const val MIN_W = 640
    const val STEP = 160
    fun h(w: Int) = w * 9 / 16
    fun snapMax(w: Int) = MIN_W + ((w - MIN_W).coerceAtLeast(0) / STEP) * STEP
}

@Composable
fun ResolutionRow(w: Int, maxW: Int, onChange: (Int) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val top = Res.snapMax(maxW).coerceAtLeast(Res.MIN_W + Res.STEP)
    val cur = w.coerceIn(Res.MIN_W, top)
    var tmp by remember(cur) { mutableFloatStateOf(cur.toFloat()) }

    fun snap(v: Float): Int = Res.MIN_W + Math.round((v - Res.MIN_W) / Res.STEP) * Res.STEP

    val shown = snap(tmp)
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.AspectRatio, null, tint = cs.primary)
            Spacer(Modifier.width(16.dp))
            Text("Screen resolution", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Text(
                "$shown × ${Res.h(shown)}",
                style = MaterialTheme.typography.labelLarge,
                color = cs.onSurfaceVariant
            )
        }
        Slider(
            value = tmp,
            onValueChange = { tmp = it },
            valueRange = Res.MIN_W.toFloat()..top.toFloat(),
            steps = (top - Res.MIN_W) / Res.STEP - 1,
            onValueChangeFinished = { onChange(snap(tmp)) }
        )
        Text(
            "Min ${Res.MIN_W} × ${Res.h(Res.MIN_W)} · Max $top × ${Res.h(top)} (server limit)",
            style = MaterialTheme.typography.labelSmall,
            color = cs.onSurfaceVariant
        )
    }
}
