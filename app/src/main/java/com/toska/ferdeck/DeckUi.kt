package com.toska.ferdeck

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

private val DeckGreen = Color(0xFF35D07F)
private val DeckPanel = Color(0xFF101010)
private val DeckBorder = Color(0xFF292929)
private val AccentChoices = listOf(0xFF35D07F, 0xFF4DA3FF, 0xFFFFB547, 0xFFFF5D73, 0xFFB18CFF, 0xFFFFFFFF)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FerDeckScreen(
    vm: DeckViewModel,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onKeepScreenOn: (Boolean) -> Unit,
    onFullscreen: (Boolean) -> Unit
) {
    val config by vm.config.collectAsStateWithLifecycle()
    val editing by vm.editing.collectAsStateWithLifecycle()
    val connected by vm.connected.collectAsStateWithLifecycle()
    val demo by vm.demo.collectAsStateWithLifecycle()
    val stats by vm.telemetry.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val log by vm.log.collectAsStateWithLifecycle()
    var settings by remember { mutableStateOf(false) }
    var selectedTile by remember { mutableStateOf<Tile?>(null) }
    var swapMode by remember { mutableStateOf(false) }
    var swapFirst by remember { mutableStateOf<Tile?>(null) }
    var host by remember(config.host) { mutableStateOf(config.host) }
    var port by remember(config.port) { mutableStateOf(config.port.toString()) }
    var pin by remember(config.pin) { mutableStateOf(config.pin) }
    val pager = rememberPagerState(pageCount = { config.pages.size })
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(log) {
        if (log.isNotBlank()) snackbar.showSnackbar(log, withDismissAction = true, duration = SnackbarDuration.Short)
    }
    LaunchedEffect(pager.currentPage) {
        swapFirst = null
    }

    Scaffold(
        containerColor = Color.Black,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF080808)) {
                config.pages.forEachIndexed { index, page ->
                    NavigationBarItem(
                        selected = pager.currentPage == index,
                        onClick = { scope.launch { pager.animateScrollToPage(index) } },
                        icon = { Text(listOf("⌂", "🎮", "♫", "⚙").getOrElse(index) { "▦" }) },
                        label = { Text(page.name, maxLines = 1) },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = DeckGreen, selectedTextColor = DeckGreen, indicatorColor = Color(0xFF173525))
                    )
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("FERDECK", color = DeckGreen, fontWeight = FontWeight.Black, letterSpacing = 3.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("●", color = if (demo || connected) DeckGreen else Color(0xFFFF6262), fontSize = 9.sp)
                        Spacer(Modifier.width(5.dp))
                        Text(
                            when { demo -> "ДЕМО-РЕЖИМ"; connected -> "ПОДКЛЮЧЕНО"; else -> "НЕТ СОЕДИНЕНИЯ" },
                            color = if (demo || connected) DeckGreen else Color.Gray,
                            fontSize = 10.sp
                        )
                    }
                }
                TextButton(onClick = {
                    vm.setEditing(!editing)
                    swapMode = false
                    swapFirst = null
                }) { Text(if (editing) "ГОТОВО" else "РЕДАКТОР", color = DeckGreen) }
                TextButton(onClick = { settings = true }) { Text("⚙", color = Color.White, fontSize = 20.sp) }
            }

            Row(
                Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(config.pages[pager.currentPage].name, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                if (editing) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { swapMode = !swapMode; swapFirst = null }) {
                            Text(if (swapMode) "ОТМЕНА ОБМЕНА" else "⇄ ОБМЕН", color = if (swapMode) Color(0xFFFFB547) else DeckGreen, fontSize = 11.sp)
                        }
                    }
                } else {
                    Text("CPU ${stats.cpu.toInt()}%  •  RAM ${stats.ram.toInt()}%", color = Color.Gray, fontSize = 10.sp)
                }
            }

            HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { pageIndex ->
                val page = config.pages[pageIndex]
                val cells = (0 until page.rows * page.columns).toList()
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val landscape = maxWidth > maxHeight
                    val displayColumns = if (landscape) maxOf(page.columns, 5) else page.columns
                    val displayRows = (cells.size + displayColumns - 1) / displayColumns
                    val cellHeight = ((maxHeight - (displayRows - 1) * 9.dp) / displayRows).coerceIn(68.dp, 148.dp)
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(displayColumns),
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(9.dp),
                        horizontalArrangement = Arrangement.spacedBy(9.dp),
                        contentPadding = PaddingValues(bottom = 8.dp)
                    ) {
                        items(cells) { index ->
                            val row = index / page.columns
                            val column = index % page.columns
                            val tile = page.tiles.firstOrNull { it.row == row && it.column == column }
                                ?: Tile(row = row, column = column)
                            DeckTile(
                                tile = tile,
                                height = cellHeight,
                                cpu = stats.cpu,
                                ram = stats.ram,
                                gpu = stats.gpu,
                                history = history,
                                editing = editing,
                                onVolume = vm::volume
                            ) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                when {
                                    editing && swapMode -> {
                                        val first = swapFirst
                                        if (first == null) swapFirst = tile
                                        else {
                                            vm.swapTiles(pageIndex, first, tile)
                                            swapFirst = null
                                            swapMode = false
                                        }
                                    }
                                    editing -> selectedTile = tile
                                    tile.type == "volume" || tile.type == "telemetry" || tile.type == "empty" -> Unit
                                    else -> vm.execute(tile)
                                }
                            }
                        }
                    }
                }
            }

            if (editing) {
                Text(
                    if (swapMode) if (swapFirst == null) "Выберите первую плитку для обмена" else "Теперь выберите вторую плитку"
                    else "Нажмите ячейку, чтобы настроить её",
                    color = Color.Gray, fontSize = 10.sp, modifier = Modifier.padding(vertical = 5.dp)
                )
            } else {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 5.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("CPU ${stats.cpu.toInt()}%   RAM ${stats.ram.toInt()}%   GPU ${if (stats.gpu < 0) "—" else "${stats.gpu.toInt()}%"}", color = Color.Gray, fontSize = 10.sp)
                    TextButton(onClick = { scope.launch { snackbar.showSnackbar(log.ifBlank { "Команды появятся здесь" }) } }) { Text("ЛОГ", color = DeckGreen, fontSize = 10.sp) }
                }
            }
        }
    }

    selectedTile?.let { tile ->
        TileEditor(
            initial = tile,
            onSave = { vm.putTile(pager.currentPage, it); selectedTile = null },
            onClear = { vm.clearTile(pager.currentPage, tile.row, tile.column); selectedTile = null },
            onClose = { selectedTile = null }
        )
    }

    if (settings) {
        AlertDialog(
            onDismissRequest = { settings = false },
            containerColor = DeckPanel,
            title = { Text("Настройки FerDeck", color = Color.White) },
            text = {
                Column(
                    Modifier.fillMaxWidth().heightIn(max = 540.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("ПОДКЛЮЧЕНИЕ К ПК", color = DeckGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    OutlinedTextField(host, { host = it }, label = { Text("IP компьютера") }, singleLine = true)
                    OutlinedTextField(port, { port = it }, label = { Text("Порт") }, singleLine = true)
                    OutlinedTextField(pin, { pin = it }, label = { Text("PIN-код") }, singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(onClick = {
                            vm.updateNetwork(host, port.toIntOrNull() ?: 8765, pin)
                            vm.connect()
                            settings = false
                        }) { Text("Подключить") }
                        OutlinedButton(onClick = { vm.disconnect(); vm.setDemo(false) }) { Text("Отключить") }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Демо-режим", color = Color.White, modifier = Modifier.weight(1f))
                        Switch(checked = demo, onCheckedChange = vm::setDemo)
                    }
                    Divider(color = DeckBorder)
                    Text("ИНТЕРФЕЙС", color = DeckGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Не выключать экран", color = Color.White, modifier = Modifier.weight(1f))
                        Switch(checked = config.keepScreenOn, onCheckedChange = { vm.setKeepScreenOn(it); onKeepScreenOn(it) })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Полноэкранный режим", color = Color.White, modifier = Modifier.weight(1f))
                        Switch(checked = config.fullscreen, onCheckedChange = { vm.setFullscreen(it); onFullscreen(it) })
                    }
                    Divider(color = DeckBorder)
                    Text("РАЗМЕР СЕТКИ • ${config.pages[pager.currentPage].name}", color = DeckGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    val current = config.pages[pager.currentPage]
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        listOf(4 to 3, 5 to 3, 6 to 3).forEach { (cols, rows) ->
                            FilterChip(
                                selected = current.columns == cols && current.rows == rows,
                                onClick = { vm.setGrid(pager.currentPage, cols, rows) },
                                label = { Text("${cols}×${rows}") }
                            )
                        }
                    }
                    Divider(color = DeckBorder)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(onClick = onExport, modifier = Modifier.weight(1f)) { Text("Экспорт JSON") }
                        OutlinedButton(onClick = onImport, modifier = Modifier.weight(1f)) { Text("Импорт JSON") }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { settings = false }) { Text("Готово", color = DeckGreen) } }
        )
    }
}

@Composable
private fun DeckTile(
    tile: Tile,
    height: Dp,
    cpu: Float,
    ram: Float,
    gpu: Float,
    history: List<Telemetry>,
    editing: Boolean,
    onVolume: (Float) -> Unit,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, label = "tilePress")
    val accent = Color(tile.color)
    Column(
        Modifier.fillMaxWidth().height(height).scale(scale)
            .background(DeckPanel, RoundedCornerShape(15.dp))
            .border(1.dp, if (editing) accent.copy(alpha = 0.65f) else DeckBorder, RoundedCornerShape(15.dp))
            .then(if (editing || tile.type != "volume") Modifier.clickable(interactionSource = interaction, indication = null) { onClick() } else Modifier)
            .padding(horizontal = 7.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(if (tile.type == "empty") "＋" else tile.icon, fontSize = 23.sp, color = accent, maxLines = 1)
        if (tile.title.isNotBlank() || tile.type == "empty") {
            Text(tile.title.ifBlank { "Добавить" }, color = Color.White, fontSize = 10.sp, maxLines = 1)
        }
        when (tile.type) {
            "volume" -> {
                var volume by remember(tile.id) { mutableFloatStateOf(0.5f) }
                if (!editing) Slider(value = volume, onValueChange = { volume = it; onVolume(it) }, modifier = Modifier.height(30.dp), colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent))
            }
            "telemetry" -> {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Text("C ${cpu.toInt()}%", color = Color.LightGray, fontSize = 8.sp)
                    Text("R ${ram.toInt()}%", color = Color.LightGray, fontSize = 8.sp)
                    Text("G ${if (gpu < 0) "—" else gpu.toInt()}%", color = Color.LightGray, fontSize = 8.sp)
                }
                Sparkline(history.takeLast(45), accent)
            }
        }
        if (editing) Text("НАСТРОИТЬ", color = accent, fontSize = 7.sp)
    }
}

