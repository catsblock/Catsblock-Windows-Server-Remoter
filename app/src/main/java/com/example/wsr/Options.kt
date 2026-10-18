package com.example.wsr

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.unit.dp

@Composable
fun SessionOptions(s: Server, onChange: (Server) -> Unit) {
    SectionTitle("Screen")
    SettingsGroup {
        ResolutionRow(s.resW, s.maxW) { onChange(s.copy(resW = it, resH = Res.h(it))) }
    }
    SectionTitle("Audio")
    SettingsGroup {
        listOf(
            "Produce audio on your device",
            "Produce audio on the remote PC",
            "Do not produce audio"
        ).forEachIndexed { i, t ->
            Row(
                Modifier.fillMaxWidth().clickable { onChange(s.copy(audio = i)) }
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(s.audio == i, null)
                Spacer(Modifier.width(12.dp))
                Text(t, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
    SectionTitle("Redirection")
    SettingsGroup {
        SettingSwitch(Icons.Rounded.Mic, "Microphone", s.mic) { onChange(s.copy(mic = it)) }
        SettingSwitch(Icons.Rounded.Folder, "Storage", s.storage) { onChange(s.copy(storage = it)) }
        SettingSwitch(Icons.Rounded.ContentPaste, "Clipboard", s.clipboard) { onChange(s.copy(clipboard = it)) }
    }
    SectionTitle("Mouse")
    SettingsGroup {
        SettingSwitch(Icons.Rounded.SwapHoriz, "Switching mouse buttons", s.swapMouse) { onChange(s.copy(swapMouse = it)) }
    }
}
