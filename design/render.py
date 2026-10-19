"""Renders the store artwork SVGs written by make_icon.py into the PNGs the Play Store needs.

Run from the project root after make_icon.py:  python3 design/render.py
Uses macOS Quick Look (qlmanage) as the SVG renderer, so it needs macOS. Quick Look only renders
square images reliably, so the 1024x500 feature graphic is drawn on a square canvas and cropped.
"""
import os
import shutil
import struct
import subprocess
import tempfile
import zlib

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
STORE = os.path.join(ROOT, "design", "store")


def render_square(svg_text, size):
    """Renders square SVG text to a size x size PNG and returns its path."""
    tmp = tempfile.mkdtemp()
    src = os.path.join(tmp, "art.svg")
    with open(src, "w") as f:
        f.write(svg_text)
    subprocess.run(["qlmanage", "-t", "-s", str(size), "-o", tmp, src], check=True, capture_output=True)
    return os.path.join(tmp, "art.svg.png")


def read_png(path):
    data, pos, idat = open(path, "rb").read(), 8, b""
    while pos < len(data):
        n = struct.unpack(">I", data[pos:pos + 4])[0]
        kind, chunk = data[pos + 4:pos + 8], data[pos + 8:pos + 8 + n]
        if kind == b"IHDR":
            width, height, depth, color = struct.unpack(">IIBB", chunk[:10])
            assert depth == 8 and color in (2, 6), "expected 8-bit RGB/RGBA"
        elif kind == b"IDAT":
            idat += chunk
        pos += 12 + n
    bpp, raw, rows, i = (4 if color == 6 else 3), zlib.decompress(idat), [], 0
    stride, prev = width * bpp, bytearray(width * bpp)
    for _ in range(height):
        f, line = raw[i], bytearray(raw[i + 1:i + 1 + stride])
        i += 1 + stride
        for x in range(stride):
            a = line[x - bpp] if x >= bpp else 0
            b, c = prev[x], (prev[x - bpp] if x >= bpp else 0)
            if f == 1:
                line[x] = (line[x] + a) & 255
            elif f == 2:
                line[x] = (line[x] + b) & 255
            elif f == 3:
                line[x] = (line[x] + (a + b) // 2) & 255
            elif f == 4:
                p = a + b - c
                pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
                line[x] = (line[x] + (a if pa <= pb and pa <= pc else b if pb <= pc else c)) & 255
        rows.append(bytes(line))
        prev = line
    return width, height, bpp, rows


def write_png(path, width, rows, bpp):
    def chunk(kind, body):
        return struct.pack(">I", len(body)) + kind + body + struct.pack(">I", zlib.crc32(kind + body) & 0xFFFFFFFF)
    header = struct.pack(">IIBBBBB", width, len(rows), 8, 6 if bpp == 4 else 2, 0, 0, 0)
    body = zlib.compress(b"".join(b"\x00" + r for r in rows), 9)
    with open(path, "wb") as f:
        f.write(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", header) + chunk(b"IDAT", body) + chunk(b"IEND", b""))


def to_rgb(rows, bpp):
    return rows if bpp == 3 else [b"".join(r[i:i + 3] for i in range(0, len(r), 4)) for r in rows]


def save(name):
    print("wrote", os.path.relpath(os.path.join(STORE, name), ROOT))


if __name__ == "__main__":
    # Play Store icon: 512x512 32-bit PNG.
    icon = render_square(open(os.path.join(STORE, "play-store-icon.svg")).read(), 512)
    w, h, bpp, rows = read_png(icon)
    assert (w, h) == (512, 512), (w, h)
    write_png(os.path.join(STORE, "play-store-icon-512.png"), w, rows, bpp)
    save("play-store-icon-512.png")

    # Feature graphic: 1024x500 24-bit PNG (no alpha), rendered on a 1024 square then cropped.
    svg = open(os.path.join(STORE, "feature-graphic.svg")).read()
    square = svg.replace('width="1024" height="500" viewBox="0 0 1024 500"', 'width="1024" height="1024" viewBox="0 0 1024 1024"', 1)
    w, h, bpp, rows = read_png(render_square(square, 1024))
    assert (w, h) == (1024, 1024), (w, h)
    write_png(os.path.join(STORE, "feature-graphic-1024x500.png"), 1024, to_rgb(rows[:500], bpp), 3)
    save("feature-graphic-1024x500.png")

    # README preview (launcher look).
    preview = render_square(open(os.path.join(ROOT, "design", "preview.svg")).read(), 512)
    shutil.copy(preview, os.path.join(ROOT, "design", "preview.png"))
    print("wrote design/preview.png")
