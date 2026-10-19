# App icon

A Google Maps style pin with 3D shading, holding a sound wave of red-and-gold Doric columns,
on a burgundy gradient. `preview.png` shows it the way most launchers do.

## Regenerating

From the project root:

```sh
python3 design/make_icon.py   # Android resources + store SVGs
python3 design/render.py      # store PNGs (macOS: uses Quick Look to render SVG)
```

All geometry and colours live in `make_icon.py`; edit there and rerun both scripts.

## Assets

| File | Used for |
| --- | --- |
| `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` | Adaptive launcher icon (also the round icon) |
| `app/src/main/res/drawable/ic_launcher_background.xml` | Adaptive icon background layer (burgundy gradient) |
| `app/src/main/res/drawable/ic_launcher_foreground.xml` | Adaptive icon foreground layer; also the splash screen icon |
| `app/src/main/res/drawable/ic_launcher_monochrome.xml` | Themed (Material You) icon on Android 13+ |
| `app/src/main/res/drawable/ic_notification.xml` | Status bar icon for the "Reading aloud" notification |
| `design/store/play-store-icon-512.png` | Play Store listing icon (512×512, 32-bit PNG) |
| `design/store/feature-graphic-1024x500.png` | Play Store feature graphic (1024×500, 24-bit PNG) |
| `design/store/*.svg` | Vector sources of the two store images |

No legacy PNG mipmaps are needed: `minSdk` is 26, so every device uses the adaptive icon.
