package com.guidedtour.app.ui

import android.view.Surface
import androidx.camera.view.RotationProvider
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Constraints
import androidx.core.content.ContextCompat

/**
 * How the phone is held, as quarter turns clockwise that content needs to look upright: 0 upright,
 * 1 when the phone is turned left (counter-clockwise), -1 when turned right (clockwise). Upside down counts as
 * upright. Uses CameraX's own sensor helper, so it matches how photos are rotated. Only listens
 * while [active].
 */
@Composable
fun rememberUprightTurns(active: Boolean): State<Int> {
    val context = LocalContext.current
    val turns = remember { mutableIntStateOf(0) }
    DisposableEffect(active) {
        if (!active) {
            turns.intValue = 0
            return@DisposableEffect onDispose {}
        }
        val provider = RotationProvider(context)
        val listener = RotationProvider.Listener { rotation ->
            turns.intValue = when (rotation) {
                Surface.ROTATION_90 -> 1
                Surface.ROTATION_270 -> -1
                else -> 0
            }
        }
        provider.addListener(ContextCompat.getMainExecutor(context), listener)
        onDispose { provider.removeListener(listener) }
    }
    return turns
}

/** [turns] as an animated angle in degrees, for icons that spin to stay upright. */
@Composable
fun animatedUprightAngle(turns: Int): Float {
    val angle by animateFloatAsState(turns * 90f, label = "upright angle")
    return angle
}

/** Rotates by whole quarter [turns], swapping width and height so the rotated content lays out correctly. */
fun Modifier.rotateLayout(turns: Int): Modifier =
    if (turns % 2 == 0) this
    else layout { measurable, constraints ->
        val placeable = measurable.measure(
            Constraints(constraints.minHeight, constraints.maxHeight, constraints.minWidth, constraints.maxWidth),
        )
        layout(placeable.height, placeable.width) {
            placeable.placeWithLayer((placeable.height - placeable.width) / 2, (placeable.width - placeable.height) / 2) {
                rotationZ = turns * 90f
            }
        }
    }
