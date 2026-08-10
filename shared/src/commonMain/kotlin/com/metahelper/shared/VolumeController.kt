package com.metahelper.shared

/**
 * Interface for controlling device media volume during audio playback.
 * Allows setting a quiet volume for the response and restoring the user's
 * original volume afterward.
 */
interface VolumeController {
    fun setQuietVolume()
    fun restoreVolume()
}

/**
 * Factory for creating platform-specific VolumeController implementations.
 */
actual fun createVolumeController(context: Any): VolumeController