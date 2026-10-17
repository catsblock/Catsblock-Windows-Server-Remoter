package com.example.wsr

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DesktopWindows
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Gamepad
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Mouse
import androidx.compose.material.icons.rounded.NearMe
import androidx.compose.material.icons.rounded.Opacity
import androidx.compose.material.icons.rounded.OpenWith
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SpaceBar
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
    val cs = MaterialTheme.colorScheme
    var tab by remember { mutableIntStateOf(1) }
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var toDelete by remember { mutableStateOf<Server?>(null) }
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val titles = listOf("Favorites", "Main", "Settings")

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        containerColor = cs.background,
        topBar = {
            LargeTopAppBar(
                title = { Text(titles[tab], fontWeight = FontWeight.SemiBold) },
                actions = {
                    if (tab != 2) {
                        IconButton({ searching = !searching; query = "" }) {
                            Icon(if (searching) Icons.Rounded.Close else Icons.Rounded.Search, "Search")
                        }
                        FilledTonalIconButton(onCreate) { Icon(Icons.Rounded.Add, "Add") }
                        Spacer(Modifier.width(8.dp))
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = cs.background,
                    scrolledContainerColor = cs.surfaceContainer
                ),
                scrollBehavior = scroll
            )
        },
        bottomBar = {
            NavigationBar(containerColor = cs.surfaceContainer) {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    icon = { Icon(if (tab == 0) Icons.Rounded.Star else Icons.Rounded.StarBorder, null) },
                    label = { Text("Favorites") }
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    icon = { Icon(Icons.Rounded.Home, null) },
                    label = { Text("Main") }
                )
                NavigationBarItem(
                    selected = tab == 2,
                    onClick = { tab = 2 },
                    icon = { Icon(Icons.Rounded.Settings, null) },
                    label = { Text("Settings") }
                )
            }
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (tab == 2) {
                SettingsPage()
            } else {
                AnimatedVisibility(searching) {
                    TextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        placeholder = { Text("Search") },
                        leadingIcon = { Icon(Icons.Rounded.Search, null) },
                        shape = CircleShape,
                        colors = TextFieldDefaults.colors(
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent,
                            focusedContainerColor = cs.surfaceContainerHigh,
                            unfocusedContainerColor = cs.surfaceContainerHigh
                        ),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                val list = servers.filter {
                    (tab == 1 || it.favorite) &&
                        (it.host.contains(query, true) || it.friendly.contains(query, true))
                }
                when {
                    servers.isEmpty() && tab == 1 -> EmptyState(
                        Icons.Rounded.DesktopWindows,
                        "It looks like you don't have a server set up. Create one now!",
                        "Create",
                        onCreate
                    )
                    list.isEmpty() -> EmptyState(
                        if (tab == 0 && query.isEmpty()) Icons.Rounded.StarBorder else Icons.Rounded.Search,
                        if (tab == 0 && query.isEmpty()) "No favorites yet. Tap the star on a server to add it here."
                        else "No servers found.",
                        null,
                        {}
                    )
                    else -> LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(list, key = { it.id }) { s ->
                            ServerCard(s, onLaunch, onFav, onEdit) { toDelete = it }
                        }
                    }
                }
            }
        }
    }

    toDelete?.let { s ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            icon = { Icon(Icons.Rounded.Delete, null) },
            title = { Text("Delete server?") },
            text = { Text("\"${s.title}\" will be removed from this device.") },
            confirmButton = { TextButton({ onDelete(s); toDelete = null }) { Text("Delete") } },
            dismissButton = { TextButton({ toDelete = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun EmptyState(icon: ImageVector, text: String, button: String?, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(96.dp).clip(CircleShape).background(cs.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, Modifier.size(44.dp), tint = cs.onPrimaryContainer)
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = cs.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (button != null) {
            Spacer(Modifier.height(24.dp))
            Button(onClick, Modifier.height(48.dp)) {
                Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(button)
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
    val cs = MaterialTheme.colorScheme
    Card(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.extraLarge).clickable { onLaunch(s) },
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = cs.surfaceContainerHigh)
    ) {
        Column {
            Box(
                Modifier.fillMaxWidth().height(132.dp).padding(8.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Brush.linearGradient(listOf(cs.primaryContainer, cs.tertiaryContainer))),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.DesktopWindows, null, Modifier.size(56.dp), tint = cs.onPrimaryContainer)
            }
            Row(
                Modifier.padding(start = 20.dp, end = 8.dp, top = 4.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        s.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        s.os,
                        style = MaterialTheme.typography.bodyMedium,
                        color = cs.onSurfaceVariant
                    )
                }
                IconButton({ onFav(s) }) {
                    Icon(
                        if (s.favorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                        "Favorite",
                        tint = if (s.favorite) cs.primary else cs.onSurfaceVariant
                    )
                }
                IconButton({ onEdit(s) }) { Icon(Icons.Rounded.Edit, "Edit", tint = cs.onSurfaceVariant) }
                IconButton({ onDelete(s) }) { Icon(Icons.Rounded.Delete, "Delete", tint = cs.onSurfaceVariant) }
            }
        }
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text,
        Modifier.padding(start = 8.dp, top = 12.dp, bottom = 2.dp),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(Modifier.padding(vertical = 4.dp), content = content)
    }
}

@Composable
fun SettingSwitch(icon: ImageVector, label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(16.dp))
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            thumbContent = if (checked) {
                { Icon(Icons.Rounded.Check, null, Modifier.size(SwitchDefaults.IconSize)) }
            } else null
        )
    }
}

