package com.metahelper.app

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GalleryWatcherInstrumentationTest {
    @Test(timeout = 20000)
    fun detectsNewMetaAiImageFromMediaStoreExactlyOnce() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val resolver = context.contentResolver
        val baselineComplete = CountDownLatch(1)
        val imageDetected = CountDownLatch(1)
        val detectedUri = AtomicReference<Uri?>()
        val detectionCount = AtomicInteger()
        val watcher = GalleryWatcher(context) { uri ->
            detectedUri.set(uri)
            detectionCount.incrementAndGet()
            imageDetected.countDown()
        }

        val permission = imageReadPermission()
        val alreadyHadPermission = ContextCompat.checkSelfPermission(
            context,
            permission,
        ) == PackageManager.PERMISSION_GRANTED
        if (!alreadyHadPermission) {
            instrumentation.uiAutomation.grantRuntimePermission(context.packageName, permission)
        }

        var insertedUri: Uri? = null
        var watchingStarted = false
        try {
            watcher.startWatchingForTest { baselineComplete.countDown() }
            watchingStarted = true
            assertTrue(
                "Gallery watcher did not finish its MediaStore baseline",
                baselineComplete.await(5, TimeUnit.SECONDS),
            )

            val newUri = resolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                ContentValues().apply {
                    put(
                        MediaStore.Images.Media.DISPLAY_NAME,
                        "metahelper-instrumentation-${System.nanoTime()}.jpg",
                    )
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Download/Meta AI/")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.Images.Media.IS_PENDING, 1)
                    }
                },
            )
            assertNotNull("Could not insert an image into MediaStore", newUri)
            val imageUri = requireNotNull(newUri)
            insertedUri = imageUri

            val output = resolver.openOutputStream(imageUri)
            assertNotNull("Could not open the MediaStore image", output)
            requireNotNull(output).use {
                it.write(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()))
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                resolver.update(
                    imageUri,
                    ContentValues().apply {
                        put(MediaStore.Images.Media.IS_PENDING, 0)
                    },
                    null,
                    null,
                )
            }

            assertTrue(
                "Gallery watcher did not detect the new Meta AI image",
                imageDetected.await(15, TimeUnit.SECONDS),
            )
            assertEquals(insertedUri, detectedUri.get())

            // Allow the remaining callbacks from the insert/update burst to settle.
            Thread.sleep(2000)
            assertEquals(1, detectionCount.get())
        } finally {
            if (watchingStarted) {
                watcher.stopWatching()
            }
            insertedUri?.let { resolver.delete(it, null, null) }
            if (!alreadyHadPermission) {
                instrumentation.uiAutomation.revokeRuntimePermission(
                    context.packageName,
                    permission,
                )
            }
        }
    }

    private fun imageReadPermission(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
}
