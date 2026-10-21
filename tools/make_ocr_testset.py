"""Generates synthetic plaque photos plus their true text, for the in-app accuracy test.

    python3 tools/make_ocr_testset.py OUTPUT_DIR

Needs Pillow and numpy. Push the result to the phone:
    adb push OUTPUT_DIR/. /data/local/tmp/testset/
These are synthetic: they check the pipeline's handling of tilt, contrast, blur and layout,
not how it copes with real plaques. Add your own photos for that.
"""
import random
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont



def first_existing(*paths):
    return next((p for p in paths if Path(p).exists()), paths[-1])


# Georgia on macOS, DejaVu Serif on Linux (CI).
FONT = first_existing("/System/Library/Fonts/Supplemental/Georgia.ttf", "/usr/share/fonts/truetype/dejavu/DejaVuSerif.ttf")
BOLD = first_existing("/System/Library/Fonts/Supplemental/Georgia Bold.ttf", "/usr/share/fonts/truetype/dejavu/DejaVuSerif-Bold.ttf")

PARAGRAPH = [
    "The Parthenon was built between 447 and 432 BC",
    "as a temple dedicated to the goddess Athena.",
    "Its marble sculptures are among the finest works",
    "of ancient Greek art. Many of them are now",
    "displayed in museums around the world.",
]
TITLE = "THE ACROPOLIS OF ATHENS"
COL1 = ["The citadel rose above the city", "and was a place of worship", "for more than a thousand years."]
COL2 = ["Pericles led the great building", "programme that created the", "temples seen here today."]
CREDIT = "Photo credit: Hellenic Ministry of Culture"
# A bilingual plaque: the local language first, then English, as many museums do.
BILINGUAL_PT = [
    "O Partenon foi construído entre 447 e 432 a.C.",
    "como templo dedicado à deusa Atena.",
]
BILINGUAL_EN = [
    "The Parthenon was built between 447 and 432 BC",
    "as a temple dedicated to the goddess Athena.",
]
NEIGHBOUR = ["Roman Athens", "Under Hadrian", "the city grew", "a new quarter", "with baths and", "a library."]


def canvas(size=(1600, 1200), color=(205, 198, 182)):
    return Image.new("RGB", size, color)


def draw_lines(img, lines, xy, size, fill=(30, 28, 26), gap=1.5, font=FONT):
    d = ImageDraw.Draw(img)
    f = ImageFont.truetype(font, size)
    x, y = xy
    for line in lines:
        d.text((x, y), line, font=f, fill=fill)
        y += int(size * gap)


def noise(img, amount, seed):
    rng = np.random.default_rng(seed)
    a = np.asarray(img).astype(np.int16) + rng.normal(0, amount, (img.height, img.width, 1)).astype(np.int16)
    return Image.fromarray(np.clip(a, 0, 255).astype(np.uint8))


def seen_at_angle(img, yaw=0, pitch=0, background=(90, 88, 84)):
    """[img] as a camera sees it turned [yaw] degrees to the side and [pitch] degrees up or down."""
    w, h = img.size
    f = 1.1 * max(w, h)  # a phone's main camera, roughly
    ry, rp = np.radians(yaw), np.radians(pitch)
    rot_y = np.array([[np.cos(ry), 0, np.sin(ry)], [0, 1, 0], [-np.sin(ry), 0, np.cos(ry)]])
    rot_x = np.array([[1, 0, 0], [0, np.cos(rp), -np.sin(rp)], [0, np.sin(rp), np.cos(rp)]])
    rot = rot_x @ rot_y

    def project(x, y):
        p = rot @ np.array([x - w / 2, y - h / 2, 0.0]) + np.array([0, 0, 1.25 * f])
        return f * p[0] / p[2], f * p[1] / p[2]

    corners = [(0, 0), (w, 0), (w, h), (0, h)]
    seen = [project(x, y) for x, y in corners]
    # Scale the result to fill the frame like a photo would.
    xs, ys = [p[0] for p in seen], [p[1] for p in seen]
    scale = 0.92 * min(w / (max(xs) - min(xs)), h / (max(ys) - min(ys)))
    cx, cy = (max(xs) + min(xs)) / 2, (max(ys) + min(ys)) / 2
    seen = [((x - cx) * scale + w / 2, (y - cy) * scale + h / 2) for x, y in seen]
    # PIL maps each output pixel back to the input: solve for that perspective transform.
    rows, rhs = [], []
    for (ox, oy), (ix, iy) in zip(seen, corners):
        rows.append([ox, oy, 1, 0, 0, 0, -ix * ox, -ix * oy]); rhs.append(ix)
        rows.append([0, 0, 0, ox, oy, 1, -iy * ox, -iy * oy]); rhs.append(iy)
    coefficients = np.linalg.solve(np.array(rows, float), np.array(rhs, float))
    out = Image.new("RGB", (w, h), background)
    warped = img.transform((w, h), Image.PERSPECTIVE, tuple(coefficients), Image.BICUBIC, fillcolor=background)
    out.paste(warped)
    return out


