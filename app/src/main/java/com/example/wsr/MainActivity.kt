package com.example.wsr

import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DesktopWindows
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
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
        Extra.init(this)
        setContent { App() }
    }

    override fun dispatchGenericMotionEvent(ev: MotionEvent): Boolean {
        val h = Gamepad.motion
        if (h != null && h(ev)) return true
        return super.dispatchGenericMotionEvent(ev)
    }

    override fun dispatchKeyEvent(ev: KeyEvent): Boolean {
        val h = Gamepad.key
        if (h != null && h(ev)) return true
        return super.dispatchKeyEvent(ev)
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
                        onUpdate = { n ->
                            val i = servers.indexOfFirst { it.id == n.id }
                            if (i >= 0 && servers[i] != n) {
                                servers[i] = n
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun Welcome(onContinue: () -> Unit) {
    var checked by remember { mutableStateOf(false) }
    val cs = MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize().background(cs.background)) {
        Column(
            Modifier.fillMaxSize().systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 24.dp)
                .widthIn(max = 560.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                Modifier.size(80.dp).clip(RoundedCornerShape(28.dp)).background(cs.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.DesktopWindows, null, Modifier.size(40.dp), tint = cs.onPrimary)
            }
            Spacer(Modifier.height(24.dp))
            Text(
                "Windows Server Remoter",
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Control a Windows PC or server from your phone using its address, user name and password.",
                style = MaterialTheme.typography.bodyLarge,
                color = cs.onSurfaceVariant
            )
            Spacer(Modifier.height(24.dp))
            Card(
                Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = cs.surfaceContainerHigh)
            ) {
                Text(
                    "You are fully responsible for the Windows machines and servers you connect to. " +
                        "The developer cannot see your servers or their screens.",
                    Modifier.padding(20.dp),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            Spacer(Modifier.height(12.dp))
            Card(
                Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(
                    containerColor = if (checked) cs.secondaryContainer else cs.surfaceContainer
                )
            ) {
                Row(
                    Modifier.fillMaxWidth()
                        .toggleable(value = checked, role = Role.Checkbox, onValueChange = { checked = it })
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked, null)
                    Spacer(Modifier.width(16.dp))
                    Text(
                        "I agree that my actions are my own responsibility, not the developer's.",
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onContinue,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                enabled = checked
            ) {
                Text("Continue", style = MaterialTheme.typography.titleMedium, maxLines = 1)
            }
        }
    }
}
