package com.guidedtour.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.guidedtour.app.BuildConfig

/** Someone whose work the app uses, with the page that tells more (and its licence). */
data class Credit(val title: String, val detail: String, val link: String)

/** The map's sources: OpenStreetMap and OpenMapTiles ask for this credit wherever the map shows. */
val MAP_CREDITS = listOf(
    Credit("© OpenStreetMap contributors", "Map data, under the Open Database License", "https://www.openstreetmap.org/copyright"),
    Credit("© OpenMapTiles", "Map design", "https://openmaptiles.org/"),
    Credit("OpenFreeMap", "Free map tiles", "https://openfreemap.org/"),
    Credit("MapLibre", "Map engine, under the BSD licence", "https://maplibre.org/"),
)

private val CREDIT_SECTIONS = listOf(
    "Map" to MAP_CREDITS,
    "Reading and translating" to listOf(
        Credit("ML Kit text recognition", "Reads the text in photos, on the phone", "https://developers.google.com/ml-kit/vision/text-recognition/v2"),
        Credit("ML Kit language identification", "Tells which language a text is in", "https://developers.google.com/ml-kit/language/identification"),
        Credit("ML Kit translation", "Translates on the phone", "https://developers.google.com/ml-kit/language/translation"),
        Credit("Google Cloud Vision", "Optional: re-reads a hard photo online", "https://cloud.google.com/vision"),
    ),
    "Speech and places" to listOf(
        Credit("Android text-to-speech", "The voices that read aloud, from the phone's speech engine", "https://developer.android.com/reference/android/speech/tts/TextToSpeech"),
        Credit("Google Play services location", "Where each photo was taken", "https://developers.google.com/location-context/fused-location-provider"),
        Credit("Android Geocoder", "Place names for the places visited", "https://developer.android.com/reference/android/location/Geocoder"),
        Credit("Wikipedia and Wikimedia Commons", "Photos of places, each credited to its author under its own free licence", "https://commons.wikimedia.org/wiki/Commons:Reusing_content_outside_Wikimedia"),
        Credit("wordfreq", "Word lists that correct misread words, by Robyn Speer, under CC BY-SA 4.0", "https://github.com/rspeer/wordfreq"),
    ),
    "Built with" to listOf(
        Credit("CameraX", "The camera, under the Apache 2.0 licence", "https://developer.android.com/media/camera/camerax"),
        Credit("Jetpack Compose and Material 3", "The interface, under the Apache 2.0 licence", "https://developer.android.com/compose"),
        Credit("Material Symbols", "Icons, under the Apache 2.0 licence", "https://fonts.google.com/icons"),
        Credit("Room", "Stores the guides, under the Apache 2.0 licence", "https://developer.android.com/training/data-storage/room"),
        Credit("Kotlin and kotlinx.coroutines", "Programming language, under the Apache 2.0 licence", "https://kotlinlang.org/"),
    ),
)

/** A credit row; tapping it opens the credit's page in the browser. */
@Composable
fun CreditRow(credit: Credit) {
    val uri = LocalUriHandler.current
    ListItem(
        headlineContent = { Text(credit.title) },
        supportingContent = { Text(credit.detail) },
        trailingContent = { Icon(Icons.AutoMirrored.Outlined.OpenInNew, "Open website") },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable { uri.openUri(credit.link) }.padding(horizontal = 8.dp),
    )
}

/** Everything the app is built on, grouped by what it does. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreditsSheet(onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
            Text(
                "Credits",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp).semantics { heading() },
            )
            Text(
                "Guided Tour ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            CREDIT_SECTIONS.forEach { (section, credits) ->
                Text(
                    section,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 4.dp).semantics { heading() },
                )
                credits.forEach { CreditRow(it) }
            }
        }
    }
}