def main(out):
    out = Path(out)
    out.mkdir(parents=True, exist_ok=True)
    truth = " ".join(PARAGRAPH)
    cases = {}

    img = canvas(); draw_lines(img, PARAGRAPH, (120, 260), 54)
    cases["clean"] = (img, truth)

    img = canvas(); draw_lines(img, PARAGRAPH, (120, 260), 54)
    cases["tilted8"] = (img.rotate(8, expand=False, fillcolor=(90, 88, 84), resample=Image.BICUBIC), truth)

    img = canvas(color=(150, 148, 142)); draw_lines(img, PARAGRAPH, (120, 260), 54, fill=(112, 110, 104))
    cases["lowcontrast"] = (noise(img, 6, 1), truth)

    img = canvas(); draw_lines(img, PARAGRAPH, (120, 260), 54)
    cases["blurred"] = (noise(img.filter(ImageFilter.GaussianBlur(2.2)), 8, 2), truth)

    img = canvas(size=(2400, 1800), color=(120, 110, 100))
    draw_lines(img, PARAGRAPH, (1500, 1300), 36, fill=(235, 230, 220))
    cases["small_in_corner"] = (img, truth)

    img = canvas()
    draw_lines(img, [TITLE], (150, 90), 70, font=BOLD)
    draw_lines(img, COL1, (130, 300), 44)
    draw_lines(img, COL2, (860, 300), 44)
    draw_lines(img, [CREDIT], (130, 1050), 24)
    cases["two_columns"] = (img, " ".join([TITLE] + COL1 + COL2))

    # Taken with the phone turned sideways while the screen stayed upright: the text lies on its side.
    img = canvas(); draw_lines(img, PARAGRAPH, (120, 260), 54)
    cases["sideways"] = (img.rotate(90, expand=True), truth)
    img = canvas(color=(150, 148, 142)); draw_lines(img, PARAGRAPH, (120, 260), 54, fill=(112, 110, 104))
    cases["sideways_lowcontrast"] = (noise(img, 6, 3).rotate(270, expand=True), truth)

    # The plaque in the middle, with part of the next plaque cut off at the right edge.
    img = canvas(color=(120, 110, 100))
    ImageDraw.Draw(img).rectangle((60, 220, 1240, 760), fill=(205, 198, 182))
    draw_lines(img, PARAGRAPH, (100, 270), 42)
    ImageDraw.Draw(img).rectangle((1420, 160, 1700, 900), fill=(205, 198, 182))
    draw_lines(img, NEIGHBOUR, (1450, 220), 42)
    cases["side_text"] = (img, truth)

    img = canvas()
    draw_lines(img, BILINGUAL_PT, (120, 260), 54)
    draw_lines(img, BILINGUAL_EN, (120, 620), 54, fill=(70, 66, 62))
    cases["bilingual"] = (img, " ".join(BILINGUAL_PT + BILINGUAL_EN))
    # Plaques photographed at an angle: from the side, from below (looking up), and both.
    for yaw, pitch in [(20, 0), (35, 0), (50, 0), (0, -30), (0, -45), (35, -25)]:
        img = canvas(); draw_lines(img, PARAGRAPH, (120, 260), 54)
        name = "angle" + (f"_side{yaw}" if yaw else "") + (f"_below{-pitch}" if pitch else "")
        cases[name] = (noise(seen_at_angle(img, yaw, pitch), 5, yaw + pitch + 100), truth)
    img = canvas()
    draw_lines(img, BILINGUAL_PT, (120, 260), 54)
    draw_lines(img, BILINGUAL_EN, (120, 620), 54, fill=(70, 66, 62))
    cases["angle_side40_bilingual"] = (noise(seen_at_angle(img, 40, 0), 5, 140), " ".join(BILINGUAL_PT + BILINGUAL_EN))
    img = canvas()
    draw_lines(img, [TITLE], (150, 90), 70, font=BOLD)
    draw_lines(img, COL1, (130, 300), 44)
    draw_lines(img, COL2, (860, 300), 44)
    cases["angle_side35_columns"] = (noise(seen_at_angle(img, -35, 0), 5, 135), " ".join([TITLE] + COL1 + COL2))

    # The languages each case should be detected in, in reading order (checked by the accuracy report).
    languages = {name: "en" for name in cases} | {"bilingual": "pt,en", "angle_side40_bilingual": "pt,en"}

    for name, (image, text) in cases.items():
        image.save(out / f"{name}.jpg", quality=88)
        (out / f"{name}.txt").write_text(text + "\n")
        (out / f"{name}.lang").write_text(languages[name] + "\n")
    print(f"wrote {len(cases)} cases to {out}")


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "ocr_testset")
