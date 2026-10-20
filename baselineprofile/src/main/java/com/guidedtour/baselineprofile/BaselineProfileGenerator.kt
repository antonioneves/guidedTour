package com.guidedtour.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Records the code used at startup and on each tab, so it is compiled ahead of time on install. */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(packageName = "com.guidedtour.app", includeInStartupProfile = true) {
        for (permission in listOf("CAMERA", "ACCESS_FINE_LOCATION", "ACCESS_COARSE_LOCATION", "POST_NOTIFICATIONS")) {
            device.executeShellCommand("pm grant $packageName android.permission.$permission")
        }
        pressHome()
        startActivityAndWait()
        device.wait(Until.hasObject(By.text("Guides")), 10_000)

        device.findObject(By.text("Guides"))?.click()
        device.wait(Until.hasObject(By.desc("Settings")), 5_000)
        device.findObject(By.scrollable(true))?.let { list ->
            list.setGestureMargin(device.displayWidth / 5)
            list.fling(Direction.DOWN)
            list.fling(Direction.UP)
        }
        device.findObject(By.text("Places"))?.click()
        device.waitForIdle()
        device.findObject(By.text("Scan"))?.click()
        device.waitForIdle()
    }
}
