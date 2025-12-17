package com.metahelper.app

import android.content.Context
import android.media.AudioManager
import android.util.Log

class VolumeController(private val context: Context) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    fun setQuietVolume() {
        try {
            // Set media volume to a very low level (index 1)
            // This ensures that even if the backend scaling isn't enough, 
            // the hardware output is also capped.
            audioManager.setStreamVolume(
                AudioManager.STREAM_MUSIC,
                1, // Lowest audible level
                0
            )
            Log.d("VolumeController", "Volume set to quiet (index 1)")
        } catch (e: Exception) {
            Log.e("VolumeController", "Failed to set volume: ${e.message}")
        }
    }
}

