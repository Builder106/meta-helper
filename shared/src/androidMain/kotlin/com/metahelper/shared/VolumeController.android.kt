package com.metahelper.shared

import android.content.Context
import android.media.AudioManager
import android.util.Log

internal class AndroidVolumeController(
    private val context: Context
) : VolumeController {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var previousVolume: Int? = null

    override fun setQuietVolume() {
        try {
            if (previousVolume == null) {
                previousVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            }
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 1, 0)
            Log.d("VolumeController", "Volume set to quiet (index 1), was $previousVolume")
        } catch (e: Exception) {
            Log.e("VolumeController", "Failed to set volume: ${e.message}")
        }
    }

    override fun restoreVolume() {
        try {
            previousVolume?.let {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, it, 0)
                Log.d("VolumeController", "Volume restored to $it")
            }
            previousVolume = null
        } catch (e: Exception) {
            Log.e("VolumeController", "Failed to restore volume: ${e.message}")
        }
    }
}

actual fun createVolumeController(context: Any): VolumeController {
    return AndroidVolumeController(context as Context)
}