package com.metahelper.shared

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
actual fun createAudioPlayer(context: Any): AudioPlayer