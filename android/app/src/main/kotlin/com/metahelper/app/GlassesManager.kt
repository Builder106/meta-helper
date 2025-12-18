package com.metahelper.app

import android.content.Context
import android.util.Log
import com.meta.wearable.mwdat.core.WearableManager
import com.meta.wearable.mwdat.camera.WearableCamera
import com.meta.wearable.mwdat.camera.CaptureCallback
import java.nio.ByteBuffer

/**
 * Manager class that coordinates the flow between the glasses and the backend.
 * Now integrated with the Meta Wearables Device Access Toolkit (DAT).
 */
class GlassesManager(
    private val context: Context,
    private val backendUrl: String = "http://10.0.2.2:8000"
) {
    private val apiClient = ApiClient(backendUrl)
    private val audioPlayer = AudioPlayer(context)
    private val volumeController = VolumeController(context)
    
    // Meta DAT components
    private val wearableManager = WearableManager.getInstance(context)
    private var wearableCamera: WearableCamera? = null

    init {
        setupGlassesConnection()
    }

    private fun setupGlassesConnection() {
        // Find and connect to the glasses
        wearableManager.discoverWearables { wearables ->
            val glasses = wearables.firstOrNull()
            if (glasses != null) {
                Log.d("GlassesManager", "Found glasses: ${glasses.name}")
                glasses.connect { result ->
                    if (result.isSuccess) {
                        Log.d("GlassesManager", "Connected to glasses!")
                        // Initialize camera access
                        val camera = WearableCamera.getInstance(glasses)
                        wearableCamera = camera
                        
                        // Register for hardware button captures
                        camera.registerCaptureListener(object : WearableCamera.CaptureListener {
                            override fun onImageCaptured(imageBuffer: ByteBuffer) {
                                val bytes = ByteArray(imageBuffer.remaining())
                                imageBuffer.get(bytes)
                                Log.d("GlassesManager", "Hardware button photo received. Processing...")
                                onPhotoCaptured(bytes)
                            }
                        })
                    } else {
                        Log.e("GlassesManager", "Connection failed: ${result.exceptionOrNull()}")
                    }
                }
            } else {
                Log.w("GlassesManager", "No glasses found nearby.")
            }
        }
    }

    /**
     * Trigger a photo capture using the actual glasses camera.
     */
    fun triggerPhotoCapture() {
        val camera = wearableCamera ?: run {
            Log.e("GlassesManager", "Camera not initialized. Is the device connected?")
            return
        }

        Log.d("GlassesManager", "Requesting photo capture from glasses...")
        camera.takePhoto(object : CaptureCallback {
            override fun onCaptureSuccess(imageBuffer: ByteBuffer) {
                // Convert ByteBuffer to ByteArray for our ApiClient
                val bytes = ByteArray(imageBuffer.remaining())
                imageBuffer.get(bytes)
                
                Log.d("GlassesManager", "Photo received (${bytes.size} bytes). Processing...")
                onPhotoCaptured(bytes)
            }

            override fun onCaptureFailure(error: Exception) {
                Log.e("GlassesManager", "Capture failed: ${error.message}")
            }
        })
    }

    /**
     * Process the photo and play the AI response.
     */
    private fun onPhotoCaptured(imageBytes: ByteArray) {
        // 1. Ensure volume is quiet
        volumeController.setQuietVolume()

        // 2. Send to backend
        apiClient.processImage(imageBytes, object : ApiClient.ApiResponseCallback {
            override fun onSuccess(audioBytes: ByteArray) {
                Log.d("GlassesManager", "Playing quiet AI response...")
                audioPlayer.playAudio(audioBytes)
            }

            override fun onError(message: String) {
                Log.e("GlassesManager", "Pipeline Error: $message")
            }
        })
    }

    fun stopAll() {
        audioPlayer.stop()
        wearableManager.disconnectAll()
    }
}
