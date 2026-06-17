package com.metahelper.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests the Meta-image path predicate — the line that decides whether a new
 * gallery photo triggers the whole capture→backend→audio pipeline. The match is
 * anchored to the "Meta AI" folder (where Meta Ray-Ban glasses photos land),
 * not a broad substring.
 */
class GalleryWatcherTest {

    @Test
    fun matchesMetaCapturePaths() {
        assertTrue(isMetaImagePath("Download/Meta AI/20260616_212855_31356889.jpg"))
        assertTrue(isMetaImagePath("/storage/emulated/0/Download/Meta AI/IMG_001.jpg"))
        assertTrue(isMetaImagePath("DCIM/Ray-Ban_0420.jpg"))   // Ray-Ban naming variant
        assertTrue(isMetaImagePath("download/meta ai/x.jpg"))  // case-insensitive
    }

    @Test
    fun ignoresOrdinaryPaths() {
        assertFalse(isMetaImagePath("DCIM/Camera/IMG_2024.jpg"))
        assertFalse(isMetaImagePath("Pictures/Screenshots/Screenshot_1.png"))
        assertFalse(isMetaImagePath("Download/receipt.jpg"))
    }

    @Test
    fun rejectsFormerFalsePositives() {
        // Anchoring to the "Meta AI" folder fixed the old broad-substring false
        // positives: these used to match on "Pano" / "meta" / "Meta" and now don't.
        assertFalse(isMetaImagePath("DCIM/Panorama/pano_1.jpg"))    // "Pano"
        assertFalse(isMetaImagePath("Pictures/metalwork/weld.jpg")) // "meta"
        assertFalse(isMetaImagePath("Download/MetaMask/seed.png"))  // "Meta" but not "Meta AI"
    }
}
