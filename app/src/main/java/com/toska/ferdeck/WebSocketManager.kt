package com.toska.ferdeck

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.*
import java.util.concurrent.TimeUnit

class WebSocketManager {
    private val json=Json { ignoreUnknownKeys=true; encodeDefaults=true }
    private val client=OkHttpClient.Builder().pingInterval(20,TimeUnit.SECONDS).build()
    private var socket:WebSocket?=null
    private val _connected=MutableStateFlow(false); val connected=_connected.asStateFlow()
    private val _telemetry=MutableStateFlow(Telemetry()); val telemetry=_telemetry.asStateFlow()
    private val _log=MutableStateFlow(""); val log=_log.asStateFlow()
    fun connect(host:String,port:Int,pin:String) {
        disconnect()
        val req=Request.Builder().url("ws://$host:$port").build()
        socket=client.newWebSocket(req,object:WebSocketListener(){
            override fun onOpen(webSocket:WebSocket,response:Response){_connected.value=true;webSocket.send(json.encodeToString(WireMessage("auth",pin=pin)))}
            override fun onMessage(webSocket:WebSocket,text:String){_log.value=text;runCatching{json.decodeFromString<Telemetry>(text)}.getOrNull()?.let{_telemetry.value=it}}
            override fun onClosed(webSocket:WebSocket,code:Int,reason:String){_connected.value=false}
            override fun onFailure(webSocket:WebSocket,t:Throwable,response:Response?){_connected.value=false;_log.value="Сеть: ${t.message}"}
        })
    }
    fun send(message:WireMessage){val raw=json.encodeToString(message);_log.value=raw;socket?.send(raw)}
    fun disconnect(){socket?.close(1000,"disconnect");socket=null;_connected.value=false}
}
