package com.metahelper.shared

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private var applicationContext: Context? = null

private class AndroidGlassesManager : GlassesManager {
    override var onStatusUpdate: ((String) -> Unit)? = null
    override var onConnectionStateChange: ((ConnectionState) -> Unit)? = null

    override fun replayLastAudio() = Unit

    override fun stopAll() = Unit
}

@Suppress("UNUSED_PARAMETER")
actual fun createGlassesManager(backendUrl: String, context: Any): GlassesManager {
    applicationContext = (context as Context).applicationContext
    return AndroidGlassesManager()
}

actual fun loadImageBytes(imageUri: Any, callback: (ByteArray?) -> Unit) {
    val context = applicationContext
    val uri = when (imageUri) {
        is Uri -> imageUri
        is String -> Uri.parse(imageUri)
        else -> null
    }

    if (context == null || uri == null) {
        callback(null)
        return
    }

    CoroutineScope(Dispatchers.IO).launch {
        val bytes = try {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } catch (e: Exception) {
            Log.e("GlassesManager", "Gallery process error: ${e.message}")
            null
        }

        withContext(Dispatchers.Main) {
            callback(bytes)
        }
    }
}

actual fun logDebug(tag: String, msg: String) {
    Log.d(tag, msg)
}

actual fun logError(tag: String, msg: String) {
    Log.e(tag, msg)
}

actual fun toast(msg: String, long: Boolean) {
    val context = applicationContext ?: return
    Handler(Looper.getMainLooper()).post {
        Toast.makeText(
            context,
            msg,
            if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
        ).show()
    }
}
