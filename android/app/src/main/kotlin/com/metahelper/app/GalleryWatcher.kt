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
            Log.d("GalleryWatcher", "Gallery change detected: $uri")
            
            // Wait 1.5 seconds to ensure the file is fully written by Meta AI
            Handler(Looper.getMainLooper()).postDelayed({
                fetchLatestImage()
            }, 1500)
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
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.DATA
        )
        
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"
        
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
                
                // Be more generous with the path filter to ensure we don't miss it
                if (path.contains("Meta", ignoreCase = true)) {
                    Log.d("GalleryWatcher", "New Meta image detected: $path")
                    Toast.makeText(context, "Photo Detected: $path", Toast.LENGTH_SHORT).show()
                    onNewImageDetected(uri)
                }
            }
        }
    }
}

