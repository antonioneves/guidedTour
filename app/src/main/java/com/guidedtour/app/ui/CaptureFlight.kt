package com.guidedtour.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

private const val FLIGHT_MS = 650

/**
 * The photo card of a successful scan flying into the Guides tab. It is drawn above the whole app
 * (tab bar included), so it can land on the tab icon.
 */
class CaptureFlight {
    var photo by mutableStateOf<ImageBitmap?>(null)
        private set
    /** Where the card starts, in window coordinates. */
    var from by mutableStateOf(Rect.Zero)
        private set
    /** Centre of the Guides tab icon, in window coordinates. */
    var target by mutableStateOf(Offset.Zero)
    val progress = Animatable(0f)

    suspend fun fly(photo: ImageBitmap, from: Rect) {
        this.photo = photo
        this.from = from
        progress.snapTo(0f)
        try {
            progress.animateTo(1f, tween(FLIGHT_MS, easing = LinearEasing))
        } finally {
            this.photo = null
        }
    }
}

@Composable
fun CaptureFlightOverlay(flight: CaptureFlight) {
    val photo = flight.photo ?: return
    val from = flight.from
    val density = LocalDensity.current
    val endSize = with(density) { 20.dp.toPx() }
    val lift = with(density) { 90.dp.toPx() }
    val t = flight.progress.value
    // A toss up and over, then a fall into the tab: a quadratic curve through a raised control point.
    val start = from.center
    val end = flight.target
    val control = Offset(start.x + (end.x - start.x) * 0.6f, start.y - lift)
    val eased = t * t * (3 - 2 * t)
    val u = 1 - eased
    val point = start * (u * u) + control * (2 * u * eased) + end * (eased * eased)
    val scale = 1f + (endSize / from.height - 1f) * eased
    with(density) {
        Image(
            photo,
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier
                .offset { IntOffset(from.left.roundToInt(), from.top.roundToInt()) }
                .size(from.width.toDp(), from.height.toDp())
                .graphicsLayer {
                    translationX = point.x - start.x
                    translationY = point.y - start.y
                    scaleX = scale
                    scaleY = scale
                    alpha = if (t > 0.9f) (1 - t) / 0.1f else 1f
                    shape = RoundedCornerShape(28.dp)
                    clip = true
                    shadowElevation = 16.dp.toPx() * (1 - t)
                },
        )
    }
}
