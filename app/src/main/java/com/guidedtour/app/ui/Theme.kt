package com.guidedtour.app.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Fallback "museum" palette (burgundy and warm gold) for devices without dynamic color.
private val LightScheme = lightColorScheme(
    primary = Color(0xFF8B2F3F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFD9DC),
    onPrimaryContainer = Color(0xFF3B0713),
    secondary = Color(0xFF75575B),
    tertiary = Color(0xFF7A5900),
    tertiaryContainer = Color(0xFFFFDEA0),
    background = Color(0xFFFFF8F7),
    surface = Color(0xFFFFF8F7),
)
private val DarkScheme = darkColorScheme(
    primary = Color(0xFFFFB2BA),
    onPrimary = Color(0xFF561421),
    primaryContainer = Color(0xFF702936),
    onPrimaryContainer = Color(0xFFFFD9DC),
    secondary = Color(0xFFE3BDC1),
    tertiary = Color(0xFFF1BF48),
    tertiaryContainer = Color(0xFF5B4300),
    background = Color(0xFF1A1112),
    surface = Color(0xFF1A1112),
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TourTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkScheme
        else -> LightScheme
    }
    MaterialExpressiveTheme(
        colorScheme = colors,
        motionScheme = MotionScheme.expressive(),
        content = content,
    )
}
