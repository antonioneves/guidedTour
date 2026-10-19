# Guided Tour: improvements and feature ideas

Suggested next steps for the app, split into fixes to existing behaviour and new features.

**Top picks:** 1 and 2 together (reliable reading with proper controls), then the live playlist
queue (which builds on them), then on-device translation, then OCR text editing plus plaque
thumbnails.

## Status (2026-10-19)

All items below are implemented except a migration test. The voice picker (per-language voice with
a spoken sample) is in the settings sheet. Checked on the emulator: visit grouping, play visit and the queue, queue sheet, settings sheet,
translation (stored and shown on the card), no-key map message, camera kept alive across tabs.
Not exercised: thumbnails and photo OCR (the emulator camera has no text), editing text, volume
shutter, route line (needs a Maps key), non-Latin OCR, TalkBack.

## Fixes and improvements

1. **Long plaques may not be read.** Android's speech engine limits how much text one request can
   contain (`TextToSpeech.getMaxSpeechInputLength()`, often about 4,000 characters), and longer text
   can fail without any error. Reading sentence by sentence fixes that and also makes pause, resume
   and skip possible.
2. **No lock-screen or headphone controls.** The background reading works, but it can only be
   stopped from the notification. A proper media session (Media3 `MediaSessionService`) would add
   play/pause on the lock screen and on Bluetooth headphone buttons, which is the natural way to
   control audio while walking.
3. **No audio focus handling.** The app doesn't request audio focus or lower other apps' volume,
   so music or another app can talk over the reading.
4. **OCR mistakes can't be fixed.** Plaques often use stylised fonts, so a quick "edit text" before
   or after saving would help.
5. **The map is blank without a Maps API key.** Add the key to `local.properties` (`MAPS_API_KEY`).
   The map should also show a message instead of an empty grey map when the key or the internet
   connection is missing.
6. **Switching tabs restarts the camera and map**, so each switch is slower than it needs to be.
   Keeping them alive would make it feel snappier.
7. **The database has no upgrade path.** The first time its structure changes, existing users'
   saved scans would be lost or the app would crash. Setting up schema export and migrations now
   is cheap.
8. **No tests yet.** Small unit tests for the photo-tip logic (`ScanQuality.kt`) and the text
   clean-up (`Recognizer.clean`) would protect the parts most likely to change.
9. **Store readiness.** The release build has no signing or code shrinking set up yet. Google Play
   also requires a privacy policy, because the app uses the camera and location.

## Feature ideas

- **Translate the plaque on the device.** ML Kit can translate offline into the user's language
  before reading aloud. For tourists abroad, probably the most valuable addition.
- **Highlight words as they're read**, karaoke-style. It helps people follow along and costs little,
  since the speech engine already reports which word it's on (`UtteranceProgressListener.onRangeStart`).
- **Group scans into visits.** Grouping by day and place gives a "Tuesday at the Acropolis Museum"
  list, with the walking route drawn on the map.
- **Save a thumbnail of each plaque** so the Texts list is easier to scan visually.
- **Play the tour as a live playlist (queue).** Reading all of a visit's scans in a row, and the
  queue stays open while the visit continues: the user keeps taking pictures, and each new scan can
  be added with "Play next" or "Add to queue" without interrupting what is being read. The
  camera stays usable while earlier texts play, and the queue is visible and editable (reorder,
  remove, skip). It builds on items 1 and 2 (sentence-by-sentence reading and a media session),
  which give the queue its next/previous/pause controls on the lock screen and headphones.
  Optionally, a setting could add every new scan to the end of the queue automatically.
- **Search and favourites** in the Texts tab.
- **Speech speed and voice settings.**
- **Volume button as shutter**, for one-handed capture.
- **More scripts.** OCR currently reads only Latin-alphabet text. Adding Chinese, Japanese, Korean
  and Devanagari (Hindi and related languages) is mostly a matter of adding ML Kit models.
- **Accessibility pass.** Check the app with TalkBack and large text sizes. It is naturally useful
  to visually impaired visitors, so this is worth doing well.
