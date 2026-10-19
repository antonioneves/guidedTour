"""Generates every app icon asset from one design: a Google Maps style pin with 3D shading whose
head holds a sound wave of red-and-gold Doric columns, on a burgundy background.

Run from the project root:  python3 design/make_icon.py

Writes the Android resources directly and the store artwork as SVG; design/render.sh turns the
SVGs into the PNGs the Play Store needs.
"""
import math
import os

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
RES = os.path.join(ROOT, "app", "src", "main", "res")
STORE = os.path.join(ROOT, "design", "store")

# Adaptive icon canvas is 108x108 dp; launchers show the central 72 dp and keep 66 dp safe.
cx, cy, R = 54, 50.5, 17
tip = cy + 1.5 * R  # a short, stubby body under a round head
# The whole artwork is drawn 15% larger around this point so the pin fills the icon as much as
# other launcher glyphs (e.g. Google Maps) while staying inside the 66dp safe zone.
ART_SCALE, ART_PIVOT = 1.15, (54, 54.5)

background_stops = [(0, "#A63A4C"), (1, "#5A1826")]
column_stops = [(0, "#5E1826"), (0.22, "#A3404F"), (0.5, "#7E2636"), (1, "#4A0F1B")]
detail_stops = [(0, "#C8932A"), (0.25, "#FFE08A"), (0.55, "#F1BF48"), (1, "#B98321")]
body_stops = [(0, "#FFFCF4"), (0.45, "#FBEBCF"), (1, "#D7B98A")]
flank_stops = [(0, "#00593A1C"), (1, "#40593A1C")]  # transparent to translucent brown
highlight_stops = [(0, "#00FFFFFF"), (0.5, "#CCFFFFFF"), (1, "#00FFFFFF")]
shadow_stops = [(0, "#73000000"), (1, "#00000000")]
rim_color = "#C29A62"
light = (cx - 6, cy - 7)  # light comes from the upper left of the head


# ---------------------------------------------------------------- geometry

def poly(points):
    return "M" + " L".join(f"{x:.2f},{y:.2f}" for x, y in points) + " Z"


def sample(f, n=8):
    return [f(i / n) for i in range(n + 1)]


def circle(x, y, r):
    return f"M{x - r:.2f},{y:.2f} A{r},{r} 0 1,0 {x + r:.2f},{y:.2f} A{r},{r} 0 1,0 {x - r:.2f},{y:.2f} Z"


def ribbon(points, width):
    """A polyline drawn as a filled band."""
    left, right = [], []
    for i, (x, y) in enumerate(points):
        x0, y0 = points[max(i - 1, 0)]
        x1, y1 = points[min(i + 1, len(points) - 1)]
        n = math.hypot(x1 - x0, y1 - y0) or 1
        ox, oy = -(y1 - y0) / n * width / 2, (x1 - x0) / n * width / 2
        left.append((x + ox, y + oy))
        right.append((x - ox, y - oy))
    return poly(left + right[::-1])


# Map-marker pin: a round head whose sides leave the circle low and curve gently inward to a
# short body ending in a softly rounded tip.
_a = math.radians(40)  # where the sides leave the head, below horizontal
_hx, _hy = R * math.cos(_a), cy + R * math.sin(_a)
_k = 4.5  # how far the sides keep following the head's tangent before bending to the tip
_tx, _ty = math.sin(_a) * _k, math.cos(_a) * _k
_tip_w, _tip_r = 0.7, 0.9  # rounded tip: half-width where the sides end, and its radius
_tip_y = tip - 0.5
pin = (f"M{cx + _tip_w:.2f},{_tip_y:.2f} "
       f"C{cx + 1.8:.2f},{tip - 3.2:.2f} {cx + _hx - _tx:.2f},{_hy + _ty:.2f} {cx + _hx:.2f},{_hy:.2f} "
       f"A{R},{R} 0 1,0 {cx - _hx:.2f},{_hy:.2f} "
       f"C{cx - _hx + _tx:.2f},{_hy + _ty:.2f} {cx - 1.8:.2f},{tip - 3.2:.2f} {cx - _tip_w:.2f},{_tip_y:.2f} "
       f"A{_tip_r},{_tip_r} 0 0,0 {cx + _tip_w:.2f},{_tip_y:.2f} Z")
