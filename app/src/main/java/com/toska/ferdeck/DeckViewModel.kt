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

class DeckViewModel(app:Application):AndroidViewModel(app){
 private val codec=Json{prettyPrint=true;ignoreUnknownKeys=true}
 private val file=File(app.filesDir,"ferdeck-config.json")
 private val ws=WebSocketManager()
 private val _config=MutableStateFlow(runCatching{codec.decodeFromString<DeckConfig>(file.readText())}.getOrElse{DeckConfig()});val config=_config.asStateFlow()
 val connected=ws.connected;val log=ws.log
 private val _demo=MutableStateFlow(true);val demo=_demo.asStateFlow()
 private val _editing=MutableStateFlow(false);val editing=_editing.asStateFlow()
 private val _page=MutableStateFlow(0);val page=_page.asStateFlow()
 private val fake=MutableStateFlow(Telemetry())
 val telemetry=combine(ws.telemetry,fake,demo){real,mock,isDemo->if(isDemo)mock else real}.stateIn(viewModelScope,SharingStarted.Eagerly,Telemetry())
 private var reconnectJob:Job?=null;private var volumeJob:Job?=null
 init{viewModelScope.launch{while(true){if(_demo.value)fake.value=Telemetry(cpu=Random.nextFloat()*65+5,ram=Random.nextFloat()*45+25,gpu=Random.nextFloat()*80+5);delay(1000)}}}
 private fun persist(){viewModelScope.launch{kotlin.runCatching{file.writeText(codec.encodeToString(_config.value))}}}
 fun updateNetwork(host:String,port:Int,pin:String){_config.update{it.copy(host=host,port=port,pin=pin)};persist()}
 fun connect(){_demo.value=false;reconnectJob?.cancel();val c=_config.value;ws.connect(c.host,c.port,c.pin);reconnectJob=viewModelScope.launch{while(true){delay(4000);if(!ws.connected.value&&!_demo.value)ws.connect(c.host,c.port,c.pin)}}}
 fun setDemo(value:Boolean){_demo.value=value;if(value){reconnectJob?.cancel();ws.disconnect()}}
 fun setEditing(value:Boolean){_editing.value=value};fun setPage(value:Int){_page.value=value.coerceIn(0,3)}
 fun putTile(page:Int,tile:Tile){_config.update{c->c.copy(pages=c.pages.mapIndexed{i,p->if(i==page)p.copy(tiles=p.tiles.filterNot{it.row==tile.row&&it.column==tile.column}+tile)else p})};persist()}
 fun clearTile(page:Int,row:Int,col:Int){_config.update{c->c.copy(pages=c.pages.mapIndexed{i,p->if(i==page)p.copy(tiles=p.tiles.filterNot{it.row==row&&it.column==col})else p})};persist()}
 fun swapTiles(page:Int,a:Tile,b:Tile){_config.update{c->c.copy(pages=c.pages.mapIndexed{i,p->if(i!=page)p else p.copy(tiles=p.tiles.filterNot{it.id==a.id||it.id==b.id}+a.copy(row=b.row,column=b.column)+b.copy(row=a.row,column=a.column))})};persist()}
 fun execute(tile:Tile){when(tile.type){"hotkey","macro"->ws.send(WireMessage(if(tile.type=="macro")"macro" else "hotkey",keys=tile.action));"launch"->ws.send(WireMessage("launch",command=tile.commandId));"media"->ws.send(WireMessage("media",action=tile.action));else->Unit}}
 fun volume(value:Float){volumeJob?.cancel();volumeJob=viewModelScope.launch{delay(120);ws.send(WireMessage("volume",value=value.coerceIn(0f,1f)))}}
 fun exportJson()=codec.encodeToString(_config.value)
 fun importJson(raw:String){runCatching{codec.decodeFromString<DeckConfig>(raw)}.onSuccess{_config.value=it;persist()}}
 override fun onCleared(){reconnectJob?.cancel();ws.disconnect();super.onCleared()}
}
