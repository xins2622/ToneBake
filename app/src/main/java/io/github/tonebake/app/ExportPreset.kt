package io.github.tonebake.app

import androidx.media3.transformer.Composition

data class ExportPreset(
    val title: String,
    val subtitle: String,
    val hdrMode: Int,
    val calibratedLut: Boolean = false,
    val brightness: Float = 0f,
    val contrast: Float = 0f,
    val saturation: Float = 0f
) {
    companion object {
        val Calibrated = ExportPreset(
            title = "ToneBake Calibrated",
            subtitle = "基于 A→B 自动拟合的亮度/色度曲线 · 推荐",
            hdrMode = Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL,
            calibratedLut = true
        )
        val System = ExportPreset(
            title = "System Tone Map",
            subtitle = "Android MediaCodec HDR→SDR · 真机质量对照",
            hdrMode = Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_MEDIACODEC
        )
        val LegacyVivid = ExportPreset(
            title = "Legacy Vivid",
            subtitle = "旧版 Vivid Reference · 作为 A/B 对照",
            hdrMode = Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL,
            brightness = 0.24f,
            contrast = 0.07f,
            saturation = 12f
        )
        val Standard = ExportPreset(
            title = "Standard SDR",
            subtitle = "Media3 OpenGL HDR→SDR 基准",
            hdrMode = Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL
        )
        val all = listOf(Calibrated, System, LegacyVivid, Standard)
    }
}