highlight = ribbon([(cx + (R - 3.2) * math.cos(math.radians(a)), cy + (R - 3.2) * math.sin(math.radians(a)))
                    for a in range(200, 276, 5)], 1.6)


def column(x, h):
    """Returns (silhouette, details) path data for one Doric column centred on x."""
    t, b = cy - h / 2, cy + h / 2
    abacus, echinus_top, shaft_top, shaft_bottom, torus, plinth = 2.1, 1.85, 1.15, 1.4, 1.75, 2.1
    neck, base = t + 1.6, b - 1.85
    right = [(abacus, t), (abacus, t + 0.8), (echinus_top, t + 0.8)]
    # Echinus: convex cushion curving in to the shaft.
    right += sample(lambda s: (shaft_top + (echinus_top - shaft_top) * math.cos(s * math.pi / 2),
                               t + 0.8 + (neck - t - 0.8) * math.sin(s * math.pi / 2)), 8)
    # Shaft with a slight taper and entasis.
    half = lambda s: shaft_top + (shaft_bottom - shaft_top) * s + 0.07 * math.sin(math.pi * s)
    right += sample(lambda s: (half(s), neck + (base - neck) * s), 10)
    # Base: rounded torus on a square plinth.
    right += sample(lambda s: (shaft_bottom + (torus - shaft_bottom) * math.sin(math.pi * s), base + 0.75 * s), 8)
    right += [(plinth, base + 0.75), (plinth, b)]
    silhouette = poly([(x + dx, y) for dx, y in right + [(-dx, y) for dx, y in reversed(right)]])

    def band(y, thickness):
        w = half((y - neck) / (base - neck)) - 0.1
        return poly([(x - w, y), (x + w, y), (x + w, y + thickness), (x - w, y + thickness)])

    details = [band(neck + 0.15, 0.12), band(neck + 0.38, 0.12), band(neck + 0.85, 0.14)]  # annulets, groove
    y0, y1 = neck + 1.25, base - 0.35
    for frac in (-0.5, 0, 0.5):  # flutes
        at = lambda y, o: x + frac * half((y - neck) / (base - neck)) + o
        details.append(poly([(at(y0, -0.13), y0), (at(y0, 0.13), y0), (at(y1, 0.13), y1), (at(y1, -0.13), y1)]))
    return silhouette, " ".join(details)


columns = [(cx + dx, *column(cx + dx, h)) for dx, h in [(-10, 9), (-5, 15), (0, 21), (5, 15), (10, 9)]]


# ---------------------------------------------------------------- Android vector drawables

def vector(body, size=108, viewport=108, aapt=True):
    ns = ' xmlns:aapt="http://schemas.android.com/aapt"' if aapt else ""
    return (f'<vector xmlns:android="http://schemas.android.com/apk/res/android"{ns}\n'
            f'    android:width="{size}dp"\n    android:height="{size}dp"\n'
            f'    android:viewportWidth="{viewport}"\n    android:viewportHeight="{viewport}">\n{body}</vector>\n')


def gradient_path(data, stops, kind, coords, indent="    "):
    items = "\n".join(f'{indent}            <item android:offset="{o}" android:color="{c}" />' for o, c in stops)
    if kind == "linear":
        x0, y0, x1, y1 = coords
        attrs = f'android:type="linear" android:startX="{x0}" android:startY="{y0}" android:endX="{x1}" android:endY="{y1}"'
    else:
        x, y, r = coords
        attrs = f'android:type="radial" android:centerX="{x}" android:centerY="{y}" android:gradientRadius="{r}"'
    return (f'{indent}<path android:pathData="{data}">\n{indent}    <aapt:attr name="android:fillColor">\n'
            f'{indent}        <gradient {attrs}>\n{items}\n{indent}        </gradient>\n'
            f'{indent}    </aapt:attr>\n{indent}</path>\n')


