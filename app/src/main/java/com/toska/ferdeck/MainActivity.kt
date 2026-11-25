package com.toska.ferdeck

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import java.io.BufferedReader
import java.io.InputStreamReader

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val vm: DeckViewModel = viewModel()
            val config by vm.config.collectAsState()
            SideEffect { applyWindowOptions(config.keepScreenOn, config.fullscreen) }
            val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
                if (uri != null) contentResolver.openOutputStream(uri)?.use { it.write(vm.exportJson().toByteArray(Charsets.UTF_8)) }
            }
            val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                if (uri != null) contentResolver.openInputStream(uri)?.use {
                    vm.importJson(BufferedReader(InputStreamReader(it, Charsets.UTF_8)).readText())
                }
            }
            MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFF35D07F), background = Color.Black, surface = Color(0xFF101010), onBackground = Color.White, onSurface = Color.White)) {
                FerDeckScreen(
                    vm = vm,
                    onExport = { export.launch("ferdeck-config.json") },
                    onImport = { import.launch(arrayOf("application/json", "text/*")) },
                    onKeepScreenOn = { applyWindowOptions(it, config.fullscreen) },
                    onFullscreen = { applyWindowOptions(config.keepScreenOn, it) }
                )
            }
        }
    }

    private fun applyWindowOptions(keepScreenOn: Boolean, fullscreen: Boolean) {
        if (keepScreenOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        if (fullscreen) {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else controller.show(WindowInsetsCompat.Type.systemBars())
    }
}