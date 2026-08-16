# ToneBake Calibration

This tool learns a reusable HLG/BT.2020 → SDR/BT.709 transform from a user-owned aligned source/reference video pair.

It deliberately avoids vendor LUTs, SDKs, private APIs, or extracted implementations. The fitted model is constrained to stay visually clean:

1. monotone luminance/tone curve;
2. standard linear BT.2020 → BT.709 primaries conversion;
3. hue-preserving saturation adjustment as a function of luminance.

A free RGB affine fit is also reported only as a diagnostic baseline. In our first ToneBake calibration pair, the constrained model had better structural similarity and much better P90 color error than the free affine model, which supports using the constrained path for the Android shader.

## Requirements

- Python 3.11+
- FFmpeg with `zscale`
- numpy
- scipy
- opencv-python
- scikit-image
- matplotlib

Install Python dependencies with:

```bash
pip install -r requirements.txt
```

## Usage

```bash
python calibrate.py ORIGINAL_HLG.mp4 REFERENCE_SDR.mp4 --out calibration_out
```

Useful options:

```bash
--frames 12
--width 336
--height 188
--max-offset 4
```

The default resolution is intentionally small because this stage fits global tone/color behavior rather than spatial detail. Increase sampling only after the pipeline is stable.

## Outputs

- `calibration.json` — machine-readable fitted curves, matrices, metrics, metadata and alignment
- `REPORT.md` — compact human-readable report
- `luminance_curve.csv/png` — learned tone mapping
- `saturation_curve.csv/png` — hue-preserving saturation-by-luminance mapping
- `alignment.png` — automatic frame-offset diagnostic
- `model_preview.png` / `reference_preview.png` / `error_heatmap.png` — visual diagnostics

## Android integration plan

The recommended clean model should be translated into a custom Media3/OpenGL effect for ToneBake v0.2. The current `Brightness`/`Contrast`/HSL Vivid presets remain only as a legacy baseline for comparison.