def android_foreground():
    body = (f'    <group android:pivotX="{ART_PIVOT[0]}" android:pivotY="{ART_PIVOT[1]}" '
            f'android:scaleX="{ART_SCALE}" android:scaleY="{ART_SCALE}">\n')
    body += "    <!-- Soft shadow under the pin tip: a radial gradient squashed into an ellipse -->\n"
    body += f'    <group android:pivotX="{cx}" android:pivotY="{tip + 1}" android:scaleY="0.3">\n'
    body += gradient_path(circle(cx, tip + 1, 10), shadow_stops, "radial", (cx, tip + 1, 10), "        ")
    body += "    </group>\n"
    body += "    <!-- Pin with 3D shading: rim, lit body, darker right flank, highlight -->\n"
    body += (f'    <path android:fillColor="{rim_color}" android:strokeColor="{rim_color}" android:strokeWidth="0.35" '
             f'android:strokeLineJoin="round" android:pathData="{pin}" />\n')
    body += gradient_path(pin, body_stops, "radial", (*light, 36))
    body += gradient_path(pin, flank_stops, "linear", (cx - 2, 0, cx + R, 0))
    body += gradient_path(highlight, highlight_stops, "linear", (cx - R, cy, cx, cy - R))
    body += "    <!-- Sound wave of Doric columns, each shaded as a rounded drum, with gold details -->\n"
    for x, silhouette, details in columns:
        body += gradient_path(silhouette, column_stops, "linear", (x - 1.45, 0, x + 1.45, 0))
        body += gradient_path(details, detail_stops, "linear", (x - 1.45, 0, x + 1.45, 0))
    body += "    </group>\n"
    return vector(body)


def android_background():
    return vector(gradient_path("M0,0h108v108h-108z", background_stops, "linear", (0, 0, 108, 108)))


def android_monochrome():
    # Themed icons only use alpha, so the columns are cut out of the pin. The flutes are left out
    # because they vanish at themed-icon size.
    allcols = " ".join(s for _, s, _ in columns)
    return vector(f'    <group android:pivotX="{ART_PIVOT[0]}" android:pivotY="{ART_PIVOT[1]}" '
                  f'android:scaleX="{ART_SCALE}" android:scaleY="{ART_SCALE}">\n'
                  f'        <path android:fillColor="#FFFFFFFF" android:fillType="evenOdd" android:pathData="{pin} {allcols}" />\n'
                  f'    </group>\n', aapt=False)


def android_notification():
    # Status bar icon: white silhouette on transparent, columns cut out (details are too small at 24dp).
    # Fit a 50-unit square centred on the pin into a 24dp viewport.
    allcols = " ".join(s for _, s, _ in columns)
    size, x0, y0 = 50, cx - 25, (cy - R + tip) / 2 - 25
    body = (f'    <group android:scaleX="{24 / size}" android:scaleY="{24 / size}" '
            f'android:translateX="{-x0 * 24 / size:.3f}" android:translateY="{-y0 * 24 / size:.3f}">\n'
            f'        <path android:fillColor="#FFFFFFFF" android:fillType="evenOdd" android:pathData="{pin} {allcols}" />\n'
            f'    </group>\n')
    return vector(body, size=24, viewport=24, aapt=False)


# ---------------------------------------------------------------- SVG (store artwork, previews)

def svg_stops(stops):
    out = ""
    for o, c in stops:
        alpha = int(c[1:3], 16) / 255 if len(c) == 9 else 1
        out += f'<stop offset="{o}" stop-color="#{c[-6:]}" stop-opacity="{alpha:.2f}"/>'
    return out


def svg_foreground(prefix):
    """Foreground artwork in 108-unit coordinates; ids are prefixed so it can be embedded twice."""
    p = prefix
    defs = [
        f'<radialGradient id="{p}sh" gradientUnits="userSpaceOnUse" cx="{cx}" cy="{tip + 1}" r="10">{svg_stops(shadow_stops)}</radialGradient>',
        f'<radialGradient id="{p}body" gradientUnits="userSpaceOnUse" cx="{light[0]}" cy="{light[1]}" r="36">{svg_stops(body_stops)}</radialGradient>',
        f'<linearGradient id="{p}flank" gradientUnits="userSpaceOnUse" x1="{cx - 2}" y1="0" x2="{cx + R}" y2="0">{svg_stops(flank_stops)}</linearGradient>',
        f'<linearGradient id="{p}hl" gradientUnits="userSpaceOnUse" x1="{cx - R}" y1="{cy}" x2="{cx}" y2="{cy - R}">{svg_stops(highlight_stops)}</linearGradient>',
    ]
    shapes = [
        f'<g transform="translate(0 {(tip + 1) * 0.7}) scale(1 0.3)"><path fill="url(#{p}sh)" d="{circle(cx, tip + 1, 10)}"/></g>',
        f'<path fill="{rim_color}" stroke="{rim_color}" stroke-width="0.35" stroke-linejoin="round" d="{pin}"/>',
        f'<path fill="url(#{p}body)" d="{pin}"/><path fill="url(#{p}flank)" d="{pin}"/><path fill="url(#{p}hl)" d="{highlight}"/>',
    ]
    for i, (x, silhouette, details) in enumerate(columns):
        for kind, stops, data in (("c", column_stops, silhouette), ("d", detail_stops, details)):
            defs.append(f'<linearGradient id="{p}{kind}{i}" gradientUnits="userSpaceOnUse" x1="{x - 1.45}" y1="0" '
                        f'x2="{x + 1.45}" y2="0">{svg_stops(stops)}</linearGradient>')
            shapes.append(f'<path fill="url(#{p}{kind}{i})" d="{data}"/>')
    px, py = ART_PIVOT
    transform = f"translate({px * (1 - ART_SCALE):.3f} {py * (1 - ART_SCALE):.3f}) scale({ART_SCALE})"
    return "".join(defs), f'<g transform="{transform}">' + "".join(shapes) + "</g>"


