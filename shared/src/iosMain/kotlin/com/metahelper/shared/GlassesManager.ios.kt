package com.metahelper.shared

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.NSData
import platform.Foundation.NSURL
import platform.Foundation.NSString
import platform.Photos.PHAsset
import platform.Photos.PHImageManager
import platform.Photos.PHImageRequestOptions
import platform.Photos.PHImageRequestOptionsDeliveryMode
import platform.Photos.PHImageRequestOptionsResizeMode

actual class GlassesManager(
    backendUrl: String = "https://metahelper.onrender.com",
    context: Any = Unit
) : GlassesManager(backendUrl, context) {

    override fun loadImageBytes(imageUri: Any, callback: (ByteArray?) -> Unit) {
        val assetIdentifier = imageUri as String
        withContext(Dispatchers.IO) {
            val options = PHImageRequestOptions().apply {
                isSynchronous = true
                deliveryMode = PHImageRequestOptionsDeliveryMode.HighQualityFormat
                resizeMode = PHImageRequestOptionsResizeMode.None
            }

            val assets = PHAsset.fetchAssetsWithLocalIdentifiers(
                NSArray(arrayOf(assetIdentifier)), options: nil
            )

            if (assets.count() == 0) {
                println("IosGlassesManager: Asset not found: $assetIdentifier")
                callback(null)
                return@withContext
            }

            val asset = assets.firstObject() as PHAsset

            PHImageManager.defaultManager().requestImageDataAndOrientationForAsset(
                asset, options: options
            ) { data, _, _, info ->
                if (data != nil) {
                    val nsData = data as NSData
                    val bytes = ByteArray(nsData.length.toInt())
                    nsData.getBytes(platform.Foundation.mutablePointerOf(bytes))
                    callback(bytes)
                } else {
                    val error = info?.valueForKey(platform.Foundation.NSString("PHImageErrorKey")) as? NSError
                    println("IosGlassesManager: Failed to load image data: ${error?.localizedDescription}")
                    callback(null)
                }
            }
        }
    }

    actual fun toast(msg: String, long: Boolean = false) {
        println("IosGlassesManager: $msg")
        // TODO: Use UserNotifications or in-app toast
    }
}

actual object Log {
    actual fun d(tag: String, msg: String) = println("[$tag] $msg")
    actual fun e(tag: String, msg: String) = System.err.println("[$tag] $msg")
}