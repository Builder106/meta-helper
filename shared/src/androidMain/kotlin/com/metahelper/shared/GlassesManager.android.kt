package com.metahelper.shared

import android.content.Context
import android.net.Uri
import android.util.Log as AndroidLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual class GlassesManager(
    backendUrl: String = "https://metahelper.onrender.com",
    context: Context
) : GlassesManager(backendUrl, context) {

    override fun loadImageBytes(imageUri: Any, callback: (ByteArray?) -> Unit) {
        val uri = imageUri as Uri
        withContext(Dispatchers.IO) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                inputStream?.let {
                    val bytes = it.readBytes()
                    it.close()
                    callback(bytes)
                } ?: callback(null)
            } catch (e: Exception) {
                Log.e("GlassesManager", "Gallery process error: ${e.message}")
                callback(null)
            }
        }
    }

    actual fun toast(msg: String, long: Boolean = false) {
        withContext(Dispatchers.Main) {
            android.widget.Toast.makeText(context, msg, if (long) android.widget.Toast.LENGTH_LONG else android.widget.Toast.LENGTH_SHORT).show()
        }
    }
}

actual object Log {
    actual fun d(tag: String, msg: String) = AndroidLog.d(tag, msg)
    actual fun e(tag: String, msg: String) = AndroidLog.e(tag, msg)
}