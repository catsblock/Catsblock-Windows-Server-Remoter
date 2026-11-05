package com.example.wsr

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Gamepad
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Mouse
import androidx.compose.material.icons.rounded.NearMe
import androidx.compose.material.icons.rounded.Opacity
import androidx.compose.material.icons.rounded.OpenWith
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SpaceBar
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
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
    val titles = listOf("Favorites", "Servers", "Settings")
    val focus = remember { FocusRequester() }

    LaunchedEffect(searching) {
        if (searching) focus.requestFocus()
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        containerColor = cs.background,
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(titles[tab]) },
                subtitle = {
                    Text(
                        when {
                            tab == 2 -> "Appearance, controls and pointer"
                            servers.isEmpty() -> "Nothing saved yet"
                            else -> "${servers.size} saved"
                        }
                    )
                },
                actions = {
                    if (tab != 2 && servers.isNotEmpty()) {
                        IconButton({ searching = !searching; query = "" }) {
                            Icon(
                                if (searching) Icons.Rounded.Close else Icons.Rounded.Search,
                                if (searching) "Close search" else "Search servers"
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = cs.background,
                    scrolledContainerColor = cs.surfaceContainer
                ),
                scrollBehavior = scroll
            )
        },
        floatingActionButton = {
            if (tab != 2 && servers.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = onCreate,
                    icon = { Icon(Icons.Rounded.Add, null) },
                    text = { Text("New server") },
                    containerColor = cs.primaryContainer,
                    contentColor = cs.onPrimaryContainer
                )
            }
        },
        bottomBar = {
            ShortNavigationBar(containerColor = cs.surfaceContainer) {
                ShortNavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    icon = { Icon(if (tab == 0) Icons.Rounded.Star else Icons.Rounded.StarBorder, null) },
                    label = { Text("Favorites", maxLines = 1) }
                )
                ShortNavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    icon = { Icon(Icons.Rounded.Dns, null) },
                    label = { Text("Servers", maxLines = 1) }
                )
                ShortNavigationBarItem(
                    selected = tab == 2,
                    onClick = { tab = 2 },
                    icon = { Icon(Icons.Rounded.Settings, null) },
                    label = { Text("Settings", maxLines = 1) }
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
                        placeholder = { Text("Search by name or address") },
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
                            .focusRequester(focus)
                    )
                }
                val q = query.trim()
                val list = servers.filter {
                    (tab == 1 || it.favorite) &&
                        (q.isEmpty() || it.host.contains(q, true) || it.friendly.contains(q, true))
                }
                when {
                    servers.isEmpty() -> EmptyState(
                        Icons.Rounded.DesktopWindows,
                        if (tab == 0) "No favorites yet" else "No servers yet",
                        if (tab == 0) "Add a server first, then tap its star to pin it here."
                        else "Add the address and credentials of a Windows PC or server to start.",
                        "Add server",
                        onCreate
                    )
                    list.isEmpty() && tab == 0 && q.isEmpty() -> EmptyState(
                        Icons.Rounded.StarBorder,
                        "No favorites yet",
                        "Tap the star on a server to pin it here.",
                        null,
                        {}
                    )
                    list.isEmpty() -> EmptyState(
                        Icons.Rounded.Search,
                        "No matches",
                        "Nothing matches \"$q\".",
                        null,
                        {}
                    )
                    else -> LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
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
            text = { Text("\"${s.title}\" and its saved password will be removed from this device.") },
            confirmButton = {
                TextButton(
                    { onDelete(s); toDelete = null },
                    colors = ButtonDefaults.textButtonColors(contentColor = cs.error)
                ) { Text("Delete") }
            },
            dismissButton = { TextButton({ toDelete = null }) { Text("Cancel") } }
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun EmptyState(icon: ImageVector, title: String, text: String, button: String?, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(96.dp).clip(RoundedCornerShape(32.dp)).background(cs.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, Modifier.size(44.dp), tint = cs.onPrimaryContainer)
        }
        Spacer(Modifier.size(24.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.size(8.dp))
        Text(
            text,
            Modifier.widthIn(max = 320.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = cs.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (button != null) {
            Spacer(Modifier.size(24.dp))
            Button(
                onClick = onClick,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.heightIn(min = 56.dp)
            ) {
                Icon(Icons.Rounded.Add, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(button, maxLines = 1)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ServerCard(
    s: Server,
    onLaunch: (Server) -> Unit,
    onFav: (Server) -> Unit,
    onEdit: (Server) -> Unit,
    onDelete: (Server) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.97f else 1f,
        MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "cardPress"
    )
    val detail = buildList {
        if (s.friendly.isNotBlank()) add(s.host)
        add(s.os)
        add("${s.resW} × ${s.resH}")
    }.joinToString(" · ")

    Card(
        onClick = { onLaunch(s) },
        modifier = Modifier.fillMaxWidth().graphicsLayer { scaleX = scale; scaleY = scale },
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = cs.surfaceContainerHigh),
        interactionSource = source
    ) {
        Column(Modifier.padding(start = 20.dp, end = 12.dp, top = 20.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    Modifier.size(56.dp).clip(RoundedCornerShape(20.dp)).background(cs.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.DesktopWindows, null, Modifier.size(28.dp), tint = cs.onPrimaryContainer)
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        s.title,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        detail,
                        style = MaterialTheme.typography.bodyMedium,
                        color = cs.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton({ onFav(s) }) {
                    Icon(
                        if (s.favorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                        if (s.favorite) "Remove from favorites" else "Add to favorites",
                        tint = if (s.favorite) cs.primary else cs.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.size(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = { onLaunch(s) },
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Rounded.PlayArrow, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Connect", maxLines = 1)
                }
                Spacer(Modifier.weight(1f))
                IconButton({ onEdit(s) }) {
                    Icon(Icons.Rounded.Edit, "Edit ${s.title}", tint = cs.onSurfaceVariant)
                }
                IconButton({ onDelete(s) }) {
                    Icon(Icons.Rounded.Delete, "Delete ${s.title}", tint = cs.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text,
        Modifier.fillMaxWidth().padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    Card(
        Modifier.fillMaxWidth().animateContentSize(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(Modifier.padding(vertical = 4.dp), content = content)
    }
}

@Composable
fun SettingSwitch(
    icon: ImageVector,
    label: String,
    checked: Boolean,
    supporting: String? = null,
    onChange: (Boolean) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (supporting != null) {
                Text(
                    supporting,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.width(16.dp))
        Switch(
            checked = checked,
            onCheckedChange = null,
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
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(16.dp))
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.width(12.dp))
            Text(
                display,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false
            )
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            onValueChangeFinished = { Prefs.save() }
        )
    }
}

@Composable
fun SettingsPage(inDialog: Boolean = false) {
    Column(
        (if (inDialog) Modifier.fillMaxWidth() else Modifier.fillMaxSize().verticalScroll(rememberScrollState()))
            .padding(horizontal = if (inDialog) 0.dp else 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SectionTitle("Appearance")
        SettingsGroup {
            SettingSwitch(
                Icons.Rounded.Palette, "Dynamic color", Prefs.dynamicColor,
                "Use colors from your wallpaper (Android 12+)"
            ) {
                Prefs.dynamicColor = it; Prefs.save()
            }
            SliderRow(
                Icons.Rounded.Opacity, "Session bar opacity", Prefs.barAlpha, 0.1f..1f,
                "${(Prefs.barAlpha * 100).toInt()}%"
            ) { Prefs.barAlpha = it }
        }
        SectionTitle("On-screen controls")
        SettingsGroup {
            SettingSwitch(Icons.Rounded.Gamepad, "Movement joystick", Prefs.joystick, "Sends W, A, S, D") {
                Prefs.joystick = it; Prefs.save()
            }
            SettingSwitch(Icons.Rounded.Explore, "Look joystick", Prefs.rightJoystick, "Moves the mouse pointer") {
                Prefs.rightJoystick = it; Prefs.save()
            }
            SettingSwitch(Icons.Rounded.SpaceBar, "Space button", Prefs.space) {
                Prefs.space = it; Prefs.save()
            }
            SettingSwitch(Icons.Rounded.UnfoldMore, "Scroll buttons", Prefs.scrollButtons, "Up and down arrows") {
                Prefs.scrollButtons = it; Prefs.save()
            }
            SettingSwitch(
                Icons.Rounded.Mouse, "Mouse buttons", Prefs.mouseButtons,
                "Show Left and Right buttons instead of tap gestures"
            ) {
                Prefs.mouseButtons = it; Prefs.save()
            }
            SettingSwitch(Icons.Rounded.Keyboard, "Quick keys", Prefs.mainKeys, "Q, E, R, F, G") {
                Prefs.mainKeys = it; Prefs.save()
            }
        }
        SectionTitle("Game controller")
        SettingsGroup {
            SettingSwitch(
                Icons.Rounded.SportsEsports, "Controller support", Prefs.controller,
                "Map a connected gamepad to keyboard and mouse"
            ) {
                Prefs.controller = it; Prefs.save()
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
                "%.1f×".format(Prefs.cursorSize)
            ) { Prefs.cursorSize = it }
            SliderRow(
                Icons.Rounded.Speed, "Cursor speed", Prefs.sensitivity, 0.5f..4f,
                "%.1f×".format(Prefs.sensitivity)
            ) { Prefs.sensitivity = it }
        }
        ExtraSettings()
        Spacer(Modifier.size(16.dp))
    }
}
