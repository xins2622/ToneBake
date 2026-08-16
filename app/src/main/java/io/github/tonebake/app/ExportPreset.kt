package io.github.tonebake.app

import androidx.media3.transformer.Composition

data class ExportPreset(
    val title: String,
    val subtitle: String,
    val hdrMode: Int,
    val calibratedLut: Boolean = false,
    val residualLutV2: Boolean = false,
    val brightness: Float = 0f,
    val contrast: Float = 0f,
    val saturation: Float = 0f
) {
    companion object {
        val CalibratedV2 = ExportPreset(
            title = "ToneBake Calibrated v2",
            subtitle = "第二轮残差拟合 · 更高色度与局部对比 · 推荐",
            hdrMode = Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL,
            calibratedLut = true,
            residualLutV2 = true
        )
        val CalibratedV1 = ExportPreset(
            title = "Calibrated v1",
            subtitle = "上一版自动拟合结果 · 作为 A/B 对照",
            hdrMode = Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL,
            calibratedLut = true
        )
        val LegacyVivid = ExportPreset(
            title = "Legacy Vivid",
            subtitle = "旧版 Vivid Reference · 作为传统调参对照",
            hdrMode = Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL,
            brightness = 0.24f,
            contrast = 0.07f,
            saturation = 12f
        )
        val System = ExportPreset(
            title = "System Tone Map",
            subtitle = "Android MediaCodec HDR→SDR · 高级诊断（本机偏暗）",
            hdrMode = Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_MEDIACODEC
        )
        val all = listOf(CalibratedV2, CalibratedV1, LegacyVivid, System)
    }
}
