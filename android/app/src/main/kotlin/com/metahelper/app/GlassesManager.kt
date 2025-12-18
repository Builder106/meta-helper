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
        updateStatus("New photo detected! Solving...")
        try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            val bytes = inputStream?.readBytes()
            if (bytes != null) {
                onPhotoCaptured(bytes)
            } else {
                updateStatus("Error: Could not read photo data.")
            }
        } catch (e: Exception) {
            Log.e("GlassesManager", "Gallery process error: ${e.message}")
            updateStatus("Error reading photo: ${e.message}")
        }
    }

    private fun updateStatus(msg: String) {
        Log.d("GlassesManager", "STATUS: $msg")
        serviceScope.launch(Dispatchers.Main) {
            onStatusUpdate?.invoke(msg)
        }
    }

    private fun checkRegistrationAndStart() {
        serviceScope.launch {
            Wearables.registrationState.collect { state ->
                when (state) {
                    is RegistrationState.Registered -> {
                        updateStatus("App Registered. Starting session...")
                        startSession()
                    }
                    is RegistrationState.Available -> {
                        updateStatus("ACTION REQUIRED: In Meta AI app, go to Settings -> App Access -> Enable 'POV Access' for MetaHelper")
                        Wearables.startRegistration(context)
                    }
                    else -> updateStatus("Registration state: ${state.javaClass.simpleName}")
                }
            }
        }
    }

    private fun startSession() {
        updateStatus("Searching for glasses...")
        val deviceSelector = AutoDeviceSelector { _, _ -> 0 }
        
        try {
            streamSession = Wearables.startStreamSession(context, deviceSelector)
            serviceScope.launch {
                streamSession?.state?.collect { state ->
                    val stateName = state.javaClass.simpleName
                    updateStatus("Stream state: $stateName")
                    
                    isStreaming = (stateName == "Streaming")
                    
                    // If we have an answer waiting and the glasses just reconnected, play it!
                    if (isStreaming && pendingAudioResponse != null) {
                        Log.d("GlassesManager", "Glasses reconnected. Playing pending answer...")
                        audioPlayer.playAudio(pendingAudioResponse!!)
                        pendingAudioResponse = null
                    }
                }
            }
        } catch (e: Exception) {
            updateStatus("Session Error: ${e.message}")
        }
    }

    /**
     * Trigger a photo capture using the StreamSession API.
     * This captures the POV image from the glasses.
     */
    fun triggerPhotoCapture() {
        val session = streamSession ?: run {
            Log.e("GlassesManager", "Session not active.")
            return
        }

        serviceScope.launch {
            Log.d("GlassesManager", "Capturing photo...")
            // As per StreamSession interface: capturePhoto() returns Result<PhotoData>
            val result = session.capturePhoto()
            
            if (result.isSuccess) {
                val photoData = result.getOrThrow()
                processPhotoData(photoData)
            } else {
                Log.e("GlassesManager", "Capture failed: ${result.exceptionOrNull()?.message}")
            }
        }
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
        Toast.makeText(context, "Sending photo to AI...", Toast.LENGTH_SHORT).show()
        volumeController.setQuietVolume()
        apiClient.processImage(imageBytes, object : ApiClient.ApiResponseCallback {
            override fun onSuccess(audioBytes: ByteArray) {
                lastAudioResponse = audioBytes
                Toast.makeText(context, "AI Answer Ready!", Toast.LENGTH_SHORT).show()
                
                if (isStreaming) {
                    Log.d("GlassesManager", "AI Solution ready. Playing...")
                    audioPlayer.playAudio(audioBytes)
                } else {
                    Log.d("GlassesManager", "AI Solution ready, but glasses disconnected. Saving for later.")
                    pendingAudioResponse = audioBytes
                    updateStatus("Answer Ready! Waiting for glasses connection...")
                }
            }
            override fun onError(message: String) {
                Log.e("GlassesManager", "Pipeline Error: $message")
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
