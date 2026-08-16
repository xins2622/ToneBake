#!/usr/bin/env python3
"""ToneBake calibration helper.

Learns a clean, reusable HLG/BT.2020 -> SDR/BT.709 mapping from a user-owned
reference pair. The intended model is deliberately constrained:
  1) monotone luminance curve,
  2) standard linear BT.2020 -> BT.709 primary conversion,
  3) hue-preserving saturation-vs-luminance curve.

A free affine RGB fit is also reported as a diagnostic baseline, but it is not
recommended as the production transform because it can create local hue errors.
"""
from __future__ import annotations

import argparse
import json
import subprocess
from pathlib import Path

import cv2
import matplotlib.pyplot as plt
import numpy as np
from scipy.interpolate import PchipInterpolator
from skimage.metrics import structural_similarity

M_BT2020_TO_XYZ = np.array([
    [0.6369580483, 0.1446169036, 0.1688809752],
    [0.2627002120, 0.6779980715, 0.0593017165],
    [0.0000000000, 0.0280726930, 1.0609850577],
], dtype=np.float64)
M_BT709_TO_XYZ = np.array([
    [0.4123907993, 0.3575843394, 0.1804807884],
    [0.2126390059, 0.7151686788, 0.0721923154],
    [0.0193308187, 0.1191947798, 0.9505321522],
], dtype=np.float64)
M_BT2020_TO_BT709 = np.linalg.inv(M_BT709_TO_XYZ) @ M_BT2020_TO_XYZ


def check_output(cmd: list[str]) -> bytes:
    return subprocess.check_output(cmd)


def ffprobe(path: Path) -> dict:
    raw = subprocess.check_output([
        "ffprobe", "-v", "error", "-select_streams", "v:0",
        "-show_entries",
        "stream=width,height,avg_frame_rate,duration,color_space,color_transfer,color_primaries,pix_fmt,codec_name",
        "-of", "json", str(path),
    ], text=True)
    return json.loads(raw)["streams"][0]


def parse_fraction(value: str) -> float:
    a, b = value.split("/")
    return float(a) / float(b)


def decode_all_rgb8(path: Path, width: int, height: int) -> np.ndarray:
    raw = check_output([
        "ffmpeg", "-v", "error", "-i", str(path),
        "-vf", f"scale={width}:{height}:flags=area,format=rgb24",
        "-an", "-sn", "-f", "rawvideo", "-pix_fmt", "rgb24", "-",
    ])
    frame_size = width * height * 3
    count = len(raw) // frame_size
    return np.frombuffer(raw, np.uint8, count=count * frame_size).reshape(count, height, width, 3)


def decode_selected_linear(
    path: Path, indices: np.ndarray, width: int, height: int, *, original_hlg: bool
) -> np.ndarray:
    if width % 2 or height % 2:
        raise ValueError("width and height must be even")
    expression = "+".join(f"eq(n\\,{int(i)})" for i in indices)
    if original_hlg:
        zscale = "zscale=min=bt2020nc:tin=arib-std-b67:pin=bt2020:m=gbr:t=linear:p=bt2020"
    else:
        zscale = "zscale=min=bt709:tin=bt709:pin=bt709:m=gbr:t=linear:p=bt709"
    vf = f"select='{expression}',scale={width}:{height}:flags=area,{zscale},format=gbrpf32le"
    raw = check_output([
        "ffmpeg", "-v", "error", "-i", str(path), "-vf", vf,
        "-fps_mode", "passthrough", "-an", "-sn",
        "-f", "rawvideo", "-pix_fmt", "gbrpf32le", "-",
    ])
    values_per_frame = width * height * 3
    array = np.frombuffer(raw, np.float32)
    decoded = array.size // values_per_frame
    if decoded != len(indices):
        raise RuntimeError(f"decoded {decoded} frames, expected {len(indices)}")
    planes = array.reshape(decoded, 3, height, width)
    return np.stack([planes[:, 2], planes[:, 0], planes[:, 1]], axis=-1)


