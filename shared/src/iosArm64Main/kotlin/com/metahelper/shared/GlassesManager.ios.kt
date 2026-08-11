package com.metahelper.shared

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import platform.Foundation.NSLog
import platform.Foundation.NSPredicate
import platform.Foundation.NSString
import platform.Foundation.getBytes
import platform.Photos.PHAsset
import platform.Photos.PHFetchOptions
import platform.Photos.PHImageManager
import platform.Photos.PHImageRequestOptions
import platform.Photos.PHImageRequestOptionsDeliveryModeHighQualityFormat
import platform.Photos.PHImageRequestOptionsResizeModeNone
import platform.UIKit.UIAlertAction
import platform.UIKit.UIAlertActionStyleDefault
import platform.UIKit.UIAlertController
import platform.UIKit.UIAlertControllerStyleAlert
import platform.UIKit.UIApplication

internal class GlassesManagerImpl(
    private val backendUrl: String = "https://metahelper.onrender.com",
    private val context: Any
) : GlassesManager {
    private val apiClient = ApiClient(backendUrl)
    private val audioPlayer = createAudioPlayer(context)
    private val volumeController = createVolumeController(context)
    private val galleryWatcher = createGalleryWatcher(context) { imageUri ->
        onNewGalleryImage(imageUri)
    }
    private val wearablesMonitor = createWearablesConnectionMonitor(context)
    private var lastAudioResponse: ByteArray? = null
    private var lastProcessedUri: Any? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())

    override var onStatusUpdate: ((String) -> Unit)? = null
    override var onConnectionStateChange: ((ConnectionState) -> Unit)? = null

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
        logDebug("GlassesManager", "Processing new gallery image: $imageUri")
        processGalleryImage(imageUri)
    }

    private fun processGalleryImage(imageUri: Any) {
        updateStatus("New photo detected! Reading data...")
        loadImageBytes(imageUri) { bytes ->
            if (bytes != null) {
                logDebug("GlassesManager", "Successfully read ${bytes.size} bytes from gallery")
                onPhotoCaptured(bytes)
            } else {
                updateStatus("Error: Could not read photo data.")
            }
        }
    }

    private fun updateStatus(msg: String) {
        logDebug("GlassesManager", "UI STATUS UPDATE: $msg")
        serviceScope.launch(Dispatchers.Main.immediate) {
            onStatusUpdate?.invoke(msg)
        }
    }

    private fun onPhotoCaptured(imageBytes: ByteArray) {
        logDebug("GlassesManager", "onPhotoCaptured called with ${imageBytes.size} bytes")
        toast("Sending photo to AI...", false)
        volumeController.setQuietVolume()

        logDebug("GlassesManager", "Calling apiClient.processImage")
        apiClient.processImage(imageBytes, object : ApiClient.ApiResponseCallback {
            override fun onSuccess(audioBytes: ByteArray) {
                logDebug("GlassesManager", "apiClient success: received ${audioBytes.size} bytes")
                lastAudioResponse = audioBytes
                toast("AI Answer Ready!", false)
                logDebug("GlassesManager", "Playing answer.")
                updateStatus("Playing answer...")
                audioPlayer.playAudio(audioBytes) { volumeController.restoreVolume() }
            }
            override fun onError(message: String) {
                logError("GlassesManager", "apiClient error: $message")
                toast("AI Error: $message", true)
                volumeController.restoreVolume()
                updateStatus("AI Error: $message")
            }
        })
    }

    override fun replayLastAudio() {
        lastAudioResponse?.let {
            logDebug("GlassesManager", "Replaying last explanation...")
            audioPlayer.playAudio(it)
        }
    }

    override fun stopAll() {
        audioPlayer.release()
        galleryWatcher.stopWatching()
        wearablesMonitor.stop()
        serviceScope.cancel()
    }
}

actual fun logDebug(tag: String, msg: String) {
    NSLog("%s: %s", tag, msg)
}

actual fun logError(tag: String, msg: String) {
    NSLog("%s ERROR: %s", tag, msg)
}

actual fun createGlassesManager(backendUrl: String, context: Any): GlassesManager {
    return GlassesManagerImpl(backendUrl, context)
}

// Platform-specific image loading from URI/identifier
@OptIn(ExperimentalForeignApi::class)
actual fun loadImageBytes(imageUri: Any, callback: (ByteArray?) -> Unit) {
    val identifier = imageUri as? String ?: return callback(null)

    val fetchOptions = PHFetchOptions()
    fetchOptions.predicate = NSPredicate.predicateWithFormat("localIdentifier == %@", identifier as NSString)

    val assets = PHAsset.fetchAssetsWithOptions(fetchOptions)
    if (assets.count == 0uL) {
        logError("GlassesManager", "No asset found for identifier: $identifier")
        return callback(null)
    }

    val asset = assets.firstObject as? PHAsset ?: return callback(null)

    val requestOptions = PHImageRequestOptions().apply {
        synchronous = true
        deliveryMode = PHImageRequestOptionsDeliveryModeHighQualityFormat
        resizeMode = PHImageRequestOptionsResizeModeNone
    }

    PHImageManager.defaultManager().requestImageDataAndOrientationForAsset(
        asset,
        requestOptions
    ) { data, _, _, info ->
        if (data != null) {
            val bytes = ByteArray(data.length.toInt())
            if (bytes.isNotEmpty()) {
                bytes.usePinned { pinned ->
                    data.getBytes(pinned.addressOf(0))
                }
            }
            callback(bytes)
        } else {
            val errorInfo = info?.get("PHImageErrorKey")
            logError("GlassesManager", "Failed to get image data: ${errorInfo ?: "unknown"}")
            callback(null)
        }
    }
}

actual fun toast(msg: String, long: Boolean) {
    val alert = UIAlertController.alertControllerWithTitle(
        title = null,
        message = msg,
        preferredStyle = UIAlertControllerStyleAlert
    )
    alert.addAction(UIAlertAction.actionWithTitle("OK", style = UIAlertActionStyleDefault, handler = null))

    val window = UIApplication.sharedApplication.keyWindow
    val rootVC = window?.rootViewController
    rootVC?.presentViewController(alert, animated = true, completion = null)

    if (!long) {
        CoroutineScope(Dispatchers.Main.immediate).launch {
            delay(2000)
            alert.dismissViewControllerAnimated(true, completion = null)
        }
    }
}
