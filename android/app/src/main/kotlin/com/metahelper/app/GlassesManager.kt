package com.metahelper.app

import android.content.Context
import android.util.Log
import com.meta.wearable.dat.core.  Wearables
import com.meta.wearable.dat.camera.StreamSession
import com.meta.wearable.dat.camera.startStreamSession
import com.meta.wearable.dat.camera.types.PhotoData
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream

/**
 * Manager class that coordinates the flow between the glasses and the backend.
 * Updated for Meta Wearables SDK 0.3.0 using the StreamSession API.
 */
import com.meta.wearable.dat.core.RegistrationState
import com.meta.wearable.dat.camera.WearableCamera

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

    init {
        val result = Wearables.initialize(context)
        if (result.isSuccess) {
            Log.d("GlassesManager", "Meta Wearables SDK 0.3.0 Initialized")
            checkRegistrationAndStart()
        } else {
            Log.e("GlassesManager", "SDK Initialization failed: ${result.exceptionOrNull()?.message}")
        }
    }

    private fun checkRegistrationAndStart() {
        serviceScope.launch {
            // Check if the app is already registered with the Meta AI app
            Wearables.registrationState.collect { state ->
                when (state) {
                    RegistrationState.REGISTERED -> {
                        Log.d("GlassesManager", "App is REGISTERED. Starting session...")
                        startSession()
                    }
                    RegistrationState.UNREGISTERED -> {
                        Log.d("GlassesManager", "App is UNREGISTERED. Opening Meta AI for registration...")
                        Wearables.startRegistration(context)
                    }
                    else -> Log.d("GlassesManager", "Registration state: $state")
                }
            }
        }
    }

    private fun startSession() {
        Log.d("GlassesManager", "Initializing session with AutoDeviceSelector...")
        val deviceSelector = com.meta.wearable.dat.core.selectors.AutoDeviceSelector { _, _ -> 0 }
        streamSession = Wearables.startStreamSession(context, deviceSelector)

        serviceScope.launch {
            streamSession?.state?.collect { state ->
                Log.d("GlassesManager", "Session state changed to: $state")
                // When we transition to STREAMING, we should also register for 
                // hardware button events specifically.
                if (state.toString() == "STREAMING") {
                    setupHardwareButtonListener()
                }
            }
        }
    }

    private fun setupHardwareButtonListener() {
        // In 0.3.0, you can get the camera from the session to listen for button events
        // This bypasses the Meta AI gallery import
        try {
            val camera = WearableCamera.getInstance(Wearables.devices.value.first())
            camera.registerCaptureListener(object : WearableCamera.CaptureListener {
                override fun onImageCaptured(imageBuffer: java.nio.ByteBuffer) {
                    val bytes = ByteArray(imageBuffer.remaining())
                    imageBuffer.get(bytes)
                    Log.d("GlassesManager", "INSTANT CAPTURE from button. Processing...")
                    onPhotoCaptured(bytes)
                }
            })
        } catch (e: Exception) {
            Log.e("GlassesManager", "Failed to setup hardware button listener: ${e.message}")
        }
    }

    private fun convertBitmapToByteArray(bitmap: android.graphics.Bitmap): ByteArray {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, outputStream)
        return outputStream.toByteArray()
    }

    /**
     * Trigger a photo capture using the StreamSession.capturePhoto suspend function.
     */
    fun triggerPhotoCapture() {
        val session = streamSession ?: run {
            Log.e("GlassesManager", "StreamSession not initialized.")
            return
        }

        serviceScope.launch {
            Log.d("GlassesManager", "Capturing photo via StreamSession...")
            // 0.3.0 Guideline: capturePhoto is a suspend function returning Result<PhotoData>
            val result = session.capturePhoto()

            if (result.isSuccess) {
                val photoData = result.getOrThrow()
                when (photoData) {
                    is PhotoData.HEIC -> {
                        val bytes = photoData.data.array() // Extract HEIC data as ByteArray
                        Log.d("GlassesManager", "Photo captured in HEIC format (${bytes.size} bytes). Processing...")
                        onPhotoCaptured(bytes)
                    }
                    is PhotoData.Bitmap -> {
                        val bitmap = photoData.bitmap // Extract Android Bitmap
                        Log.d("GlassesManager", "Photo captured as Bitmap (${bitmap.width}x${bitmap.height}). Processing...")
                        // Here you can save or process the bitmap (e.g., converting to JPEG or PNG)
                        val bytes = convertBitmapToByteArray(bitmap) // Convert bitmap to ByteArray
                        onPhotoCaptured(bytes)
                    }
                    else -> {
                        Log.e("GlassesManager", "Unsupported PhotoData format: ${photoData.javaClass.name}")
                    }
                }
            } else {
                Log.e("GlassesManager", "Capture failed: ${result.exceptionOrNull()?.message}")
            }
        }
    }

    private fun onPhotoCaptured(imageBytes: ByteArray) {
        volumeController.setQuietVolume()
        apiClient.processImage(imageBytes, object : ApiClient.ApiResponseCallback {
            override fun onSuccess(audioBytes: ByteArray) {
                lastAudioResponse = audioBytes
                Log.d("GlassesManager", "Playing AI response...")
                audioPlayer.playAudio(audioBytes)
            }
            override fun onError(message: String) {
                Log.e("GlassesManager", "Pipeline Error: $message")
            }
        })
    }

    fun replayLastAudio() {
        lastAudioResponse?.let {
            Log.d("GlassesManager", "Replaying last audio response...")
            audioPlayer.playAudio(it)
        } ?: Log.w("GlassesManager", "No audio to replay")
    }

    fun stopAll() {
        audioPlayer.release()
        streamSession?.close()
        serviceScope.cancel()
    }
}
