package com.metahelper.app

import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.*

/**
 * Watches the device gallery for new Meta Ray-Ban glasses photos and reports
 * each genuinely-new one exactly once.
 *
 * Hardened over the original newest-row-only / DATA-substring version:
 *  - Classifies by RELATIVE_PATH (robust under scoped storage; falls back to the
 *    DATA path) anchored to the "Meta AI" folder, killing false positives.
 *  - Tracks a high-water-mark _ID so only images added after watching started
 *    fire, and the burst of onChange callbacks for a single insert can't
 *    double-fire the same photo.
 *  - Scans the newest few rows (not just row 0) so an unrelated newer photo
 *    (screenshot, chat image) can't mask the glasses photo.
 *  - Debounces the onChange burst and runs all MediaStore work off the main thread.
 */
class GalleryWatcher(
    private val context: Context,
    private val onNewImageDetected: (Uri) -> Unit
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var debounceJob: Job? = null

    // Largest image _ID we've already accounted for. Starts at MAX so nothing
    // fires until the baseline (current newest id) is computed — that avoids
    // reprocessing photos that existed before we started watching.
    @Volatile private var lastSeenId: Long = Long.MAX_VALUE

    private val contentUri: Uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI

    private val contentObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            super.onChange(selfChange, uri)
            // MediaStore fires several onChange callbacks for one insert; debounce
            // so we scan once, shortly after the burst settles.
            debounceJob?.cancel()
            debounceJob = scope.launch {
                delay(DEBOUNCE_MS)
                checkForNewMetaImage()
            }
        }
    }

    fun startWatching() {
        Log.d(TAG, "Starting to watch gallery...")
        context.contentResolver.registerContentObserver(contentUri, true, contentObserver)
        // Baseline the high-water mark to the current newest image, so we only
        // react to photos added from now on.
        scope.launch {
            lastSeenId = queryNewestImageId()
            Log.d(TAG, "Baseline newest image id = $lastSeenId")
        }
    }

    fun stopWatching() {
        Log.d(TAG, "Stopping gallery watch.")
        context.contentResolver.unregisterContentObserver(contentObserver)
        debounceJob?.cancel()
    }

    /** Newest image _ID currently in the gallery, or -1 if there are none. */
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
                    if (id <= lastSeenId) break  // sorted DESC: the rest are already seen
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

                // Advance past everything we scanned so non-Meta new images
                // (screenshots, chat photos) aren't rescanned on the next change.
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
    }

    companion object {
        private const val TAG = "GalleryWatcher"
        private const val DEBOUNCE_MS = 1500L
        private const val MAX_SCAN = 15
    }
}

/**
 * True if a gallery path looks like a Meta Ray-Ban glasses capture. Glasses
 * photos land in "Download/Meta AI/" (filenames like 20260616_212855_<hex>.jpg).
 * Anchored to that folder (and the "Ray-Ban" naming variant) rather than a broad
 * substring, so unrelated paths — a "Metallica" folder, any panorama — don't
 * false-match. Pure string logic so it's unit-testable without MediaStore.
 */
fun isMetaImagePath(path: String): Boolean =
    path.contains("Meta AI", ignoreCase = true) ||
    path.contains("Ray-Ban", ignoreCase = true)
