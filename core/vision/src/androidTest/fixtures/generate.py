"""Generate original synthetic, noncaptured golden screenshots; Python stdlib only."""
import math
import random
import struct
import zlib
from pathlib import Path

WIDTH, HEIGHT = 600, 700
CASES = {
    "square": [(100, 150), (500, 150), (500, 550), (100, 550)],
    "rotated": [(170, 100), (550, 220), (430, 600), (50, 480)],
    "diamond": [(300, 100), (550, 350), (300, 600), (50, 350)],
    "diamond-noisy": [(300, 100), (550, 350), (300, 600), (50, 350)],
    "diamond-jitter": [(301, 100), (550, 350), (300, 600), (49, 350)],
    "scaled": [(180, 230), (420, 230), (420, 470), (180, 470)],
    "perspective": [(130, 140), (490, 170), (520, 550), (80, 530)],
    "noisy": [(100, 150), (500, 150), (500, 550), (100, 550)],
    "clipped": [(-30, 150), (400, 150), (400, 550), (-30, 550)],
    "thin": [(100, 200), (500, 200), (500, 330), (100, 330)],
    "blank": [],
    "multiple": [],
}


def inside(x, y, polygon):
    return all((b[0] - a[0]) * (y - a[1]) - (b[1] - a[1]) * (x - a[0]) >= 0
               for a, b in zip(polygon, polygon[1:] + polygon[:1]))


def diagonal_center(p):
    a, b, c, d = p
    u, v = (c[0] - a[0], c[1] - a[1]), (d[0] - b[0], d[1] - b[1])
    t = ((b[0] - a[0]) * v[1] - (b[1] - a[1]) * v[0]) / (u[0] * v[1] - u[1] * v[0])
    return a[0] + t * u[0], a[1] + t * u[1]


def chunk(kind, data):
    return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data))


def generate(name, corners, target):
    rng = random.Random(21)
    polygons = [corners] if corners else []
    if name == "multiple":
        polygons = [[(50, 60), (290, 60), (290, 300), (50, 300)],
                    [(300, 390), (540, 390), (540, 630), (300, 630)]]
    centers = [diagonal_center(p) for p in polygons]
    raw = bytearray()
    for y in range(HEIGHT):
        raw.append(0)
        for x in range(WIDTH):
            color = (28, 35, 48)
            for p, center in zip(polygons, centers):
                if inside(x, y, p):
                    color = (215, 185, 130)
                    if math.hypot(x - center[0], y - center[1]) < 18:
                        color = (30, 60, 230)
                    for cx, cy in p:
                        if math.hypot(x - (cx * .86 + center[0] * .14),
                                      y - (cy * .86 + center[1] * .14)) < 10:
                            color = (35, 25, 20)
            if "noisy" in name:
                color = tuple(max(0, min(255, c + rng.randint(-18, 18))) for c in color)
            raw.extend(color)
    png = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", WIDTH, HEIGHT, 8, 2, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")
    (target / (name + ".png")).write_bytes(png)


if __name__ == "__main__":
    output = Path(__file__).resolve().parents[1] / "assets" / "board-v1"
    output.mkdir(parents=True, exist_ok=True)
    for key, polygon in CASES.items():
        generate(key, polygon, output)
