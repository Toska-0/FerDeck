package com.toska.ferdeck

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.random.Random

class DeckViewModel(app: Application) : AndroidViewModel(app) {
    private val codec = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }
    private val file = File(app.filesDir, "ferdeck-config.json")
    private val ws = WebSocketManager()
    private val _config = MutableStateFlow(runCatching { codec.decodeFromString<DeckConfig>(file.readText()) }.getOrElse { DeckConfig() })
    val config = _config.asStateFlow()
    val connected = ws.connected
    val log = ws.log

    private val _demo = MutableStateFlow(_config.value.demoMode)
    val demo = _demo.asStateFlow()
    private val _editing = MutableStateFlow(false)
    val editing = _editing.asStateFlow()
    private val fake = MutableStateFlow(Telemetry())
    val telemetry = combine(ws.telemetry, fake, demo) { real, mock, isDemo -> if (isDemo) mock else real }
        .stateIn(viewModelScope, SharingStarted.Eagerly, Telemetry())
    private val _history = MutableStateFlow<List<Telemetry>>(emptyList())
    val history = _history.asStateFlow()

    private var reconnectJob: Job? = null
    private var volumeJob: Job? = null
    private var historyJob: Job? = null

    init {
        viewModelScope.launch {
            while (true) {
                if (_demo.value) fake.value = Telemetry(
                    cpu = Random.nextFloat() * 65f + 5f,
                    ram = Random.nextFloat() * 45f + 25f,
                    gpu = Random.nextFloat() * 80f + 5f
                )
                delay(1000)
            }
        }
        historyJob = viewModelScope.launch {
            telemetry.collect { sample ->
                _history.update { (it + sample).takeLast(60) }
            }
        }
    }

    private fun persist() {
        viewModelScope.launch { runCatching { file.writeText(codec.encodeToString(_config.value)) } }
    }

    fun updateNetwork(host: String, port: Int, pin: String) {
        _config.update { it.copy(host = host.trim(), port = port.coerceIn(1, 65535), pin = pin.trim()) }
        persist()
    }

    fun setKeepScreenOn(value: Boolean) { _config.update { it.copy(keepScreenOn = value) }; persist() }
    fun setFullscreen(value: Boolean) { _config.update { it.copy(fullscreen = value) }; persist() }
    fun setEditing(value: Boolean) { _editing.value = value }

    fun connect() {
        _demo.value = false
        reconnectJob?.cancel()
        val target = _config.value
        ws.connect(target.host, target.port, target.pin)
        reconnectJob = viewModelScope.launch {
            while (true) {
                delay(3500)
                if (!ws.connected.value && !_demo.value) ws.connect(target.host, target.port, target.pin)
            }
        }
    }

    fun disconnect() {
        reconnectJob?.cancel()
        ws.disconnect()
    }

    fun setDemo(value: Boolean) {
        _demo.value = value
        _config.update { it.copy(demoMode = value) }
        persist()
        if (value) { reconnectJob?.cancel(); ws.disconnect() }
    }

    fun putTile(page: Int, tile: Tile) {
        _config.update { c ->
            c.copy(pages = c.pages.mapIndexed { i, p ->
                if (i == page) p.copy(tiles = p.tiles.filterNot { it.row == tile.row && it.column == tile.column } + tile) else p
            })
        }
        persist()
    }

    fun clearTile(page: Int, row: Int, col: Int) {
        _config.update { c -> c.copy(pages = c.pages.mapIndexed { i, p ->
            if (i == page) p.copy(tiles = p.tiles.filterNot { it.row == row && it.column == col }) else p
        }) }
        persist()
    }

    fun swapTiles(page: Int, first: Tile, second: Tile) {
        if (first.row == second.row && first.column == second.column) return
        _config.update { c ->
            c.copy(pages = c.pages.mapIndexed { i, p ->
                if (i != page) p else {
                    val a = p.tiles.firstOrNull { it.row == first.row && it.column == first.column }
                    val b = p.tiles.firstOrNull { it.row == second.row && it.column == second.column }
                    val without = p.tiles.filterNot { (it.row == first.row && it.column == first.column) || (it.row == second.row && it.column == second.column) }
                    val movedA = a?.copy(row = second.row, column = second.column)
                    val movedB = b?.copy(row = first.row, column = first.column)
                    p.copy(tiles = without + listOfNotNull(movedA, movedB))
                }
            })
        }
        persist()
    }

    fun setGrid(page: Int, columns: Int, rows: Int) {
        _config.update { c ->
            c.copy(pages = c.pages.mapIndexed { i, p ->
                if (i != page) p else {
                    val newTiles = p.tiles.filter { it.row < rows && it.column < columns }
                    p.copy(rows = rows, columns = columns, tiles = newTiles)
                }
            })
        }
        persist()
    }

    fun execute(tile: Tile) {
        when (tile.type) {
            "hotkey", "macro" -> ws.send(WireMessage(if (tile.type == "macro") "macro" else "hotkey", keys = tile.action))
            "launch" -> ws.send(WireMessage("launch", command = tile.commandId))
            "media" -> ws.send(WireMessage("media", action = tile.action))
        }
    }

    fun volume(value: Float) {
        volumeJob?.cancel()
        volumeJob = viewModelScope.launch {
            delay(90)
            ws.send(WireMessage("volume", value = value.coerceIn(0f, 1f)))
        }
    }

    fun exportJson(): String = codec.encodeToString(_config.value)

    fun importJson(raw: String): Boolean {
        return runCatching {
            val imported = codec.decodeFromString<DeckConfig>(raw)
            require(imported.pages.isNotEmpty() && imported.pages.size <= 12) { "Недопустимое число страниц" }
            require(imported.pages.all { it.rows in 1..6 && it.columns in 1..8 }) { "Недопустимый размер сетки" }
            _config.value = imported
            persist()
        }.isSuccess
    }

    override fun onCleared() {
        reconnectJob?.cancel()
        volumeJob?.cancel()
        historyJob?.cancel()
        ws.disconnect()
        super.onCleared()
    }
}
