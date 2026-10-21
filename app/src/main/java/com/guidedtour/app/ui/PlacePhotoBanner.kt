package com.guidedtour.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.guidedtour.app.TourViewModel
import com.guidedtour.app.data.Place
import com.guidedtour.app.data.PlacePhoto

/** The photo of [place], once loaded; null while loading, without one, or with photos off. */
@Composable
fun rememberPlacePhoto(viewModel: TourViewModel, place: Place?): PlacePhoto? {
    val enabled by viewModel.settings.placePhotos.collectAsStateWithLifecycle()
    // Named after its first scan, so Guides and the map ask the same and share one photo.
    val name = place?.scans?.firstNotNullOfOrNull { it.placeName }
    val photo by produceState<PlacePhoto?>(null, enabled, place?.latitude, place?.longitude, name) {
        value = if (enabled && place != null) viewModel.placePhotos.photo(place.latitude, place.longitude, name) else null
    }
    return photo
}

/**
 * [photo] filling a banner, darkened towards the bottom so white [content] on it stays readable,
 * with its author and licence in a corner; tapping them opens the photo's page on Wikimedia Commons.
 */
@Composable
fun PlacePhotoBanner(
    photo: PlacePhoto,
    modifier: Modifier = Modifier,
    height: Dp = 152.dp,
    shape: Shape = MaterialTheme.shapes.large,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val uriHandler = LocalUriHandler.current
    Box(modifier.fillMaxWidth().height(height).clip(shape)) {
        Image(photo.image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.25f), 0.35f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.75f)),
            ),
        )
        if (photo.credit.isNotBlank()) {
            Surface(
                onClick = { if (photo.page.isNotBlank()) uriHandler.openUri(photo.page) },
                shape = MaterialTheme.shapes.small,
                color = Color.Black.copy(alpha = 0.45f),
                contentColor = Color.White,
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).widthIn(max = 240.dp).semantics {
                    contentDescription = "Photo: ${photo.credit}. Opens Wikimedia Commons"
                    role = Role.Button
                },
            ) {
                Text(
                    "Photo: ${photo.credit}",
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
        content()
    }
}
