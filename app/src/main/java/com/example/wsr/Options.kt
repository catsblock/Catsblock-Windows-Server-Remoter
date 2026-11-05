package com.example.wsr

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

private val AudioModes = listOf(
    "Play on this device",
    "Play on the remote PC",
    "Mute"
)

@Composable
fun AudioOptions(selected: Int, onSelect: (Int) -> Unit) {
    AudioModes.forEachIndexed { i, t ->
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp)
                .selectable(selected = selected == i, role = Role.RadioButton, onClick = { onSelect(i) })
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected == i, null)
            Spacer(Modifier.width(16.dp))
            Text(t, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
fun SessionOptions(s: Server, onChange: (Server) -> Unit) {
    SectionTitle("Screen")
    SettingsGroup {
        ResolutionRow(s.resW, s.maxW) { onChange(s.copy(resW = it, resH = Res.h(it))) }
    }
    SectionTitle("Audio")
    SettingsGroup {
        AudioOptions(s.audio) { onChange(s.copy(audio = it)) }
    }
    SectionTitle("Redirection")
    SettingsGroup {
        SettingSwitch(Icons.Rounded.Mic, "Microphone", s.mic) { onChange(s.copy(mic = it)) }
        SettingSwitch(Icons.Rounded.Folder, "Storage", s.storage) { onChange(s.copy(storage = it)) }
        SettingSwitch(Icons.Rounded.ContentPaste, "Clipboard", s.clipboard) { onChange(s.copy(clipboard = it)) }
    }
    SectionTitle("Mouse")
    SettingsGroup {
        SettingSwitch(
            Icons.Rounded.SwapHoriz, "Swap mouse buttons", s.swapMouse,
            "Left and right buttons trade roles"
        ) { onChange(s.copy(swapMouse = it)) }
    }
}
