package com.example.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun MainScreen(viewModel: NetSentryViewModel = viewModel()) {
    val result by viewModel.scanResult.collectAsState()

    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF121212)) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = "NetSentry Spoofer", color = Color.Green, style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(20.dp))
            
            Text(text = result, color = Color.White)
            
            Spacer(modifier = Modifier.height(20.dp))
            
            Button(onClick = { viewModel.startScanning("1.1.1.1", 443) }) {
                Text("شروع اسکن آی‌پی")
            }
        }
    }
}