def edge_signatures(frames: np.ndarray) -> np.ndarray:
    signatures = []
    for rgb in frames:
        gray = cv2.cvtColor(rgb, cv2.COLOR_RGB2GRAY)
        gx = cv2.Sobel(gray, cv2.CV_32F, 1, 0, ksize=3)
        gy = cv2.Sobel(gray, cv2.CV_32F, 0, 1, ksize=3)
        v = cv2.magnitude(gx, gy).ravel().astype(np.float64)
        v -= v.mean()
        v /= np.linalg.norm(v) + 1e-12
        signatures.append(v)
    return np.stack(signatures)


def find_frame_offset(original: np.ndarray, reference: np.ndarray, max_offset: int = 4) -> tuple[int, dict[int, float]]:
    eo = edge_signatures(original)
    er = edge_signatures(reference)
    n = min(len(eo), len(er))
    scores: dict[int, float] = {}
    for offset in range(-max_offset, max_offset + 1):
        if offset >= 0:
            a, b = eo[: n - offset], er[offset:n]
        else:
            a, b = eo[-offset:n], er[: n + offset]
        scores[offset] = float(np.mean(np.sum(a * b, axis=1)))
    return max(scores, key=scores.get), scores


def luma_bt2020(rgb: np.ndarray) -> np.ndarray:
    return rgb[..., 0] * 0.2627002120 + rgb[..., 1] * 0.6779980715 + rgb[..., 2] * 0.0593017165


def luma_bt709(rgb: np.ndarray) -> np.ndarray:
    return rgb[..., 0] * 0.2126390059 + rgb[..., 1] * 0.7151686788 + rgb[..., 2] * 0.0721923154


def fit_monotone_luminance_curve(x: np.ndarray, y: np.ndarray, bins: int = 72):
    x, y = x.ravel(), y.ravel()
    valid = np.isfinite(x) & np.isfinite(y) & (x >= 0) & (y >= 0)
    x, y = x[valid], y[valid]
    low, high = np.percentile(x, [0.1, 99.9])
    valid = (x >= low) & (x <= high)
    x, y = x[valid], y[valid]
    edges = np.quantile(x, np.linspace(0, 1, bins + 1))
    xs, ys = [], []
    for i in range(bins):
        mask = (x >= edges[i]) & (x < edges[i + 1] if i < bins - 1 else x <= edges[i + 1])
        if mask.sum() > 50:
            xs.append(float(np.median(x[mask])))
            ys.append(float(np.median(y[mask])))
    xs = np.asarray(xs)
    ys = np.maximum.accumulate(np.asarray(ys))
    keep = np.r_[True, np.diff(xs) > 1e-10]
    xs, ys = xs[keep], ys[keep]
    return xs, ys, PchipInterpolator(xs, ys, extrapolate=True)


def apply_luminance_curve(rgb2020: np.ndarray, curve: PchipInterpolator) -> np.ndarray:
    y = luma_bt2020(rgb2020)
    target = np.clip(curve(np.clip(y, 0, None)), 0, None)
    return rgb2020 * (target / np.maximum(y, 1e-6))[..., None]


def fit_saturation_by_luminance(src709: np.ndarray, dst709: np.ndarray, bins: int = 20):
    y_src = luma_bt709(src709)
    y_dst = luma_bt709(dst709)
    chroma_src = src709 - y_src[..., None]
    chroma_dst = dst709 - y_dst[..., None]
    denominator = np.sum(chroma_src * chroma_src, axis=-1) + 1e-8
    factor = np.sum(chroma_src * chroma_dst, axis=-1) / denominator
    chroma_mag = np.sqrt(denominator)
    valid = (
        np.isfinite(factor) & (chroma_mag > 0.01) & (y_src >= 0) & (y_src <= 1.2)
        & (factor > -1) & (factor < 5)
    )
    edges = np.linspace(0, 1, bins + 1)
    centers, factors = [], []
    for lo, hi in zip(edges[:-1], edges[1:]):
        mask = valid & (y_src >= lo) & (y_src < hi)
        if mask.sum() > 100:
            centers.append((lo + hi) / 2)
            factors.append(float(np.median(factor[mask])))
    centers = np.asarray(centers)
    factors = np.asarray(factors)
    return centers, factors, PchipInterpolator(centers, factors, extrapolate=True)


