# ToneBake

ToneBake is an experimental Android utility for converting HDR video—especially HLG / BT.2020 footage—into broadly compatible SDR video.

The project grew out of a practical problem: an HLG export can look correct inside an editing app but appear too dark in a phone gallery or in an SDR-oriented sharing workflow. ToneBake makes a real HDR → SDR tone-mapped copy instead of merely changing HDR metadata.

## v0.1 alpha

- Android 10+ (API 29+)
- Local video picker
- AndroidX Media3 Transformer 1.10.1
- OpenGL HDR → SDR tone mapping
- Three presets:
  - **Standard SDR** — conservative baseline
  - **Bright SDR** — brighter midtones; current preferred compatibility preset
  - **Vivid SDR** — brighter and more saturated
- H.265 / HEVC output
- Keeps source geometry and timing unless the device encoder requires fallback
- Saves output to `Movies/ToneBake`

`Bright SDR` is an original compatibility preset being calibrated against a user-approved SDR reference generated from an HLG test clip. It does **not** copy, extract, or redistribute any camera-vendor LUT, SDK, or proprietary algorithm.

## Build

Open the project in a recent Android Studio version with Android SDK 36 installed, or use the included GitHub Actions workflow. Pushes and pull requests run `assembleDebug` and upload the debug APK as the `ToneBake-debug` workflow artifact.

The current build uses:

- Android Gradle Plugin 8.9.1
- Gradle 8.11.1 in CI
- Kotlin 2.1.20
- Jetpack Compose BOM 2026.06.00
- AndroidX Media3 1.10.1

CI smoke-test branch is used only to verify that GitHub Actions can assemble the first debug APK.

## Roadmap

1. Verify v0.1 on Xiaomi / Android 16 and calibrate Bright SDR against the reference clip.
2. Add real-time export progress and cancel controls.
3. Add HDR metadata inspection: HLG/PQ, BT.2020/BT.709, bit depth, FPS, resolution.
4. Add export codec, resolution, FPS and quality controls.
5. Add custom brightness / contrast / saturation controls and preview.
6. Add batch conversion and optional MediaCodec/OpenGL tone-mapping engine selection.

## Legal / trademark note

ToneBake is a general-purpose video utility and is not affiliated with or endorsed by DJI or any camera manufacturer. Product and app names may be mentioned only to describe interoperability or testing. No DJI SDK, private API, LUT, logo, or proprietary code is included.

## License

MIT — see [LICENSE](LICENSE).
