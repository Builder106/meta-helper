package com.metahelper.shared

import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex

private const val TAG = "GalleryWatcher"
private const val DEBOUNCE_MS = 1500L
private const val MAX_SCAN = 15

internal class AndroidGalleryWatcher(
    private val context: Context,
    private val onNewImageDetected: (Uri) -> Unit
) : GalleryWatcher {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var debounceJob: Job? = null
    @Volatile private var lastSeenId: Long = Long.MAX_VALUE
    private val contentUri: Uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    private val mutex = Mutex()

    private val contentObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            super.onChange(selfChange, uri)
            debounceJob?.cancel()
            debounceJob = scope.launch {
                delay(DEBOUNCE_MS)
                checkForNewMetaImage()
            }
        }
    }

    override fun startWatching() {
        Log.d(TAG, "Starting to watch gallery...")
        context.contentResolver.registerContentObserver(contentUri, true, contentObserver)
        scope.launch {
            lastSeenId = queryNewestImageId()
            Log.d(TAG, "Baseline newest image id = $lastSeenId")
        }
    }

    override fun stopWatching() {
        Log.d(TAG, "Stopping gallery watch.")
        context.contentResolver.unregisterContentObserver(contentObserver)
        debounceJob?.cancel()
    }

    private fun queryNewestImageId(): Long {
        return try {
            context.contentResolver.query(
                contentUri,
                arrayOf(MediaStore.Images.Media._ID),
                null, null,
                "${MediaStore.Images.Media._ID} DESC"
            )?.use { c ->
                if (c.moveToFirst()) c.getLong(c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)) else -1L
            } ?: -1L
        } catch (e: Exception) {
            Log.e(TAG, "Baseline query failed: ${e.message}")
            -1L
        }
    }

    private fun checkForNewMetaImage() {
        mutex.lock()
        try {
            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.RELATIVE_PATH,
                MediaStore.Images.Media.DATA,
            )
            try {
                context.contentResolver.query(
                    contentUri, projection, null, null,
                    "${MediaStore.Images.Media._ID} DESC"
                )?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                    val nameCol = cursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
                    val relCol = cursor.getColumnIndex(MediaStore.Images.Media.RELATIVE_PATH)
                    val dataCol = cursor.getColumnIndex(MediaStore.Images.Media.DATA)

                    var newest = lastSeenId
                    var matchUri: Uri? = null
                    var matchInfo = ""
                    var scanned = 0

                    while (cursor.moveToNext() && scanned < MAX_SCAN) {
                        val id = cursor.getLong(idCol)
                        if (id <= lastSeenId) break
                        scanned++
                        if (id > newest) newest = id
                        val rel = if (relCol >= 0) cursor.getString(relCol).orEmpty() else ""
                        val data = if (dataCol >= 0) cursor.getString(dataCol).orEmpty() else ""
                        val name = if (nameCol >= 0) cursor.getString(nameCol).orEmpty() else ""
                        if (matchUri == null && (isMetaImagePath(rel) || isMetaImagePath(data))) {
                            matchUri = ContentUris.withAppendedId(contentUri, id)
                            matchInfo = "id=$id name=$name path=$rel"
                        }
                    }

                    if (newest > lastSeenId) lastSeenId = newest

                    if (matchUri != null) {
                        Log.d(TAG, "New Meta image detected: $matchInfo")
                        onNewImageDetected(matchUri!!)
                    } else {
                        Log.d(TAG, "Gallery change had no new Meta image (scanned $scanned new rows).")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "MediaStore query failed: ${e.message}")
            }
        } finally {
            mutex.unlock()
        }
    }
}

fun isMetaImagePath(path: String): Boolean =
    path.contains("Meta AI", ignoreCase = true) ||
    path.contains("Ray-Ban", ignoreCase = true)

actual fun createGalleryWatcher(
    context: Any,
    onNewImageDetected: (Any) -> Unit
): GalleryWatcher {
    return AndroidGalleryWatcher(context as Context) { uri ->
        onNewImageDetected(uri)
    }
}