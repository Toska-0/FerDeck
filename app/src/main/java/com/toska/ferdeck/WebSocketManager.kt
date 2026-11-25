package com.toska.ferdeck

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.*
import java.util.concurrent.TimeUnit

class WebSocketManager {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val client = OkHttpClient.Builder().pingInterval(20, TimeUnit.SECONDS).build()
    @Volatile private var socket: WebSocket? = null
    private val _connected = MutableStateFlow(false)
    val connected = _connected.asStateFlow()
    private val _telemetry = MutableStateFlow(Telemetry())
    val telemetry = _telemetry.asStateFlow()
    private val _log = MutableStateFlow("")
    val log = _log.asStateFlow()

    @Synchronized
    fun connect(host: String, port: Int, pin: String) {
        disconnect()
        val request = runCatching { Request.Builder().url("ws://$host:$port").build() }
            .getOrElse { _log.value = "Некорректный адрес: ${it.message}"; return }
        socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send(json.encodeToString(WireMessage("auth", pin = pin)))
                _log.value = "Соединение открыто, проверка PIN…"
            }
            override fun onMessage(webSocket: WebSocket, text: String) {
                val type = runCatching {
                    json.parseToJsonElement(text).jsonObject["type"]?.jsonPrimitive?.content
                }.getOrNull()
                when (type) {
                    "auth_ok" -> { _connected.value = true; _log.value = "Авторизация успешна" }
                    "auth_error" -> { _connected.value = false; _log.value = "Неверный PIN" }
                    "telemetry" -> runCatching { json.decodeFromString<Telemetry>(text) }
                        .getOrNull()?.let { _telemetry.value = it }
                    else -> _log.value = text
                }
            }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                _connected.value = false
                if (socket === webSocket) socket = null
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                _connected.value = false
                if (socket === webSocket) socket = null
                _log.value = "Сеть: ${t.message ?: "ошибка соединения"}"
            }
        })
    }

    fun send(message: WireMessage) {
        val raw = json.encodeToString(message)
        _log.value = raw
        if (connected.value) socket?.send(raw) else _log.value = "OFFLINE → $raw"
    }

    @Synchronized
    fun disconnect() {
        _connected.value = false
        socket?.close(1000, "disconnect")
        socket = null
    }
}