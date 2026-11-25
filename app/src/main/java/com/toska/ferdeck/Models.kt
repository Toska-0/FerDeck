package com.toska.ferdeck

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class Tile(
    val id: String = UUID.randomUUID().toString(),
    val type: String = "empty",
    val title: String = "",
    val icon: String = "＋",
    val color: Long = 0xFF35D07F,
    val action: String = "",
    val commandId: String = "",
    val row: Int = 0,
    val column: Int = 0
)

@Serializable
data class DeckPage(
    val name: String,
    val rows: Int = 3,
    val columns: Int = 4,
    val tiles: List<Tile> = emptyList()
)

@Serializable
data class DeckConfig(
    val host: String = "192.168.1.10",
    val port: Int = 8765,
    val pin: String = "123456",
    val keepScreenOn: Boolean = true,
    val fullscreen: Boolean = false,
    val demoMode: Boolean = true,
    val pages: List<DeckPage> = listOf(
        DeckPage("Основное", tiles = listOf(
            Tile(type = "hotkey", title = "Рабочий стол", icon = "▣", action = "win+d", row = 0, column = 0),
            Tile(type = "media", title = "Play / Pause", icon = "⏯", action = "play_pause", row = 0, column = 1),
            Tile(type = "telemetry", title = "Мониторинг", icon = "◉", row = 0, column = 2),
            Tile(type = "volume", title = "Громкость", icon = "🔊", row = 0, column = 3)
        )),
        DeckPage("Игры"),
        DeckPage("Медиа"),
        DeckPage("Система")
    )
)

@Serializable
data class WireMessage(
    val type: String,
    val pin: String? = null,
    val action: String? = null,
    val keys: String? = null,
    val command: String? = null,
    val value: Float? = null
)

@Serializable
data class Telemetry(
    val type: String = "telemetry",
    val cpu: Float = 0f,
    val ram: Float = 0f,
    val gpu: Float = -1f
)
