# Synthetic board screenshots v1

Original fixture artwork generated for this repository; no captured screen data.
Regenerate from repository root with:

```powershell
py core/vision/src/androidTest/fixtures/generate.py
```

Python standard library only. Coordinates in the generator and device tests are
the golden annotations. Frames are 600x700 RGB PNGs, decoded to RGBA by the tests.
Seed 21 adds bounded +/-18 channel noise. Cases cover square, scale, rotation,
perspective, noise, missing/clipped/thin geometry and two plausible boards.
The blue marker is at the intersection of the diagonals, the projective center.
Detection corner tolerance is 3px; transform round trips use 1e-7 mm/pixel tolerance.
These fixtures verify geometry mechanics and do not establish actual game accuracy.
