# Guided Tour

An Android app that turns the plaques and labels in museums and historic places into an audio
guide. Point the camera at the text, and the app reads it, saves it with the place where it was
scanned, and reads it aloud, also with the screen off.

## Features

- **Scan**: live text recognition with tips for a better photo (too dark, too small, cut off,
  shaky, glare). Text photographed at an angle, from the side or below, is stretched back square,
  and sideways or tilted text is turned upright. Capture with the shutter button or the volume key, or let it capture
  automatically once the text looks readable. Optionally takes three photos and keeps the sharpest.
- **Correct misread words**: words the camera wasn't sure of, and that aren't real words in their
  paragraph's language, are replaced by the likeliest common word one OCR slip away ("tcmple" →
  "temple", "construido" → "construído"). Uses word lists on the phone for about 30 languages.
- **Read aloud**: sentence by sentence, with the current word highlighted. Plays in the
  background with lock-screen and headphone controls, and pauses other audio while reading.
  Speed, pitch and voice (per language) are adjustable.
- **Live playlist**: keep scanning while earlier texts play. New scans can play next or join the
  end of the queue, and the queue can be reordered, skipped or edited.
- **Guides**: every scan is saved, grouped into visits by day and place, with a photo thumbnail,
  search, favourites, "Play visit", text editing and swipe to delete.
- **Places**: an OpenStreetMap map of where each text was scanned, with the walking route of
  each day. No map key or account needed.
- **Photos of places**: each visit, and a tapped pin, shows a photo of the place: the lead image
  of the most notable Wikipedia article nearby, from Wikimedia Commons, credited to its author.
  Can be turned off in Settings.
- **Bilingual plaques**: the language is detected paragraph by paragraph, so a plaque in, say,
  Portuguese and English reads each part with its own voice and translates each from its own
  language. A guide's language can also be set by hand.
- **Translate** on the device into the phone's language, or one chosen in Settings (models
  download once).
- **Scripts**: Latin, Chinese, Japanese, Korean and Devanagari, detected automatically.
- **Improve with cloud** (optional): sends one photo to Google Cloud Vision for a second reading.

Everything except new map areas, new place photos, translation model downloads and the optional cloud reading works
offline. See [PRIVACY.md](PRIVACY.md) for what leaves the device.

## Requirements

- Android Studio (or the command line) with JDK 17+ and the Android SDK for API 37.
- A device or emulator running Android 8.0 (API 26) or newer. The camera features need a real
  device; the emulator camera shows no text.

## Setup

An optional key goes in `local.properties` (git-ignored):

```properties
VISION_API_KEY=...   # Google Cloud Vision; enables "Improve with cloud"
```

## Building

```sh
./gradlew :app:installDebug          # debug build on the connected device
./gradlew :app:assembleRelease       # signed, shrunk APK in app/build/outputs/apk/release/
./gradlew :app:bundleRelease         # app bundle for Google Play
```

With more than one device connected, Gradle installs to all of them; set `ANDROID_SERIAL` to pick
one.

### Release signing

Release builds are signed when `keystore.properties` exists in the project root (git-ignored):

```properties
storeFile=release.jks
storePassword=...
keyAlias=guidedtour
keyPassword=...
```

Keep a backup of `release.jks` and its passwords: Google Play updates must be signed with the same
key. Without the file, release builds are left unsigned.

### GitHub Actions

`.github/workflows/release.yml` builds the signed APK and bundle on GitHub. Start it by hand
(Actions → Release APK → Run workflow) or push a version tag (`git tag v1.0 && git push --tags`),
which also publishes a GitHub release with the APK. It needs these repository secrets:
`RELEASE_KEYSTORE_BASE64` (`base64 -i release.jks`), `RELEASE_STORE_PASSWORD`,
`RELEASE_KEY_ALIAS` and `RELEASE_KEY_PASSWORD`. Raise `versionCode` in
`app/build.gradle.kts` before each release uploaded to Google Play.

