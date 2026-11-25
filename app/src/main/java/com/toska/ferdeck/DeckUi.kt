package com.toska.ferdeck

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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

private val Green=Color(0xFF35D07F)
private val Panel=Color(0xFF101010)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FerDeckScreen(vm:DeckViewModel,onExport:()->Unit,onImport:()->Unit){
 val cfg by vm.config.collectAsStateWithLifecycle()
 val editing by vm.editing.collectAsStateWithLifecycle()
 val connected by vm.connected.collectAsStateWithLifecycle()
 val demo by vm.demo.collectAsStateWithLifecycle()
 val stats by vm.telemetry.collectAsStateWithLifecycle()
 val log by vm.log.collectAsStateWithLifecycle()
 var settings by remember{mutableStateOf(false)}
 var selected by remember{mutableStateOf<Tile?>(null)}
 var host by remember(cfg.host){mutableStateOf(cfg.host)}
 var port by remember(cfg.port){mutableStateOf(cfg.port.toString())}
 var pin by remember(cfg.pin){mutableStateOf(cfg.pin)}
 var showLog by remember{mutableStateOf(false)}
 val pager= rememberPagerState(pageCount={cfg.pages.size})
 val scope= rememberCoroutineScope()
 val haptic= LocalHapticFeedback.current
 LaunchedEffect(pager.currentPage){vm.setPage(pager.currentPage)}
 Scaffold(containerColor=Color.Black,bottomBar={
  NavigationBar(containerColor=Color(0xFF080808)){
   cfg.pages.forEachIndexed{i,p->NavigationBarItem(selected=pager.currentPage==i,onClick={scope.launch{pager.animateScrollToPage(i)}},icon={Text(listOf("⌂","🎮","♫","⚙").getOrElse(i){"•"})},label={Text(p.name)})}
  }
 }){padding->
  Column(Modifier.fillMaxSize().padding(padding).padding(horizontal=14.dp)){
   Row(Modifier.fillMaxWidth().padding(top=10.dp,bottom=12.dp),verticalAlignment=Alignment.CenterVertically){
    Column(Modifier.weight(1f)){Text("FERDECK",color=Green,fontWeight=FontWeight.Black,letterSpacing=3.sp);Text(if(demo)"ДЕМО-РЕЖИМ" else if(connected)"ПОДКЛЮЧЕНО" else "НЕТ СОЕДИНЕНИЯ",color=if(demo||connected)Green else Color.Gray,fontSize=11.sp)}
    TextButton(onClick={vm.setEditing(!editing)}){Text(if(editing)"ГОТОВО" else "РЕДАКТОР",color=Green)}
    TextButton(onClick={settings=true}){Text("⚙",color=Color.White,fontSize=20.sp)}
   }
   Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){Text(if(editing)"РЕДАКТИРОВАНИЕ" else cfg.pages[pager.currentPage].name,color=Color.White,fontSize=20.sp,fontWeight=FontWeight.Bold);Text(if(editing)"НАЖМИТЕ ЯЧЕЙКУ" else "● LIVE",color=Green,fontSize=10.sp)}
   Spacer(Modifier.height(8.dp))
   HorizontalPager(state=pager,modifier=Modifier.weight(1f)){index->
    val p=cfg.pages[index]
    LazyVerticalGrid(columns=GridCells.Fixed(p.columns),modifier=Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(10.dp),horizontalArrangement=Arrangement.spacedBy(10.dp),contentPadding=PaddingValues(bottom=8.dp)){
     items((0 until p.rows*p.columns).toList()){idx->
      val row=idx/p.columns;val col=idx%p.columns
      val tile=p.tiles.firstOrNull{it.row==row&&it.column==col}?:Tile(row=row,column=col)
      DeckTile(tile,stats.cpu,stats.ram,stats.gpu,editing,vm::volume){
       haptic.performHapticFeedback(HapticFeedbackType.LongPress)
       if(editing)selected=tile else if(tile.type=="volume")Unit else {vm.execute(tile);showLog=true}
      }
     }
    }
   }
   if(!editing){Text("CPU ${stats.cpu.toInt()}%     RAM ${stats.ram.toInt()}%     GPU ${if(stats.gpu<0)"—" else stats.gpu.toInt().toString()+"%"}",color=Color.Gray,fontSize=11.sp,modifier=Modifier.padding(vertical=7.dp).clickable{showLog=true})}
  }
 }
 selected?.let{tile->TileEditor(tile,{vm.putTile(pager.currentPage,it);selected=null},{vm.clearTile(pager.currentPage,tile.row,tile.column);selected=null},{selected=null})}
 if(settings)AlertDialog(onDismissRequest={settings=false},containerColor=Panel,title={Text("Подключение",color=Color.White)},text={
  Column(verticalArrangement=Arrangement.spacedBy(7.dp)){
   OutlinedTextField(host,{host=it},label={Text("IP компьютера")},singleLine=true)
   OutlinedTextField(port,{port=it},label={Text("Порт")},singleLine=true)
   OutlinedTextField(pin,{pin=it},label={Text("PIN")},singleLine=true)
   Row(verticalAlignment=Alignment.CenterVertically){Text("Demo Mode",color=Color.White,modifier=Modifier.weight(1f));Switch(checked=demo,onCheckedChange=vm::setDemo)}
   Row(horizontalArrangement=Arrangement.spacedBy(5.dp)){Button(onClick={vm.updateNetwork(host,port.toIntOrNull()?:8765,pin);vm.connect();settings=false}){Text("Подключить")};OutlinedButton(onClick=onExport){Text("Экспорт")};OutlinedButton(onClick=onImport){Text("Импорт")}}
  }
 },confirmButton={TextButton(onClick={settings=false}){Text("Закрыть")}})
 if(showLog)AlertDialog(onDismissRequest={showLog=false},containerColor=Panel,title={Text("Последнее сообщение",color=Color.White)},text={Text(if(log.isBlank())"Пока нет отправленных команд" else log,color=Color.LightGray)},confirmButton={TextButton(onClick={showLog=false}){Text("ОК")}})
}

