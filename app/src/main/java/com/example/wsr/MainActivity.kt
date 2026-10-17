package com.example.wsr

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DesktopWindows
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

sealed interface Screen {
    object Tabs : Screen
    class Form(val server: Server?) : Screen
    class Session(val server: Server) : Screen
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Store.init(this)
        setContent { App() }
    }
}

@Composable
fun App() {
    WsrTheme {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            var agreed by remember { mutableStateOf(Store.agreed) }
            var screen by remember { mutableStateOf<Screen>(Screen.Tabs) }
            val servers = remember { mutableStateListOf<Server>().apply { addAll(Store.servers()) } }

            fun persist() = Store.save(servers.toList())

            if (!agreed) {
                Welcome {
                    Store.agreed = true
                    agreed = true
                }
            } else {
                when (val s = screen) {
                    Screen.Tabs -> {
                        MainTabs(
                            servers = servers,
                            onCreate = { screen = Screen.Form(null) },
                            onLaunch = { screen = Screen.Session(it) },
                            onFav = { sv ->
                                val i = servers.indexOfFirst { it.id == sv.id }
                                if (i >= 0) {
                                    servers[i] = sv.copy(favorite = !sv.favorite)
                                    persist()
                                }
                            },
                            onEdit = { screen = Screen.Form(it) },
                            onDelete = {
                                servers.removeAll { x -> x.id == it.id }
                                persist()
                            }
                        )
                        UpdateHost()
                    }
                    is Screen.Form -> {
                        BackHandler { screen = Screen.Tabs }
                        ServerForm(s.server, { screen = Screen.Tabs }) { n ->
                            val i = servers.indexOfFirst { it.id == n.id }
                            if (i >= 0) servers[i] = n else servers.add(n)
                            persist()
                            screen = Screen.Tabs
                        }
                    }
                    is Screen.Session -> SessionScreen(
                        server = s.server,
                        all = servers,
                        onOs = { os ->
                            val i = servers.indexOfFirst { it.id == s.server.id }
                            if (i >= 0) {
                                servers[i] = servers[i].copy(os = os)
                                persist()
                            }
                        },
                        onExit = { screen = Screen.Tabs },
                        onSwitch = { screen = Screen.Session(it) }
                    )
                }
            }
        }
    }
}

@Composable
fun Welcome(onContinue: () -> Unit) {
    var checked by remember { mutableStateOf(false) }
    val cs = MaterialTheme.colorScheme
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(cs.primaryContainer.copy(alpha = 0.55f), cs.background))
        )
    ) {
        Column(
            Modifier.fillMaxSize().systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                Modifier.size(88.dp).clip(RoundedCornerShape(30.dp)).background(cs.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.DesktopWindows, null, Modifier.size(44.dp), tint = cs.onPrimary)
            }
            Spacer(Modifier.height(24.dp))
            Text(
                "Welcome to Windows Server Remoter!",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(20.dp))
            Card(
                Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = cs.surfaceContainerHigh)
            ) {
                Text(
                    "Welcome to Windows Server Remoter! You can use a server's IP address, name, and password to access Windows from your home or mobile device! But remember, all responsibilities for the windows and server that you created are entirely your responsibility. The creator is not allowed to see ur servers or view the current screen of ur server.",
                    Modifier.padding(20.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    color = cs.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(12.dp))
            Card(
                Modifier.fillMaxWidth().clip(MaterialTheme.shapes.extraLarge).clickable { checked = !checked },
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(
                    containerColor = if (checked) cs.secondaryContainer else cs.surfaceContainer
                )
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked, { checked = it })
                    Text(
                        "I agree that all actions taken by me are my responsibility and not the app creator's.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
            Button(
                onContinue,
                Modifier.fillMaxWidth().height(56.dp),
                enabled = checked
            ) {
                Text("Go to the main page", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
