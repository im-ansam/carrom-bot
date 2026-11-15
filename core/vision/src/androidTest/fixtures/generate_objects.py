"""Original rectified synthetic screenshots and annotations, no captured pixels/dependencies."""
import json
import math
import random
import struct
import zlib
from pathlib import Path

OUT = Path(__file__).resolve().parents[1] / "assets" / "objects-v1"
COLORS = {"POCKET": (10, 10, 10), "BLACK_DISC": (60, 60, 60),
          "WHITE_DISC": (245, 245, 245), "QUEEN": (240, 20, 20),
          "STRIKER": (20, 80, 240), "OBSTACLE": (20, 220, 40)}
OBJECTS = [("POCKET", x, y, 14) for x, y in [(25, 25), (375, 25), (25, 375), (375, 375)]]
OBJECTS += [("BLACK_DISC", 100, 120, 18), ("WHITE_DISC", 200, 100, 18),
            ("QUEEN", 280, 180, 18), ("STRIKER", 200, 310, 26),
            ("OBSTACLE", 90, 260, 20)]


def chunk(kind, data):
    return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data))


def generate(name, size):
    rng = random.Random(22)
    raw = bytearray()
    for row in range(size):
        raw.append(0)
        for col in range(size):
            x, y = col * 400 / (size - 1), row * 400 / (size - 1)
            color = (190, 165, 120)
            if name in ("complete", "noisy", "scaled"):
                for kind, cx, cy, radius in OBJECTS:
                    hit = abs(x-cx) <= 16 and abs(y-cy) <= 12 if kind == "OBSTACLE" else math.hypot(x-cx, y-cy) <= radius
                    if hit:
                        color = COLORS[kind]
            if name == "occluded":
                # Same-color merged circles, half-circle, boundary clipping: all unclassifiable.
                if any(math.hypot(x-cx, y-cy) <= 18 for cx, cy in [(100, 100), (125, 100), (0, 250)]):
                    color = COLORS["QUEEN"]
                if math.hypot(x-280, y-200) <= 18 and x <= 280:
                    color = COLORS["WHITE_DISC"]
            if name == "false-positives":
                # Hollow ring, tiny circle, elongated line, interior pocket-colored circle.
                if 14 <= math.hypot(x-100, y-100) <= 18 or math.hypot(x-200, y-100) <= 3:
                    color = COLORS["QUEEN"]
                if 240 <= x <= 320 and 195 <= y <= 201:
                    color = COLORS["WHITE_DISC"]
                if math.hypot(x-200, y-300) <= 14:
                    color = COLORS["POCKET"]
            if name == "noisy":
                color = tuple(max(0, min(255, c + rng.randint(-7, 7))) for c in color)
            raw.extend(color)
    png = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", size, size, 8, 2, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")
    (OUT / (name + ".png")).write_bytes(png)
    expected = OBJECTS if name in ("complete", "noisy", "scaled") else []
    return {"name": name, "size": size, "objects": [dict(kind=k, x=x, y=y, radius=r) for k, x, y, r in expected]}


if __name__ == "__main__":
    OUT.mkdir(parents=True, exist_ok=True)
    annotations = [generate(n, s) for n, s in [("complete", 401), ("scaled", 601),
                   ("noisy", 401), ("occluded", 401), ("false-positives", 401), ("empty", 401)]]
    (OUT / "annotations.json").write_text(json.dumps(annotations, indent=2) + "\n", encoding="utf-8")
