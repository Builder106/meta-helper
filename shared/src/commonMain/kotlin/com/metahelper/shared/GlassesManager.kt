package com.metahelper.shared

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect

/**
 * Core manager that coordinates the flow between the glasses/camera and the backend.
 * Handles gallery watching, API communication, audio playback, and volume management.
 */
class GlassesManager(
    private val backendUrl: String = "https://metahelper.onrender.com",
    private val context: Any // Platform-specific context (Android Context, iOS nil)
) {
    private val apiClient = ApiClient(backendUrl)
    private val audioPlayer = createAudioPlayer(context)
    private val volumeController = createVolumeController(context)
    private val galleryWatcher = createGalleryWatcher(context) { imageUri ->
        onNewGalleryImage(imageUri)
    }
    private val wearablesMonitor = createWearablesConnectionMonitor(context)
    private var lastAudioResponse: ByteArray? = null
    private var lastProcessedUri: Any? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    var onStatusUpdate: ((String) -> Unit)? = null
    var onConnectionStateChange: ((ConnectionState) -> Unit)? = null

    init {
        updateStatus("Initializing...")
        start()
    }

    private fun start() {
        updateStatus("Monitoring gallery for new photos...")
        galleryWatcher.startWatching()
        wearablesMonitor.start()
        observeConnectionState()
    }

    private fun observeConnectionState() {
        serviceScope.launch {
            wearablesMonitor.connectionState.collect { state ->
                onConnectionStateChange?.invoke(state)
            }
        }
    }

    private fun onNewGalleryImage(imageUri: Any) {
        if (imageUri == lastProcessedUri) return
        lastProcessedUri = imageUri
        Log.d("GlassesManager", "Processing new gallery image: $imageUri")
        processGalleryImage(imageUri)
    }

    private fun processGalleryImage(imageUri: Any) {
        updateStatus("New photo detected! Reading data...")
        // Platform-specific image loading
        loadImageBytes(imageUri) { bytes ->
            if (bytes != null) {
                Log.d("GlassesManager", "Successfully read ${bytes.size} bytes from gallery")
                onPhotoCaptured(bytes)
            } else {
                updateStatus("Error: Could not read photo data.")
            }
        }
    }

    /**
     * Platform-specific image loading from URI/identifier.
     * Actual implementation in platform-specific source sets.
     */
    expect fun loadImageBytes(imageUri: Any, callback: (ByteArray?) -> Unit)

    private fun updateStatus(msg: String) {
        Log.d("GlassesManager", "UI STATUS UPDATE: $msg")
        serviceScope.launch(Dispatchers.Main) {
            onStatusUpdate?.invoke(msg)
        }
    }

    private fun onPhotoCaptured(imageBytes: ByteArray) {
        Log.d("GlassesManager", "onPhotoCaptured called with ${imageBytes.size} bytes")
        toast("Sending photo to AI...")
        volumeController.setQuietVolume()

        Log.d("GlassesManager", "Calling apiClient.processImage")
        apiClient.processImage(imageBytes, object : ApiClient.ApiResponseCallback {
            override fun onSuccess(audioBytes: ByteArray) {
                Log.d("GlassesManager", "apiClient success: received ${audioBytes.size} bytes")
                lastAudioResponse = audioBytes
                toast("AI Answer Ready!")
                Log.d("GlassesManager", "Playing answer.")
                updateStatus("Playing answer...")
                audioPlayer.playAudio(audioBytes) { volumeController.restoreVolume() }
            }
            override fun onError(message: String) {
                Log.e("GlassesManager", "apiClient error: $message")
                toast("AI Error: $message", long = true)
                volumeController.restoreVolume()
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
        galleryWatcher.stopWatching()
        wearablesMonitor.stop()
        serviceScope.cancel()
    }

    // Platform-specific toast/logging
    private expect fun toast(msg: String, long: Boolean = false)
}

/**
 * Logging utility (expect/actual for platform-specific logging)
 */
internal object Log {
    expect fun d(tag: String, msg: String)
    expect fun e(tag: String, msg: String)
}