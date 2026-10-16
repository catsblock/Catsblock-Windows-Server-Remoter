package com.example.wsr

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainTabs(
    servers: List<Server>,
    onCreate: () -> Unit,
    onLaunch: (Server) -> Unit,
    onFav: (Server) -> Unit,
    onEdit: (Server) -> Unit,
    onDelete: (Server) -> Unit
) {
    var tab by remember { mutableIntStateOf(1) }
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (searching) {
                        TextField(
                            query, { query = it },
                            singleLine = true,
                            placeholder = { Text("Search") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Text(listOf("Favorites", "Main", "Settings")[tab])
                    }
                },
                actions = {
                    if (tab != 2) {
                        IconButton({ searching = !searching; query = "" }) {
                            Icon(if (searching) Icons.Default.Close else Icons.Default.Search, "Search")
                        }
                        IconButton(onCreate) { Icon(Icons.Default.Add, "Add") }
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(tab == 0, { tab = 0 }, { Icon(Icons.Default.Star, null) }, label = { Text("Favorites") })
                NavigationBarItem(tab == 1, { tab = 1 }, { Icon(Icons.Default.Home, null) }, label = { Text("Main") })
                NavigationBarItem(tab == 2, { tab = 2 }, { Icon(Icons.Default.Settings, null) }, label = { Text("Settings") })
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            if (tab == 2) {
                SettingsPage()
            } else {
                val list = servers.filter {
                    (tab == 1 || it.favorite) &&
                        (it.host.contains(query, true) || it.friendly.contains(query, true))
                }
                if (servers.isEmpty() && tab == 1) {
                    Column(
                        Modifier.fillMaxSize().padding(24.dp),
                        Arrangement.Center,
                        Alignment.CenterHorizontally
                    ) {
                        Text("It looks like you don't have a server set up. Create one now!", textAlign = TextAlign.Center)
                        Spacer(Modifier.height(16.dp))
                        Button(onCreate) { Text("Create") }
                    }
                } else {
                    LazyColumn(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(list, key = { it.id }) { s ->
                            ServerCard(s, onLaunch, onFav, onEdit, onDelete)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ServerCard(
    s: Server,
    onLaunch: (Server) -> Unit,
    onFav: (Server) -> Unit,
    onEdit: (Server) -> Unit,
    onDelete: (Server) -> Unit
) {
    OutlinedCard(
        Modifier.fillMaxWidth().clickable { onLaunch(s) },
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(s.title, style = MaterialTheme.typography.titleMedium)
                Text(s.os, style = MaterialTheme.typography.bodyMedium)
            }
            IconButton({ onFav(s) }) {
                Icon(if (s.favorite) Icons.Default.Star else Icons.Default.StarBorder, "Favorite")
            }
            IconButton({ onEdit(s) }) { Icon(Icons.Default.Edit, "Edit") }
            IconButton({ onDelete(s) }) { Icon(Icons.Default.Delete, "Delete") }
        }
    }
}

@Composable
fun SettingsPage() {
    Column(
        Modifier.padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SwitchRow("Material You dynamic colors", Prefs.dynamicColor) { Prefs.dynamicColor = it; Prefs.save() }
        SwitchRow("Show joystick (WASD)", Prefs.joystick) { Prefs.joystick = it; Prefs.save() }
        SwitchRow("Show space button", Prefs.space) { Prefs.space = it; Prefs.save() }
        SwitchRow("Show scroll arrows (up/down)", Prefs.scrollButtons) { Prefs.scrollButtons = it; Prefs.save() }
        SwitchRow("Show Mouse buttons instead of By touch", Prefs.mouseButtons) { Prefs.mouseButtons = it; Prefs.save() }
        Text("Joystick size")
        Slider(Prefs.joySize, { Prefs.joySize = it }, valueRange = 60f..160f, onValueChangeFinished = { Prefs.save() })
        Text("Cursor size")
        Slider(Prefs.cursorSize, { Prefs.cursorSize = it }, valueRange = 0.3f..1.5f, onValueChangeFinished = { Prefs.save() })
        Text("Top bar opacity")
        Slider(Prefs.barAlpha, { Prefs.barAlpha = it }, valueRange = 0.1f..1f, onValueChangeFinished = { Prefs.save() })
        Text("Cursor sensitivity")
        Slider(Prefs.sensitivity, { Prefs.sensitivity = it }, valueRange = 0.5f..4f, onValueChangeFinished = { Prefs.save() })
    }
}

@Composable
fun SwitchRow(label: String, v: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(v, onChange)
    }
}
