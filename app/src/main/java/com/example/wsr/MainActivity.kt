package com.example.wsr

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

sealed interface Screen {
    object Tabs : Screen
    class Form(val server: Server?) : Screen
    class Session(val server: Server) : Screen
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        setContent { App() }
    }
}

@Composable
fun App() {
    val ctx = LocalContext.current
    val dark = isSystemInDarkTheme()
    val scheme = if (Prefs.dynamicColor && Build.VERSION.SDK_INT >= 31) {
        if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
    } else if (dark) darkColorScheme() else lightColorScheme()

    MaterialTheme(colorScheme = scheme) {
        Surface(Modifier.fillMaxSize()) {
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
                    Screen.Tabs -> MainTabs(
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
    Column(
        Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center
    ) {
        Text("Welcome to Windows Server Remoter!", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))
        Text("Welcome to Windows Server Remoter! You can use a server's IP address, name, and password to access Windows from your home or mobile device! But remember, all responsibilities for the windows and server that you created are entirely your responsibility. The creator is not allowed to see any of their servers or view the current screen of their server.")
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked, { checked = it })
            Text("I agree that all actions taken by me are my responsibility and not the app creator's.")
        }
        Spacer(Modifier.height(16.dp))
        Button(onContinue, enabled = checked, modifier = Modifier.fillMaxWidth()) {
            Text("Go to the main page")
        }
    }
}