def apply_hue_preserving_saturation(rgb709: np.ndarray, curve: PchipInterpolator, centers: np.ndarray) -> np.ndarray:
    y = luma_bt709(rgb709)
    chroma = rgb709 - y[..., None]
    factor = np.clip(curve(np.clip(y, centers[0], centers[-1])), 0.3, 2.5)
    return y[..., None] + chroma * factor[..., None]


def fit_affine_rgb(src: np.ndarray, dst: np.ndarray, max_samples: int = 400_000):
    s = src.reshape(-1, 3).astype(np.float64)
    d = dst.reshape(-1, 3).astype(np.float64)
    valid = np.all(np.isfinite(s), axis=1) & np.all(np.isfinite(d), axis=1) & (np.min(d, axis=1) >= 0) & (np.max(d, axis=1) <= 1.25)
    s, d = s[valid], d[valid]
    if len(s) > max_samples:
        idx = np.random.default_rng(0).choice(len(s), max_samples, replace=False)
        s, d = s[idx], d[idx]
    x = np.c_[s, np.ones(len(s))]
    coef = np.linalg.lstsq(x, d, rcond=1e-7)[0]
    pred = x @ coef
    return coef[:3].T, coef[3], np.sqrt(np.mean((pred - d) ** 2, axis=0))


def xyz709(rgb: np.ndarray) -> np.ndarray:
    return rgb @ M_BT709_TO_XYZ.T


def xyz_to_lab(xyz: np.ndarray) -> np.ndarray:
    white = np.array([0.95047, 1.0, 1.08883])
    q = np.clip(xyz / white, 0, None)
    epsilon, kappa = 216 / 24389, 24389 / 27
    f = np.where(q > epsilon, np.cbrt(q), (kappa * q + 16) / 116)
    return np.stack([116 * f[..., 1] - 16, 500 * (f[..., 0] - f[..., 1]), 200 * (f[..., 1] - f[..., 2])], axis=-1)


def model_metrics(pred: np.ndarray, target: np.ndarray, seed: int = 1) -> dict:
    p = np.clip(pred, 0, 1)
    t = np.clip(target, 0, 1)
    flat_p, flat_t = p.reshape(-1, 3), t.reshape(-1, 3)
    count = min(250_000, len(flat_p))
    idx = np.random.default_rng(seed).choice(len(flat_p), count, replace=False)
    delta_e = np.linalg.norm(xyz_to_lab(xyz709(flat_p[idx])) - xyz_to_lab(xyz709(flat_t[idx])), axis=1)
    ssim = []
    for pf, tf in zip(p, t):
        pg = (pf ** (1 / 2.2) * 255).astype(np.uint8)
        tg = (tf ** (1 / 2.2) * 255).astype(np.uint8)
        ssim.append(structural_similarity(
            cv2.cvtColor(pg, cv2.COLOR_RGB2GRAY),
            cv2.cvtColor(tg, cv2.COLOR_RGB2GRAY), data_range=255,
        ))
    return {
        "ssim_mean": float(np.mean(ssim)),
        "deltaE76_median": float(np.median(delta_e)),
        "deltaE76_mean": float(np.mean(delta_e)),
        "deltaE76_p90": float(np.percentile(delta_e, 90)),
    }


def save_diagnostic_frame(out: Path, model: np.ndarray, target: np.ndarray) -> None:
    i = len(model) // 2
    m = (np.clip(model[i], 0, 1) ** (1 / 2.2) * 255).astype(np.uint8)
    t = (np.clip(target[i], 0, 1) ** (1 / 2.2) * 255).astype(np.uint8)
    cv2.imwrite(str(out / "model_preview.png"), cv2.cvtColor(m, cv2.COLOR_RGB2BGR))
    cv2.imwrite(str(out / "reference_preview.png"), cv2.cvtColor(t, cv2.COLOR_RGB2BGR))
    error = np.mean(np.abs(m.astype(np.float32) - t.astype(np.float32)), axis=-1)
    error = np.clip(error / max(float(np.percentile(error, 99)), 1e-6), 0, 1)
    cv2.imwrite(str(out / "error_heatmap.png"), (error * 255).astype(np.uint8))


