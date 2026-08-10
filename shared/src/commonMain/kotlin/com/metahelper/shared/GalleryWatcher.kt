package com.metahelper.shared

import kotlinx.coroutines.CoroutineScope

/**
 * Interface for watching the device's photo gallery for new images
 * taken with Meta Ray-Ban glasses.
 */
interface GalleryWatcher {
    fun startWatching()
    fun stopWatching()
}

/**
 * Factory for creating platform-specific GalleryWatcher implementations.
 * Actual implementations are in platform-specific source sets.
 */
actual fun createGalleryWatcher(
    context: Any,
    onNewImageDetected: (Any) -> Unit
): GalleryWatcher