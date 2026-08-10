package com.metahelper.ios

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metahelper.shared.ConnectionState
import com.metahelper.shared.GlassesManager

@Composable
fun MainScreen(glassesManager: GlassesManager) {
    var status by remember { mutableStateOf("Initializing...") }
    var connectionState by remember { mutableStateOf<ConnectionState>(ConnectionState.Disconnected) }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        glassesManager.onStatusUpdate = { msg ->
            status = msg
        }
        glassesManager.onConnectionStateChange = { state ->
            connectionState = state
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "MetaHelper",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold
        )
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(16.dp))
        Text(
            text = "Audio-first programming assistant\nfor Meta Ray-Ban glasses",
            fontSize = 16.sp,
            textAlign = androidx.compose.ui.text.TextAlign.Center
        )
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(24.dp))

        // Connection state
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Glasses Connection",
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = when (connectionState) {
                    is ConnectionState.Connected -> "Connected (App ID: ${connectionState.applicationId})"
                    ConnectionState.Disconnected -> "Disconnected"
                    is ConnectionState.Error -> "Error: ${connectionState.message}"
                },
                fontSize = 14.sp,
                color = when (connectionState) {
                    is ConnectionState.Connected -> androidx.compose.ui.graphics.Color.Green
                    ConnectionState.Disconnected -> androidx.compose.ui.graphics.Color.Gray
                    is ConnectionState.Error -> androidx.compose.ui.graphics.Color.Red
                }
            )
        }

        androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(24.dp))

        // Status
        Text(
            text = status,
            fontSize = 14.sp,
            color = androidx.compose.ui.graphics.Color.Gray
        )

        androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(24.dp))

        // Instructions
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "How to use:",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "1. Take a photo of code with your Meta Ray-Ban glasses\n2. The photo appears in your gallery\n3. MetaHelper reads it and explains it aloud\n4. Double-tap / next-track to replay",
                fontSize = 14.sp,
                textAlign = androidx.compose.ui.text.TextAlign.Center
            )
        }

        androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(32.dp))

        Button(
            onClick = { glassesManager.replayLastAudio() },
            modifier = Modifier.padding(16.dp)
        ) {
            Text(text = "Replay Last Answer", fontSize = 16.sp)
        }
    }
}