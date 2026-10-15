package com.example.wsr

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerForm(initial: Server?, onBack: () -> Unit, onSave: (Server) -> Unit) {
    var s by remember { mutableStateOf(initial ?: Server()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (initial == null) "New server" else "Edit server") },
                navigationIcon = { TextButton(onBack) { Text("Cancel") } },
                actions = { TextButton({ onSave(s) }, enabled = s.host.isNotBlank()) { Text("Save") } }
            )
        }
    ) { pad ->
        Column(
            Modifier.padding(pad).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                s.host, { s = s.copy(host = it) },
                label = { Text("Computer name") },
                placeholder = { Text("HostName or IP of server") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Text("User account", style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(
                s.user, { s = s.copy(user = it) },
                label = { Text("Username") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                s.password, { s = s.copy(password = it) },
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                s.friendly, { s = s.copy(friendly = it) },
                label = { Text("Friendly name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            SwitchRow("Administration mode", s.admin) { s = s.copy(admin = it) }
            SwitchRow("Switching mouse buttons", s.swapMouse) { s = s.copy(swapMouse = it) }
            Text("Gateway", style = MaterialTheme.typography.titleSmall)
            SwitchRow("Do not use any local gateway", s.gateway.isEmpty()) {
                s = s.copy(gateway = if (it) "" else " ")
            }
            if (s.gateway.isNotEmpty()) {
                OutlinedTextField(
                    s.gateway.trim(), { s = s.copy(gateway = it.ifEmpty { " " }) },
                    label = { Text("Gateway address") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Text("Audio", style = MaterialTheme.typography.titleSmall)
            listOf(
                "Produce audio on your device",
                "Produce audio on the remote PC",
                "Do not produce audio"
            ).forEachIndexed { i, t ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(s.audio == i, { s = s.copy(audio = i) })
                    Text(t)
                }
            }
            SwitchRow("Microphone", s.mic) { s = s.copy(mic = it) }
            SwitchRow("Camera", s.camera) { s = s.copy(camera = it) }
            SwitchRow("Storage", s.storage) { s = s.copy(storage = it) }
            SwitchRow("Clipboard", s.clipboard) { s = s.copy(clipboard = it) }
        }
    }
}