### Baseline profile

`baselineprofile/` records startup and tab navigation so that code is compiled ahead of time on
install. Regenerate it after larger UI changes, with only an emulator (API 33+) connected, since
it replaces the installed app:

```sh
ANDROID_SERIAL=emulator-5554 ./gradlew :app:generateReleaseBaselineProfile
```

The result is written to `app/src/release/generated/baselineProfiles/`; commit it.

## Testing

```sh
./gradlew :app:testDebugUnitTest
```

Unit tests cover text layout and clean-up, misread-word correction, perspective, sentence splitting, photo tips, analysis throttling
and visit grouping.

**OCR accuracy**: `tools/make_ocr_testset.py` generates synthetic plaque photos with their true
text (needs Pillow and numpy). Push them to the device and run the instrumented test, which
reports the character error rate with and without the image enhancements:

```sh
python3 tools/make_ocr_testset.py /tmp/testset
adb push /tmp/testset /data/local/tmp/testset
./gradlew :app:installDebug :app:installDebugAndroidTest
adb shell am instrument -w -e class com.guidedtour.app.AccuracyTest \
  com.guidedtour.app.test/androidx.test.runner.AndroidJUnitRunner
```

`.github/workflows/tests.yml` runs the unit tests and this instrumented test (on an emulator) on
every push and pull request.

The report also shows the error after misread words are corrected, and each word it changed. Another
folder of photos can be given with `-e dir /data/local/tmp/other`.

To regenerate the word lists (after a wordfreq update), run `pip install wordfreq` and
`python3 tools/make_wordlists.py app/src/main/assets/words`.

The same test is available in the app under Settings → Developer. Real plaque photos with typed
transcripts give far more useful numbers than the synthetic set.

## Project layout

| Path | Contents |
| --- | --- |
| `app/src/main/java/com/guidedtour/app/` | `MainActivity`, `TourViewModel`, `ReaderService` (background reading and media session) |
| `…/data/` | OCR (`Recognizer`, `OcrLayout`), speech (`TextReader`, `TextChunker`), database (`Scan`), settings, translation, location, visits, cloud OCR, accuracy test |
| `…/ui/` | Compose screens: camera, map, guides list, player and settings sheets, photo tips |
| `app/schemas/` | Exported Room database schemas (for migrations) |
| `baselineprofile/` | Baseline profile generator |
| `design/` | App icon and store graphics generator (see [design/README.md](design/README.md)) |
| `tools/` | OCR test set and word list generators |
| `app/src/main/assets/words/` | Word lists for correcting misread words (from `tools/make_wordlists.py`) |
| `IMPROVEMENTS.md` | Ideas and their status |
| `PRIVACY.md` | Privacy policy |

## Tech stack

Kotlin, Jetpack Compose with Material 3 Expressive, CameraX, ML Kit (text recognition, language
identification, translation), Android TextToSpeech, Room, MapLibre with OpenStreetMap tiles from
OpenFreeMap, and AndroidX Media.

## License

Copyright (C) 2026 Antonio Neves

Guided Tour is free software: you can redistribute it and/or modify it under the terms of the
GNU General Public License as published by the Free Software Foundation, either version 3 of the
License, or (at your option) any later version. See [LICENSE](LICENSE) for the full text.

### Additional permission under GNU GPL version 3 section 7

If you modify this program, or any covered work, by linking or combining it with Google Play
services (`com.google.android.gms`) or Google ML Kit (`com.google.mlkit`), or a modified version of
those libraries, containing parts covered by the terms of their license agreements, the licensors
of this program grant you additional permission to convey the resulting work.

The word lists in `app/src/main/assets/words/` are derived from
[wordfreq](https://github.com/rspeer/wordfreq) by Robyn Speer and are licensed under
[CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/).

The libraries and services it uses keep their own licenses; they are listed in the app under
Settings → Credits.
