package com.metahelper.app

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log

import android.widget.Toast

class GalleryWatcher(
    private val context: Context,
    private val onNewImageDetected: (Uri) -> Unit
) {
    private val contentObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            super.onChange(selfChange, uri)
            Log.d("GalleryWatcher", "Gallery change detected (uri: $uri, selfChange: $selfChange)")
            
            // Wait 2 seconds to ensure Meta AI and the system gallery have finished indexing
            Handler(Looper.getMainLooper()).postDelayed({
                fetchLatestImage()
            }, 2000)
        }
    }

    fun startWatching() {
        Log.d("GalleryWatcher", "Starting to watch gallery...")
        context.contentResolver.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            true,
            contentObserver
        )
    }

    fun stopWatching() {
        Log.d("GalleryWatcher", "Stopping gallery watch.")
        context.contentResolver.unregisterContentObserver(contentObserver)
    }

    private fun fetchLatestImage() {
        Log.d("GalleryWatcher", "--- fetchLatestImage Start ---")
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.DISPLAY_NAME
        )
        
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"
        
        try {
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                sortOrder
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
                    val path = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA))
                    val uri = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString())
                    
                    Log.d("GalleryWatcher", "Checking most recent image: $path")
                    
                    // Most Meta images are saved in a folder named "Meta" or start with "Meta_"
                    val isMetaImage = path.contains("Meta", ignoreCase = true) || 
                                     path.contains("Ray-Ban", ignoreCase = true) ||
                                     path.contains("Pano", ignoreCase = true)
                    
                    if (isMetaImage) {
                        Log.d("GalleryWatcher", "MATCH! Meta image detected: $path")
                        Toast.makeText(context, "AI Processing: ${path.substringAfterLast("/")}", Toast.LENGTH_SHORT).show()
                        onNewImageDetected(uri)
                    } else {
                        Log.d("GalleryWatcher", "IGNORE: Most recent image ($path) does not look like a Meta photo.")
                    }
                } else {
                    Log.d("GalleryWatcher", "MediaStore query returned no results.")
                }
            }
        } catch (e: Exception) {
            Log.e("GalleryWatcher", "MediaStore query failed: ${e.message}")
            e.printStackTrace()
        }
    }
}

