package com.metahelper.app

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.metahelper.app.ui.theme.MetaHelperTheme
import com.meta.wearable.dat.core.Wearables
import com.meta.wearable.dat.core.types.Permission
import com.meta.wearable.dat.core.types.PermissionStatus

import android.widget.Toast

class MainActivity : ComponentActivity() {
    private var glassesManager by mutableStateOf<GlassesManager?>(null)
    private var statusMessage by mutableStateOf("Initializing...")
    private var isBound = false

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.all { it.value }) {
            statusMessage = "Permissions granted. Requesting Meta access..."
            requestMetaPermissions()
        } else {
            statusMessage = "Android permissions denied."
        }
    }

    private val metaPermissionLauncher = registerForActivityResult(
        Wearables.RequestPermissionContract()
    ) { result ->
        Log.d("MainActivity", "Meta Permission Result: $result")
        
        // Unwrap the result using the pattern found in GlassesManager.kt
        if (result.isSuccess) {
            val status = result.getOrThrow()
            if (status is PermissionStatus.Granted) {
                statusMessage = "Meta Permission Granted! Starting Service..."
                Toast.makeText(this, "Meta Permission Granted!", Toast.LENGTH_SHORT).show()
                startWearableService()
            } else {
                statusMessage = "Meta Permission Denied: $status"
                Log.e("MainActivity", "Meta permission denied: $status")
                Toast.makeText(this, "Permission Denied: $status", Toast.LENGTH_LONG).show()
            }
        } else {
            val error = result.exceptionOrNull()?.message ?: "Unknown Error"
            statusMessage = "Meta SDK Error: $error"
            Log.e("MainActivity", "Meta SDK Error: $error")
            Toast.makeText(this, "Meta SDK Error: $error", Toast.LENGTH_LONG).show()
        }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            Log.d("MainActivity", "Service Connected - updating glassesManager")
            val binder = service as WearableService.LocalBinder
            val manager = binder.getService().glassesManager
            
            manager.onStatusUpdate = { msg -> 
                Log.d("MainActivity", "UI Status update from manager: $msg")
                statusMessage = msg 
            }
            
            glassesManager = manager
            isBound = true
            Toast.makeText(this@MainActivity, "App connected to background service!", Toast.LENGTH_SHORT).show()
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            glassesManager = null
            isBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        checkAndRequestPermissions()

        setContent {
            MetaHelperTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    glassesManager?.let { MainScreen(it) } ?: LoadingScreen(statusMessage)
                }
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN,
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        val missingPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isEmpty()) {
            requestMetaPermissions()
        } else {
            requestPermissionLauncher.launch(missingPermissions.toTypedArray())
        }
    }

    private fun requestMetaPermissions() {
        // Request CAMERA permission through the Meta SDK
        // The contract expects a single Permission object, not an array
        metaPermissionLauncher.launch(Permission.CAMERA)
    }

    private fun startWearableService() {
        val intent = Intent(this, WearableService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        bindService(intent, connection, BIND_AUTO_CREATE)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            unbindService(connection)
            isBound = false
        }
    }
}

@Composable
fun LoadingScreen(status: String) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = status,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
    }
}

@Composable
fun MainScreen(manager: GlassesManager) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.Bluetooth,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
            )
            Text(
                text = "MetaHelper",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Smart Glasses Companion",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.secondary
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "GALLERY WATCHER ACTIVE",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color(0xFF4CAF50)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "1. Take a photo with your glasses.\n2. Meta AI will save it to 'Downloads/Meta AI'.\n3. MetaHelper will detect it and play the answer!",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Button(
            onClick = { manager.triggerPhotoCapture() },
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            shape = CircleShape
        ) {
            Icon(Icons.Default.CameraAlt, contentDescription = null)
            Spacer(modifier = Modifier.width(12.dp))
            Text("Capture Manually", style = MaterialTheme.typography.titleMedium)
        }

        OutlinedButton(
            onClick = { manager.replayLastAudio() },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = CircleShape
        ) {
            Icon(Icons.Default.Refresh, contentDescription = null)
            Spacer(modifier = Modifier.width(12.dp))
            Text("Replay Last Answer", style = MaterialTheme.typography.titleMedium)
        }

        Text(
            text = "Cloud: Connected to Render",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline
        )
    }
}