def main() -> None:
    parser = argparse.ArgumentParser(description="Fit a clean HLG/BT.2020 -> SDR/BT.709 calibration model")
    parser.add_argument("original", type=Path, help="HLG/BT.2020 source video")
    parser.add_argument("reference", type=Path, help="aligned SDR/BT.709 reference video")
    parser.add_argument("--out", type=Path, default=Path("calibration_out"))
    parser.add_argument("--frames", type=int, default=12, help="number of frame pairs to sample")
    parser.add_argument("--width", type=int, default=336)
    parser.add_argument("--height", type=int, default=188)
    parser.add_argument("--max-offset", type=int, default=4)
    args = parser.parse_args()
    if args.width % 2 or args.height % 2:
        parser.error("--width and --height must be even")

    args.out.mkdir(parents=True, exist_ok=True)
    meta_o, meta_r = ffprobe(args.original), ffprobe(args.reference)
    fps = parse_fraction(meta_o["avg_frame_rate"])
    frame_count = int(round(float(meta_o["duration"]) * fps))

    small_o = decode_all_rgb8(args.original, 168, 94)
    small_r = decode_all_rgb8(args.reference, 168, 94)
    offset, alignment_scores = find_frame_offset(small_o, small_r, args.max_offset)

    indices = np.linspace(8, frame_count - 9, args.frames).round().astype(int)
    ref_indices = indices + offset
    valid = (ref_indices >= 0) & (ref_indices < frame_count)
    indices, ref_indices = indices[valid], ref_indices[valid]

    source = decode_selected_linear(args.original, indices, args.width, args.height, original_hlg=True)
    reference = decode_selected_linear(args.reference, ref_indices, args.width, args.height, original_hlg=False)

    lum_x, lum_y, lum_curve = fit_monotone_luminance_curve(luma_bt2020(source), luma_bt709(reference))
    source_toned = apply_luminance_curve(source, lum_curve)

    source_709 = source_toned @ M_BT2020_TO_BT709.T
    sat_x, sat_y, sat_curve = fit_saturation_by_luminance(source_709, reference)
    clean = np.clip(apply_hue_preserving_saturation(source_709, sat_curve, sat_x), 0, 1)
    clean_metrics = model_metrics(clean, reference)

    affine_matrix, affine_bias, affine_rmse = fit_affine_rgb(source_toned, reference)
    affine = np.clip(source_toned @ affine_matrix.T + affine_bias, 0, 1)
    affine_metrics = model_metrics(affine, reference, seed=2)

    report = {
        "metadata": {"original": meta_o, "reference": meta_r},
        "alignment": {"best_frame_offset": int(offset), "scores": {str(k): v for k, v in alignment_scores.items()}},
        "sample_frames": {"original": indices.tolist(), "reference": ref_indices.tolist()},
        "recommended_clean_model": {
            "luminance_curve": {"x_hlg_bt2020_linear": lum_x.tolist(), "y_sdr_bt709_linear": lum_y.tolist()},
            "bt2020_to_bt709_matrix": M_BT2020_TO_BT709.tolist(),
            "saturation_by_luminance": {"x_sdr_linear_luma": sat_x.tolist(), "factor": sat_y.tolist()},
            "metrics": clean_metrics,
        },
        "diagnostic_affine_model": {
            "matrix": affine_matrix.tolist(), "bias": affine_bias.tolist(),
            "channel_rmse_linear": affine_rmse.tolist(), "metrics": affine_metrics,
        },
        "provenance": "Empirical fit from user-owned input/reference footage. No vendor LUT, SDK, private API, or extracted implementation is used.",
    }
    (args.out / "calibration.json").write_text(json.dumps(report, indent=2, ensure_ascii=False), encoding="utf-8")
    np.savetxt(args.out / "luminance_curve.csv", np.c_[lum_x, lum_y], delimiter=",", header="HLG_BT2020_linear_Y,SDR_BT709_linear_Y", comments="")
    np.savetxt(args.out / "saturation_curve.csv", np.c_[sat_x, sat_y], delimiter=",", header="SDR_linear_Y,hue_preserving_saturation_factor", comments="")

    plt.figure(figsize=(8, 5))
    plt.plot(lum_x, lum_y, "o-", markersize=3)
    plt.xlabel("Original HLG / BT.2020 linear Y")
    plt.ylabel("Reference SDR / BT.709 linear Y")
    plt.title("Learned luminance mapping")
    plt.grid(alpha=0.25)
    plt.tight_layout()
    plt.savefig(args.out / "luminance_curve.png", dpi=150)
    plt.close()

    plt.figure(figsize=(8, 5))
    plt.plot(sat_x, sat_y, "o-")
    plt.axhline(1.0, linewidth=1)
    plt.xlabel("SDR linear luminance")
    plt.ylabel("Hue-preserving saturation factor")
    plt.title("Learned saturation vs luminance")
    plt.grid(alpha=0.25)
    plt.tight_layout()
    plt.savefig(args.out / "saturation_curve.png", dpi=150)
    plt.close()

    plt.figure(figsize=(7, 4))
    offsets = sorted(alignment_scores)
    plt.bar([str(v) for v in offsets], [alignment_scores[v] for v in offsets])
    plt.xlabel("Reference frame offset")
    plt.ylabel("Mean edge correlation")
    plt.title(f"Automatic alignment (best = {offset:+d} frames)")
    plt.tight_layout()
    plt.savefig(args.out / "alignment.png", dpi=150)
    plt.close()

    save_diagnostic_frame(args.out, clean, reference)

    grid = np.linspace(lum_x[0], lum_x[-1], 8)
    mapped = lum_curve(grid)
    lines = [
        "# ToneBake Calibration Report", "",
        f"- Frame offset: **{offset:+d}**",
        f"- Sampled frame pairs: **{len(indices)}**",
        f"- Clean model SSIM: **{clean_metrics['ssim_mean']:.4f}**",
        f"- Clean model median ΔE76: **{clean_metrics['deltaE76_median']:.2f}**",
        f"- Clean model P90 ΔE76: **{clean_metrics['deltaE76_p90']:.2f}**",
        f"- Affine diagnostic SSIM: **{affine_metrics['ssim_mean']:.4f}**",
        f"- Affine diagnostic P90 ΔE76: **{affine_metrics['deltaE76_p90']:.2f}**",
        "", "## Interpretation", "",
        "The preferred model is intentionally constrained: monotone tone curve + standard BT.2020→BT.709 primaries conversion + hue-preserving saturation-by-luminance. This is usually cleaner than a free RGB matrix because it avoids using cross-channel mixing to chase local errors.",
        "", "## Luminance mapping samples", "", "| HLG linear Y | Target SDR linear Y |", "|---:|---:|",
    ]
    lines += [f"| {x:.6f} | {y:.6f} |" for x, y in zip(grid, mapped)]
    lines += ["", "## Saturation mapping", "", "| SDR linear Y | Factor |", "|---:|---:|"]
    lines += [f"| {x:.3f} | {y:.3f} |" for x, y in zip(sat_x, sat_y)]
    lines += [
        "", "## Next step", "",
        "Translate the clean model into an Android GPU shader and compare it with MediaCodec tone mapping. Keep the current Vivid preset only as a legacy visual baseline.",
    ]
    (args.out / "REPORT.md").write_text("\n".join(lines), encoding="utf-8")

    print(json.dumps({
        "best_frame_offset": offset,
        "clean_metrics": clean_metrics,
        "affine_metrics": affine_metrics,
        "output": str(args.out),
    }, indent=2))


if __name__ == "__main__":
    main()
