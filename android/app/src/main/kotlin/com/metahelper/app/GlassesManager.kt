package com.metahelper.app

import android.content.Context
import android.util.Log
import com.meta.wearable.dat.core.Wearables
import com.meta.wearable.dat.core.types.RegistrationState
import kotlinx.coroutines.*
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
    
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())

    private var isGlassesConnected = false

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

    // OkHttp callbacks fire on a background thread; Toast must run on the main
    // thread or it throws (no Looper). Always post via the main dispatcher.
    private fun toast(msg: String, long: Boolean = false) {
        serviceScope.launch(Dispatchers.Main) {
            Toast.makeText(context, msg, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkRegistrationAndStart() {
        // Capture is driven by GalleryWatcher, not an SDK StreamSession (which
        // plays an "Experience Started" cue), so we only monitor connection state.
        monitorConnectionState()
    }

    private fun monitorConnectionState() {
        serviceScope.launch {
            Wearables.registrationState.collect { state ->
                // Registration state reflects optional Meta DAT pairing, not
                // capture readiness — gallery capture works whether or not the
                // glasses are "linked", so avoid an alarming "Disconnected" that
                // reads as if the app is broken.
                isGlassesConnected = (state is RegistrationState.Registered)
                updateStatus(
                    if (isGlassesConnected) "Glasses linked · watching for photos"
                    else "Watching gallery for new photos"
                )
            }
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
                // Play as soon as the answer arrives. Capture is via the gallery,
                // not a DAT session, so playback isn't gated on glasses
                // registration; MediaPlayer routes to whatever audio output is
                // active (phone speaker or connected glasses). Tap Replay to repeat.
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
        serviceScope.cancel()
    }
}
