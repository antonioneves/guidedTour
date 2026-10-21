# Guided Tour: privacy policy

_Last updated: 2026-10-21. Replace the contact line below before publishing to Google Play._

Guided Tour reads text from plaques and labels you photograph and reads it aloud.

## What the app handles

- **Camera images.** Used on your device to recognise text. A small thumbnail of each scanned plaque
  is saved in the app's private storage. Photos are not uploaded unless you use the optional cloud
  reading described below.
- **Scanned text, translations and notes.** Stored in a database on your device only.
- **Location.** When you scan, the app records your approximate or precise location and, using the
  Android geocoder, a place name. This is stored with the scan on your device. The place-name lookup
  may be handled by Google's location services.
- **Maps.** The Places tab loads OpenStreetMap map tiles from [OpenFreeMap](https://openfreemap.org).
  Its servers receive the usual map-loading requests (such as your IP address and the area shown),
  but not your scans or route, which are drawn on the device.
- **Photos of places.** Unless you turn off "Photos of places" in Settings, the app asks Wikipedia
  and Wikimedia Commons for a photo of each place where you scanned. It sends only the place's
  name and its position rounded to about 100 m, once per place, never your scans or your live
  position; the photo
  is then kept on your device. Wikimedia's servers receive the usual request details, such as your
  IP address.
- **Language packs.** Translation downloads language models from Google the first time you use a
  language. Only the model download happens online; your text is translated on the device.

- **Optional cloud reading.** Only in builds that include a Google Cloud Vision key, a scan has an
  "Improve with cloud" button. If you choose it and confirm, the saved photo of that one plaque is
  uploaded to Google Cloud Vision to be read again, and the text it returns replaces the text of
  that scan. Nothing is uploaded unless you do this.

## What the app does not do

No accounts, no analytics, no advertising and no tracking. Photos and text stay on your device,
except for a photo you explicitly send with "Improve with cloud" where that option exists.

## Your control

You can delete any scan in the Texts tab (swipe it away). Uninstalling the app removes all stored
data. You can revoke camera and location permissions in Android settings; the app keeps working
without location, but scans then have no place.

## Contact

TODO: add a contact email before publishing.
