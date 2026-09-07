package com.metahelper.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GalleryWatcherInstrumentationTest {
    @Test
    fun targetContextAndGalleryClassifierWorkOnAndroid() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.metahelper.app", context.packageName)
        assertTrue(context.contentResolver != null)
        assertTrue(isMetaImagePath("Download/Meta AI/capture.jpg"))
    }
}
