package com.metahelper.shared

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

/**
 * Core manager that coordinates the flow between the glasses/camera and the backend.
 * Handles gallery watching, API communication, audio playback, and volume management.
 */
interface GlassesManager {
    var onStatusUpdate: ((String) -> Unit)?
    var onConnectionStateChange: ((ConnectionState) -> Unit)?

    fun replayLastAudio()
    fun stopAll()
}

/**
 * Factory for creating platform-specific GlassesManager implementations.
 */
expect fun createGlassesManager(backendUrl: String, context: Any): GlassesManager

/**
 * Platform-specific image loading from URI/identifier.
 * Actual implementation in platform-specific source sets.
 */
expect fun loadImageBytes(imageUri: Any, callback: (ByteArray?) -> Unit)

/**
 * Interface for playing audio responses from the backend.
 * Platform-specific implementations handle the actual audio playback.
 */
interface AudioPlayer {
    fun playAudio(audioBytes: ByteArray, onComplete: () -> Unit = {})
    fun stop()
    fun release()

    var onReplayRequested: (() -> Unit)?
}

/**
 * Factory for creating platform-specific AudioPlayer implementations.
 */
expect fun createAudioPlayer(context: Any): AudioPlayer

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
expect fun createVolumeController(context: Any): VolumeController

/**
 * Interface for monitoring Meta Wearables SDK connection state.
 * Android implementation uses mwdat-core; iOS implementation can use mwdat-ios when available.
 */
interface WearablesConnectionMonitor {
    val connectionState: StateFlow<ConnectionState>

    fun start()
    fun stop()
}

sealed interface ConnectionState {
    data class Connected(val applicationId: String) : ConnectionState
    object Disconnected : ConnectionState
    data class Error(val message: String) : ConnectionState
}

/**
 * Factory for creating platform-specific WearablesConnectionMonitor implementations.
 */
expect fun createWearablesConnectionMonitor(context: Any): WearablesConnectionMonitor

/**
 * Interface for watching the photo gallery for new Meta AI / Ray-Ban photos.
 */
interface GalleryWatcher {
    fun startWatching()
    fun stopWatching()
}

/**
 * Factory for creating platform-specific GalleryWatcher implementations.
 */
expect fun createGalleryWatcher(context: Any, onNewImageDetected: (String) -> Unit): GalleryWatcher

/**
 * Logging utility (expect/actual for platform-specific logging)
 */
expect fun logDebug(tag: String, msg: String)
expect fun logError(tag: String, msg: String)

/**
 * Platform-specific toast/logging
 */
expect fun toast(msg: String, long: Boolean)