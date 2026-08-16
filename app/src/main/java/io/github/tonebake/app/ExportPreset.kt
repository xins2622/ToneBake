package io.github.tonebake.app

data class ExportPreset(
    val title: String,
    val subtitle: String,
    val brightness: Float,
    val contrast: Float,
    val saturation: Float
) {
    companion object {
        val Standard = ExportPreset("Standard SDR", "标准、保守的 HDR → SDR", 0f, 0f, 0f)
        // Alpha calibration target: visually approach the previously selected B reference.
        val Bright = ExportPreset("Bright SDR", "更明亮的中间调 · B 基准", 0.06f, 0.04f, 4f)
        val Vivid = ExportPreset("Vivid SDR", "更明亮、更鲜艳", 0.08f, 0.08f, 10f)
        val all = listOf(Standard, Bright, Vivid)
    }
}
