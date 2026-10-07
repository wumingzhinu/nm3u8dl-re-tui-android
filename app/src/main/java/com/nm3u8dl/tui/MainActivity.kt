package com.nm3u8dl.tui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nm3u8dl.tui.ui.AppViewModel
import com.nm3u8dl.tui.ui.TerminalApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val shared: AppViewModel = viewModel()
        val launchUrl = intent?.data?.toString()?.takeIf { it.startsWith("http") }
        if (launchUrl != null) {
            shared.onEvent(com.nm3u8dl.tui.ui.Event.UrlChanged(launchUrl))
        }
        setContent {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0C0C0C))
                    .statusBarsPadding()
                    .navigationBarsPadding(),
            ) {
                TerminalApp(vm = shared)
            }
        }
    }
}