@Composable
private fun DeckTile(tile:Tile,cpu:Float,ram:Float,gpu:Float,editing:Boolean,onVolume:(Float)->Unit,onClick:()->Unit){
 val interaction=remember{MutableInteractionSource()}
 val pressed by interaction.collectIsPressedAsState()
 val scale by animateFloatAsState(if(pressed) 0.96f else 1f,label="tilePress")
 var volume by remember{mutableStateOf(0.5f)}
 Column(Modifier.fillMaxWidth().height(108.dp).scale(scale).background(Panel,RoundedCornerShape(17.dp)).border(1.dp,Color(0xFF292929),RoundedCornerShape(17.dp)).clickable(interactionSource=interaction,indication=null){onClick()}.padding(9.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){
  Text(if(tile.type=="empty")"＋" else tile.icon,fontSize=25.sp,color=Green)
  Spacer(Modifier.height(4.dp))
  Text(tile.title.ifBlank{if(tile.type=="empty")"Добавить" else tile.type},color=Color.White,fontSize=11.sp,maxLines=2)
  when(tile.type){
   "telemetry"->{Text("CPU ${cpu.toInt()}  RAM ${ram.toInt()}  GPU ${if(gpu<0)"—" else gpu.toInt().toString()}",color=Color.LightGray,fontSize=8.sp);Sparkline(listOf(cpu,ram,gpu.coerceAtLeast(0f)))}
   "volume"->Slider(value=volume,onValueChange={volume=it;onVolume(it)},modifier=Modifier.height(24.dp))
  }
  if(editing)Text("EDIT",color=Green,fontSize=8.sp)
 }
}

@Composable
private fun Sparkline(values:List<Float>){
 Canvas(Modifier.fillMaxWidth().height(22.dp)){val path=Path();values.forEachIndexed{i,v->val x=size.width*i/(values.size-1).coerceAtLeast(1);val y=size.height*(1f-v.coerceIn(0f,100f)/100f);if(i==0)path.moveTo(x,y)else path.lineTo(x,y)};drawPath(path,Green,style=Stroke(3f))}
}

@Composable
private fun TileEditor(initial:Tile,onSave:(Tile)->Unit,onClear:()->Unit,onClose:()->Unit){
 var type by remember{mutableStateOf(if(initial.type=="empty")"hotkey" else initial.type)}
 var title by remember{mutableStateOf(initial.title)}
 var icon by remember{mutableStateOf(initial.icon)}
 var action by remember{mutableStateOf(initial.action)}
 var command by remember{mutableStateOf(initial.commandId)}
 var expanded by remember{mutableStateOf(false)}
 AlertDialog(onDismissRequest=onClose,containerColor=Panel,title={Text("Плитка",color=Color.White)},text={
  Column(verticalArrangement=Arrangement.spacedBy(7.dp)){
   Box{OutlinedButton(onClick={expanded=true}){Text("Тип: $type")};DropdownMenu(expanded=expanded,onDismissRequest={expanded=false}){listOf("hotkey","macro","launch","media","volume","telemetry").forEach{DropdownMenuItem(text={Text(it)},onClick={type=it;expanded=false})}}}
   OutlinedTextField(title,{title=it},label={Text("Название")},singleLine=true)
   OutlinedTextField(icon,{icon=it},label={Text("Иконка / emoji")},singleLine=true)
   OutlinedTextField(action,{action=it},label={Text("Действие / клавиши")},singleLine=true)
   OutlinedTextField(command,{command=it},label={Text("ID разрешённой команды")},singleLine=true)
  }
 },confirmButton={TextButton(onClick={onSave(initial.copy(type=type,title=title,icon=icon,action=action,commandId=command))}) {Text("Сохранить")}},dismissButton={Row{TextButton(onClick=onClear){Text("Очистить")};TextButton(onClick=onClose){Text("Отмена")}}})
}
