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

    # The plaque in the middle, with part of the next plaque cut off at the right edge.
    img = canvas(color=(120, 110, 100))
    ImageDraw.Draw(img).rectangle((60, 220, 1240, 760), fill=(205, 198, 182))
    draw_lines(img, PARAGRAPH, (100, 270), 42)
    ImageDraw.Draw(img).rectangle((1420, 160, 1700, 900), fill=(205, 198, 182))
    draw_lines(img, NEIGHBOUR, (1450, 220), 42)
    cases["side_text"] = (img, truth)

    for name, (image, text) in cases.items():
        image.save(out / f"{name}.jpg", quality=88)
        (out / f"{name}.txt").write_text(text + "\n")
    print(f"wrote {len(cases)} cases to {out}")


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "ocr_testset")
