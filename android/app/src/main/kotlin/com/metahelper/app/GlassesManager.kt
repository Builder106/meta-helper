package com.metahelper.app

import android.content.Context
import android.util.Log
import com.meta.wearable.dat.core.Wearables
import com.meta.wearable.dat.core.types.RegistrationState
import com.meta.wearable.dat.core.selectors.AutoDeviceSelector
import com.meta.wearable.dat.camera.StreamSession
import com.meta.wearable.dat.camera.startStreamSession
import com.meta.wearable.dat.camera.types.PhotoData
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream

import android.net.Uri
import android.widget.Toast
import java.io.InputStream

/**
 * Manager class that coordinates the flow between the glasses and the backend.
 * Now updated to support Gallery Watcher trigger.
 */
class GlassesManager(
    private val context: Context,
    private val backendUrl: String = "https://metahelper.onrender.com"
) {
    private val apiClient = ApiClient(backendUrl)
    private val audioPlayer = AudioPlayer(context).apply {
        onReplayRequested = { replayLastAudio() }
    }
    private val volumeController = VolumeController(context)
    private var lastAudioResponse: ByteArray? = null
    
    private var streamSession: StreamSession? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())

    private var pendingAudioResponse: ByteArray? = null
    private var isStreaming = false

    private var lastProcessedUri: Uri? = null

    private val galleryWatcher = GalleryWatcher(context) { uri ->
        if (uri != lastProcessedUri) {
            lastProcessedUri = uri
            Log.d("GlassesManager", "Processing new gallery image: $uri")
            processGalleryImage(uri)
        }
    }

    var onStatusUpdate: ((String) -> Unit)? = null

    init {
        updateStatus("Initializing SDK...")
        val result = Wearables.initialize(context)
        if (result.isSuccess) {
            Log.d("GlassesManager", "Meta Wearables SDK 0.3.0 Initialized")
            updateStatus("SDK Initialized. Monitoring gallery for new photos...")
            checkRegistrationAndStart()
            galleryWatcher.startWatching()
        } else {
            val error = result.exceptionOrNull()?.message ?: "Unknown Error"
            Log.e("GlassesManager", "SDK Initialization failed: $error")
            updateStatus("SDK Error: $error")
        }
    }

    private fun processGalleryImage(uri: Uri) {
        Log.d("GlassesManager", "Starting processGalleryImage for URI: $uri")
        updateStatus("New photo detected! Reading data...")
        try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            if (inputStream == null) {
                Log.e("GlassesManager", "InputStream is null for URI: $uri")
                updateStatus("Error: Could not open photo stream.")
                return
            }
            val bytes = inputStream.readBytes()
            inputStream.close()
            Log.d("GlassesManager", "Successfully read ${bytes.size} bytes from gallery")
            onPhotoCaptured(bytes)
        } catch (e: Exception) {
            val errorMsg = "Gallery process error: ${e.message}"
            Log.e("GlassesManager", errorMsg)
            updateStatus("Error reading photo: ${e.message}")
        }
    }

    private fun updateStatus(msg: String) {
        Log.d("GlassesManager", "UI STATUS UPDATE: $msg")
        serviceScope.launch(Dispatchers.Main) {
            onStatusUpdate?.invoke(msg)
        }
    }

    private fun checkRegistrationAndStart() {
        // No longer starting a full StreamSession to avoid "Experience Started" sounds
        monitorConnectionState()
    }

    private fun monitorConnectionState() {
        serviceScope.launch {
            Wearables.registrationState.collect { state ->
                // Registered means the phone and glasses are communicating
                isGlassesConnected = (state is RegistrationState.Registered)
                updateStatus(if (isGlassesConnected) "Glasses Connected" else "Glasses Disconnected")
                
                if (isGlassesConnected && pendingAudioResponse != null) {
                    Log.d("GlassesManager", "Glasses detected. Playing pending answer...")
                    audioPlayer.playAudio(pendingAudioResponse!!)
                    pendingAudioResponse = null
                }
            }
        }
    }

    private fun startSession() {
        // Function removed to prevent audio cues
    }

    fun triggerPhotoCapture() {
        updateStatus("Manual capture disabled in Gallery Mode.")
    }

    private fun processPhotoData(photoData: PhotoData) {
        when (photoData) {
            is PhotoData.HEIC -> {
                Log.d("GlassesManager", "Processing HEIC photo")
                onPhotoCaptured(photoData.data.array())
            }
            is PhotoData.Bitmap -> {
                Log.d("GlassesManager", "Processing Bitmap photo")
                val outputStream = ByteArrayOutputStream()
                photoData.bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, outputStream)
                onPhotoCaptured(outputStream.toByteArray())
            }
            else -> Log.e("GlassesManager", "Unknown PhotoData type")
        }
    }

    private fun onPhotoCaptured(imageBytes: ByteArray) {
        Log.d("GlassesManager", "onPhotoCaptured called with ${imageBytes.size} bytes")
        Toast.makeText(context, "Sending photo to AI...", Toast.LENGTH_SHORT).show()
        volumeController.setQuietVolume()
        
        Log.d("GlassesManager", "Calling apiClient.processImage")
        apiClient.processImage(imageBytes, object : ApiClient.ApiResponseCallback {
            override fun onSuccess(audioBytes: ByteArray) {
                Log.d("GlassesManager", "apiClient success: received ${audioBytes.size} bytes")
                lastAudioResponse = audioBytes
                Toast.makeText(context, "AI Answer Ready!", Toast.LENGTH_SHORT).show()
                
                if (isGlassesConnected) {
                    Log.d("GlassesManager", "Glasses ARE connected. Playing audio immediately.")
                    audioPlayer.playAudio(audioBytes)
                } else {
                    Log.d("GlassesManager", "Glasses NOT connected. Saving to pending.")
                    pendingAudioResponse = audioBytes
                    updateStatus("Answer Ready! Waiting for glasses connection...")
                }
            }
            override fun onError(message: String) {
                Log.e("GlassesManager", "apiClient error: $message")
                Toast.makeText(context, "AI Error: $message", Toast.LENGTH_LONG).show()
                updateStatus("AI Error: $message")
            }
        })
    }

    fun replayLastAudio() {
        lastAudioResponse?.let {
            Log.d("GlassesManager", "Replaying last explanation...")
            audioPlayer.playAudio(it)
        }
    }

    fun stopAll() {
        audioPlayer.release()
        streamSession?.close() // As per StreamSession interface
        galleryWatcher.stopWatching()
        serviceScope.cancel()
    }
}
