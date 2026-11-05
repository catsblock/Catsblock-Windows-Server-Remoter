package com.example.wsr

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

private fun hostError(raw: String): String? {
    val h = raw.trim()
    if (h.isEmpty()) return null
    if (h.any { it.isWhitespace() }) return "The address cannot contain spaces."
    val idx = h.lastIndexOf(':')
    if (idx > 0 && h.indexOf(':') == idx) {
        val port = h.substring(idx + 1)
        if (port.isEmpty() || !port.all(Char::isDigit) || port.toIntOrNull()?.let { it in 1..65535 } != true) {
            return "Use a port between 1 and 65535."
        }
    }
    return null
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ServerForm(initial: Server?, onBack: () -> Unit, onSave: (Server) -> Unit) {
    var s by remember { mutableStateOf(initial ?: Server()) }
    var useGateway by remember { mutableStateOf(initial?.gateway?.isNotBlank() == true) }
    var showPass by remember { mutableStateOf(false) }
    val cs = MaterialTheme.colorScheme
    val fieldShape = MaterialTheme.shapes.medium
    val hostProblem = hostError(s.host)
    val canSave = s.host.isNotBlank() && hostProblem == null

    fun submit() {
        if (!canSave) return
        onSave(s.copy(host = s.host.trim(), gateway = if (useGateway) s.gateway.trim() else ""))
    }

    Scaffold(
        containerColor = cs.background,
        topBar = {
            TopAppBar(
                title = { Text(if (initial == null) "New server" else "Edit server") },
                navigationIcon = {
                    IconButton(onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Discard and go back") }
                },
                actions = {
                    Button(onClick = { submit() }, shapes = ButtonDefaults.shapes(), enabled = canSave) {
                        Icon(Icons.Rounded.Check, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Save", maxLines = 1)
                    }
                    Spacer(Modifier.width(16.dp))
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.background)
            )
        }
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .widthIn(max = 640.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SectionTitle("Server")
            OutlinedTextField(
                value = s.host,
                onValueChange = { s = s.copy(host = it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Address") },
                placeholder = { Text("Host name or IP, for example 192.168.1.10") },
                supportingText = { Text(hostProblem ?: "Add :port only if it is not 3389.") },
                isError = hostProblem != null,
                leadingIcon = { Icon(Icons.Rounded.Computer, null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
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
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
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
                            if (showPass) "Hide password" else "Show password"
                        )
                    }
                },
                visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                singleLine = true,
                shape = fieldShape
            )

            SectionTitle("Details")
            OutlinedTextField(
                value = s.friendly,
                onValueChange = { s = s.copy(friendly = it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Display name (optional)") },
                leadingIcon = { Icon(Icons.Rounded.Badge, null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                shape = fieldShape
            )
            SettingsGroup {
                SettingSwitch(
                    Icons.Rounded.AdminPanelSettings, "Administrative session", s.admin,
                    "Connect to the server console session"
                ) { s = s.copy(admin = it) }
                SettingSwitch(
                    Icons.Rounded.SwapHoriz, "Swap mouse buttons", s.swapMouse,
                    "Left and right buttons trade roles"
                ) { s = s.copy(swapMouse = it) }
            }

            SectionTitle("Display")
            SettingsGroup {
                ResolutionRow(s.resW, s.maxW) { s = s.copy(resW = it, resH = Res.h(it)) }
            }

            SectionTitle("Gateway")
            SettingsGroup {
                SettingSwitch(
                    Icons.Rounded.Router, "Use a remote desktop gateway", useGateway,
                    "Leave off to connect directly"
                ) { useGateway = it }
                if (useGateway) {
                    OutlinedTextField(
                        value = s.gateway,
                        onValueChange = { s = s.copy(gateway = it) },
                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                        label = { Text("Gateway address") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                        shape = fieldShape
                    )
                }
            }

            SectionTitle("Audio")
            SettingsGroup {
                AudioOptions(s.audio) { s = s.copy(audio = it) }
            }

            SectionTitle("Redirection")
            SettingsGroup {
                SettingSwitch(Icons.Rounded.Mic, "Microphone", s.mic) { s = s.copy(mic = it) }
                SettingSwitch(Icons.Rounded.Folder, "Storage", s.storage) { s = s.copy(storage = it) }
                SettingSwitch(Icons.Rounded.ContentPaste, "Clipboard", s.clipboard) { s = s.copy(clipboard = it) }
            }
            Spacer(Modifier.size(24.dp))
        }
    }
}