def svg_background(gid):
    return (f'<linearGradient id="{gid}" x1="0" y1="0" x2="1" y2="1">{svg_stops(background_stops)}</linearGradient>',
            f'<rect width="108" height="108" fill="url(#{gid})"/>')


def svg_play_icon():
    # Play Store icon: 512x512 full square (Play rounds the corners itself). It shows the central
    # 72dp that launchers show, so the pin has the same size it has on the home screen.
    bd, bs = svg_background("bg")
    fd, fs = svg_foreground("f")
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="18 18 72 72" width="512" height="512">'
            f'<defs>{bd}{fd}</defs>{bs}{fs}</svg>')


def svg_preview():
    # How most launchers show it: the central 72dp cropped to a circle.
    bd, bs = svg_background("bg")
    fd, fs = svg_foreground("f")
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="18 18 72 72" width="512" height="512">'
            f'<defs>{bd}{fd}<clipPath id="clip"><circle cx="54" cy="54" r="36"/></clipPath></defs>'
            f'<g clip-path="url(#clip)">{bs}{fs}</g></svg>')


def svg_feature_graphic():
    # Play Store feature graphic: 1024x500, artwork left, name and tagline right.
    fd, fs = svg_foreground("f")
    bg = svg_stops(background_stops)
    scale = 6.6  # pin + shadow (y 30..83) becomes ~350px tall
    tx, ty = 240 - cx * scale, 250 - 56.5 * scale
    return (f'<svg xmlns="http://www.w3.org/2000/svg" width="1024" height="500" viewBox="0 0 1024 500">'
            f'<defs><linearGradient id="bg" x1="0" y1="0" x2="1" y2="1">{bg}</linearGradient>{fd}</defs>'
            f'<rect width="1024" height="500" fill="url(#bg)"/>'
            f'<g transform="translate({tx:.1f} {ty:.1f}) scale({scale})">{fs}</g>'
            f'<text x="450" y="235" font-family="Georgia, \'Times New Roman\', serif" font-size="92" fill="#FFF4E0">Guided Tour</text>'
            f'<rect x="454" y="268" width="96" height="4" rx="2" fill="#F1BF48"/>'
            f'<text x="452" y="330" font-family="-apple-system, Helvetica, Arial, sans-serif" font-size="34" fill="#F6E2C6">'
            f'Scan a plaque. Listen as you explore.</text>'
            f'</svg>')


def write(path, content):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        f.write(content)
    print("wrote", os.path.relpath(path, ROOT))


if __name__ == "__main__":
    write(os.path.join(RES, "drawable", "ic_launcher_foreground.xml"), android_foreground())
    write(os.path.join(RES, "drawable", "ic_launcher_background.xml"), android_background())
    write(os.path.join(RES, "drawable", "ic_launcher_monochrome.xml"), android_monochrome())
    write(os.path.join(RES, "drawable", "ic_notification.xml"), android_notification())
    write(os.path.join(STORE, "play-store-icon.svg"), svg_play_icon())
    write(os.path.join(STORE, "feature-graphic.svg"), svg_feature_graphic())
    write(os.path.join(ROOT, "design", "preview.svg"), svg_preview())
