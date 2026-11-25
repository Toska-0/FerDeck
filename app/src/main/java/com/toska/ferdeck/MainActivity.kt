package com.toska.ferdeck

import android.content.Context
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel
import java.io.BufferedReader
import java.io.InputStreamReader

class MainActivity:ComponentActivity(){
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState)
  if(getSharedPreferences("ferdeck",Context.MODE_PRIVATE).getBoolean("keep_screen",true))window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
  setContent{
   val vm:DeckViewModel=viewModel()
   val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")){uri->if(uri!=null)contentResolver.openOutputStream(uri)?.use{it.write(vm.exportJson().toByteArray())}}
   val import=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->if(uri!=null)contentResolver.openInputStream(uri)?.use{vm.importJson(BufferedReader(InputStreamReader(it)).readText())}}
   MaterialTheme(colorScheme=darkColorScheme(primary=Color(0xFF35D07F),background=Color.Black,surface=Color(0xFF101010))){FerDeckScreen(vm,{export.launch("ferdeck-config.json")},{import.launch(arrayOf("application/json","text/*"))})}
  }
 }
}
