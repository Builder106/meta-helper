package com.metahelper.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.metahelper.app.ui.theme.MetaHelperTheme

class MainActivity : ComponentActivity() {
    private lateinit var glassesManager: GlassesManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize the manager (Change 10.0.2.2 to your local IP if testing on real device)
        glassesManager = GlassesManager(this, "http://10.0.2.2:8000")

        setContent {
            MetaHelperTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(onTestCapture = {
                        // Simulate a photo capture with a dummy byte array
                        // In reality, this would be the actual image from the glasses
                        val dummyImage = ByteArray(100) 
                        glassesManager.onPhotoCaptured(dummyImage)
                    })
                }
            }
        }
    }
}

@Composable
fun MainScreen(onTestCapture: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "MetaHelper",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "AI Companion for Meta Glasses",
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = onTestCapture,
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Text("Simulate Photo Capture")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Note: This will send a dummy request to the backend to test the audio pipeline.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.secondary
        )
    }
}
