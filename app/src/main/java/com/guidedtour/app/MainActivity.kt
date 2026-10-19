package com.guidedtour.app

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.guidedtour.app.ui.TourApp
import com.guidedtour.app.ui.TourTheme

class MainActivity : ComponentActivity() {
    private val viewModel: TourViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TourTheme { TourApp(viewModel) }
        }
    }

    // Optional: the volume buttons act as a shutter on the Scan tab (see Settings).
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (isShutterKey(keyCode)) {
            if (event.repeatCount == 0) viewModel.fireShutter()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean =
        isShutterKey(keyCode) || super.onKeyUp(keyCode, event)

    private fun isShutterKey(keyCode: Int) =
        (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP) &&
            viewModel.volumeShutterActive
}
