package com.nm3u8dl.tui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nm3u8dl.tui.ui.AppViewModel
import com.nm3u8dl.tui.ui.TerminalRoot

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val sharedUrl = intent?.data?.toString()?.takeIf { it.startsWith("http") }
        setContent {
            val vm: AppViewModel = viewModel(factory = AppViewModel.Factory)
            LaunchedEffect(sharedUrl) {
                if (sharedUrl != null) {
                    vm.onUrlChange(sharedUrl)
                    vm.probe()
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding(),
            ) {
                TerminalRoot(vm)
            }
        }
    }
}