@Composable
private fun Sparkline(samples: List<Telemetry>, accent: Color) {
    Canvas(Modifier.fillMaxWidth().height(24.dp)) {
        if (samples.size < 2) return@Canvas
        fun plot(values: List<Float>, lineColor: Color) {
            val path = Path()
            values.forEachIndexed { index, value ->
                val x = size.width * index / (values.size - 1).toFloat()
                val y = size.height * (1f - value.coerceIn(0f, 100f) / 100f)
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, lineColor, style = Stroke(width = 2.1f))
        }
        plot(samples.map { it.cpu }, accent)
        plot(samples.map { it.ram }, Color(0xFF4DA3FF))
        plot(samples.map { it.gpu.coerceAtLeast(0f) }, Color(0xFFFFB547))
    }
}

@Composable
private fun TileEditor(initial: Tile, onSave: (Tile) -> Unit, onClear: () -> Unit, onClose: () -> Unit) {
    var type by remember(initial.id) { mutableStateOf(if (initial.type == "empty") "hotkey" else initial.type) }
    var title by remember(initial.id) { mutableStateOf(initial.title) }
    var icon by remember(initial.id) { mutableStateOf(initial.icon) }
    var action by remember(initial.id) { mutableStateOf(initial.action) }
    var command by remember(initial.id) { mutableStateOf(initial.commandId) }
    var color by remember(initial.id) { mutableLongStateOf(initial.color) }
    var expanded by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onClose,
        containerColor = DeckPanel,
        title = { Text("Настройка плитки", color = Color.White) },
        text = {
            Column(Modifier.heightIn(max = 480.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box {
                    OutlinedButton(onClick = { expanded = true }) { Text("Тип: $type") }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        listOf("hotkey", "macro", "launch", "media", "volume", "telemetry").forEach { option ->
                            DropdownMenuItem(text = { Text(option) }, onClick = { type = option; expanded = false })
                        }
                    }
                }
                OutlinedTextField(title, { title = it }, label = { Text("Название") }, singleLine = true)
                OutlinedTextField(icon, { icon = it }, label = { Text("Иконка / emoji") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("🎮", "⏯", "🔊", "🖥", "🌐", "⚙", "🚀").forEach { emoji ->
                        Text(emoji, modifier = Modifier.clickable { icon = emoji }, fontSize = 18.sp)
                    }
                }
                when (type) {
                    "hotkey" -> OutlinedTextField(action, { action = it }, label = { Text("Клавиши: ctrl+shift+m") }, singleLine = true)
                    "macro" -> OutlinedTextField(action, { action = it }, label = { Text("Макрос: ctrl+c;wait:300;ctrl+v") }, minLines = 2)
                    "media" -> {
                        var mediaExpanded by remember { mutableStateOf(false) }
                        Box {
                            OutlinedButton(onClick = { mediaExpanded = true }) { Text("Команда: ${action.ifBlank { "play_pause" }}") }
                            DropdownMenu(expanded = mediaExpanded, onDismissRequest = { mediaExpanded = false }) {
                                listOf("play_pause", "next", "prev", "mute").forEach { option ->
                                    DropdownMenuItem(text = { Text(option) }, onClick = { action = option; mediaExpanded = false })
                                }
                            }
                        }
                    }
                    "launch" -> {
                        OutlinedTextField(command, { command = it }, label = { Text("ID команды из белого списка") }, singleLine = true)
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            listOf("notepad", "calculator", "steam", "browser", "explorer").forEach { id ->
                                AssistChip(onClick = { command = id }, label = { Text(id, fontSize = 9.sp) })
                            }
                        }
                    }
                }
                Text("ЦВЕТ АКЦЕНТА", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    AccentChoices.forEach { choice ->
                        Box(
                            Modifier.size(29.dp).background(Color(choice), RoundedCornerShape(50))
                                .border(if (color == choice) 3.dp else 1.dp, Color.White.copy(alpha = if (color == choice) 1f else 0.25f), RoundedCornerShape(50))
                                .clickable { color = choice }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(initial.copy(type = type, title = title, icon = icon, action = action, commandId = command, color = color))
            }) { Text("Сохранить", color = DeckGreen) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onClear) { Text("Очистить", color = Color(0xFFFF6262)) }
                TextButton(onClick = onClose) { Text("Отмена") }
            }
        }
    )
}