@Composable
private fun SliderRow(
    icon: ImageVector,
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    display: String,
    onChange: (Float) -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(16.dp))
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Text(
                display,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Slider(value, onChange, valueRange = range, onValueChangeFinished = { Prefs.save() })
    }
}

@Composable
fun SettingsPage(inDialog: Boolean = false) {
    Column(
        (if (inDialog) Modifier else Modifier.fillMaxSize())
            .verticalScroll(rememberScrollState())
            .padding(horizontal = if (inDialog) 0.dp else 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SectionTitle("Appearance")
        SettingsGroup {
            SettingSwitch(Icons.Rounded.Palette, "Material You dynamic colors", Prefs.dynamicColor) {
                Prefs.dynamicColor = it; Prefs.save()
            }
            SliderRow(
                Icons.Rounded.Opacity, "Top bar opacity", Prefs.barAlpha, 0.1f..1f,
                "${(Prefs.barAlpha * 100).toInt()}%"
            ) { Prefs.barAlpha = it }
        }
        SectionTitle("Controls")
        SettingsGroup {
            SettingSwitch(Icons.Rounded.Gamepad, "Show joystick (WASD)", Prefs.joystick) {
                Prefs.joystick = it; Prefs.save()
            }
            SettingSwitch(Icons.Rounded.SpaceBar, "Show space button", Prefs.space) {
                Prefs.space = it; Prefs.save()
            }
            SettingSwitch(Icons.Rounded.UnfoldMore, "Show scroll arrows (up/down)", Prefs.scrollButtons) {
                Prefs.scrollButtons = it; Prefs.save()
            }
            SettingSwitch(Icons.Rounded.Mouse, "Show Mouse buttons instead of By touch", Prefs.mouseButtons) {
                Prefs.mouseButtons = it; Prefs.save()
            }
        }
        SectionTitle("Sizes and pointer")
        SettingsGroup {
            SliderRow(
                Icons.Rounded.OpenWith, "Joystick size", Prefs.joySize, 60f..160f,
                "${Prefs.joySize.toInt()} dp"
            ) { Prefs.joySize = it }
            SliderRow(
                Icons.Rounded.NearMe, "Cursor size", Prefs.cursorSize, 0.3f..1.5f,
                String.format("%.1fx", Prefs.cursorSize)
            ) { Prefs.cursorSize = it }
            SliderRow(
                Icons.Rounded.Speed, "Cursor sensitivity", Prefs.sensitivity, 0.5f..4f,
                String.format("%.1fx", Prefs.sensitivity)
            ) { Prefs.sensitivity = it }
        }
        Spacer(Modifier.height(16.dp))
    }
}
