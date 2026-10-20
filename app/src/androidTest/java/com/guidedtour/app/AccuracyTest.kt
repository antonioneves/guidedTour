package com.guidedtour.app

import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import com.guidedtour.app.data.Accuracy
import com.guidedtour.app.data.OcrScript
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

/**
 * Runs the accuracy report on the photos in /data/local/tmp/testset (see tools/make_ocr_testset.py)
 * and logs it under the tag "OCR_REPORT". The test process can't read the app's own folder, so push the
 * photos to /data/local/tmp/testset first: adb push OUTPUT_DIR/. /data/local/tmp/testset/
 */
class AccuracyTest {
    @Test fun report() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val report = runBlocking { Accuracy.run(context, OcrScript.Auto, File("/data/local/tmp/testset")) }
        report.lines().forEach { Log.i("OCR_REPORT", it) }
        assertFalse(report.startsWith("No test photos"))
    }
}
