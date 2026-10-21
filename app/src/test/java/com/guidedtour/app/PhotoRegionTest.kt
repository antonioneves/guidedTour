package com.guidedtour.app

import com.guidedtour.app.data.ViewArea
import com.guidedtour.app.data.photoRegion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhotoRegionTest {
    @Test fun previewShowingThePhotoWholeMapsStraight() {
        // A 3000 × 4000 photo in a 1080 × 1440 preview: same proportions, nothing cropped.
        val region = photoRegion(ViewArea(270f, 360f, 810f, 1080f, 1080, 1440), 3000, 4000, margin = 0f)!!
        assertEquals(0.25f, region.left, 1e-4f)
        assertEquals(0.25f, region.top, 1e-4f)
        assertEquals(0.75f, region.right, 1e-4f)
        assertEquals(0.75f, region.bottom, 1e-4f)
    }

    @Test fun previewCroppingThePhotoSidesMapsInward() {
        // A tall 1080 × 2160 preview fills with a 3000 × 4000 photo, cropping its sides: the
        // preview's middle is the photo's middle, and the preview's edges are inside the photo's.
        val region = photoRegion(ViewArea(0f, 0f, 1080f, 2160f, 1080, 2160), 3000, 4000, margin = 0f)!!
        assertEquals(0.5f - 0.5f * (1080f / 2160f) * (4000f / 3000f), region.left, 1e-3f)
        assertEquals(0f, region.top, 1e-3f)
        assertEquals(1f, region.bottom, 1e-3f)
    }

    @Test fun marginGrowsTheRegionAndStaysInThePhoto() {
        val region = photoRegion(ViewArea(270f, 360f, 810f, 1080f, 1080, 1440), 3000, 4000, margin = 0.1f)!!
        assertEquals(0.2f, region.left, 1e-4f)
        assertEquals(0.8f, region.right, 1e-4f)
        val edge = photoRegion(ViewArea(0f, 0f, 540f, 720f, 1080, 1440), 3000, 4000, margin = 0.5f)!!
        assertEquals(0f, edge.left, 0f)
        assertEquals(0f, edge.top, 0f)
    }

    @Test fun photoTheOtherWayRoundIsNotCropped() {
        assertNull(photoRegion(ViewArea(270f, 360f, 810f, 1080f, 1080, 1440), 4000, 3000))
    }
}
