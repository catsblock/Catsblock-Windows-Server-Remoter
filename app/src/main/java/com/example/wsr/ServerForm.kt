package com.example.wsr

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerForm(initial: Server?, onBack: () -> Unit, onSave: (Server) -> Unit) {
    var s by remember { mutableStateOf(initial ?: Server()) }
    var showPass by remember { mutableStateOf(false) }
    val cs = MaterialTheme.colorScheme
    val fieldShape = RoundedCornerShape(16.dp)

    Scaffold(
        containerColor = cs.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (initial == null) "New server" else "Edit server",
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Cancel") }
                },
                actions = {
                    FilledTonalButton({ onSave(s) }, enabled = s.host.isNotBlank()) {
                        Icon(Icons.Rounded.Check, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Save")
                    }
                    Spacer(Modifier.width(8.dp))
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.background)
            )
        }
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SectionTitle("Server")
            OutlinedTextField(
                value = s.host,
                onValueChange = { s = s.copy(host = it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Computer name") },
                placeholder = { Text("HostName or IP of server") },
                leadingIcon = { Icon(Icons.Rounded.Computer, null) },
                singleLine = true,
                shape = fieldShape
            )

            SectionTitle("User account")
            OutlinedTextField(
                value = s.user,
                onValueChange = { s = s.copy(user = it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Username") },
                leadingIcon = { Icon(Icons.Rounded.Person, null) },
                singleLine = true,
                shape = fieldShape
            )
            OutlinedTextField(
                value = s.password,
                onValueChange = { s = s.copy(password = it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Password") },
                leadingIcon = { Icon(Icons.Rounded.Lock, null) },
                trailingIcon = {
                    IconButton({ showPass = !showPass }) {
                        Icon(
                            if (showPass) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            "Show password"
                        )
                    }
                },
                visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
                shape = fieldShape
            )

            SectionTitle("Details")
            OutlinedTextField(
                value = s.friendly,
                onValueChange = { s = s.copy(friendly = it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Friendly name") },
                leadingIcon = { Icon(Icons.Rounded.Badge, null) },
                singleLine = true,
                shape = fieldShape
            )
            SettingsGroup {
                SettingSwitch(Icons.Rounded.AdminPanelSettings, "Administration mode", s.admin) {
                    s = s.copy(admin = it)
                }
                SettingSwitch(Icons.Rounded.SwapHoriz, "Switching mouse buttons", s.swapMouse) {
                    s = s.copy(swapMouse = it)
                }
            }

            SectionTitle("Gateway")
            SettingsGroup {
                SettingSwitch(Icons.Rounded.Router, "Do not use any local gateway", s.gateway.isEmpty()) {
                    s = s.copy(gateway = if (it) "" else " ")
                }
                if (s.gateway.isNotEmpty()) {
                    OutlinedTextField(
                        value = s.gateway.trim(),
                        onValueChange = { s = s.copy(gateway = it.ifEmpty { " " }) },
                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                        label = { Text("Gateway address") },
                        singleLine = true,
                        shape = fieldShape
                    )
                }
            }

            SectionTitle("Audio")
            SettingsGroup {
                listOf(
                    "Produce audio on your device",
                    "Produce audio on the remote PC",
                    "Do not produce audio"
                ).forEachIndexed { i, t ->
                    Row(
                        Modifier.fillMaxWidth().clickable { s = s.copy(audio = i) }
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
                SettingSwitch(Icons.Rounded.Mic, "Microphone", s.mic) { s = s.copy(mic = it) }
                SettingSwitch(Icons.Rounded.Videocam, "Camera", s.camera) { s = s.copy(camera = it) }
                SettingSwitch(Icons.Rounded.Folder, "Storage", s.storage) { s = s.copy(storage = it) }
                SettingSwitch(Icons.Rounded.ContentPaste, "Clipboard", s.clipboard) { s = s.copy(clipboard = it) }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
