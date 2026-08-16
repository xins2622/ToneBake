package io.github.tonebake.app

data class ExportPreset(
    val title: String,
    val subtitle: String,
    val brightness: Float,
    val contrast: Float,
    val saturation: Float
) {
    companion object {
        val Standard = ExportPreset("Standard SDR", "OpenGL HDR → SDR 基准", 0f, 0f, 0f)
        val Bright = ExportPreset("Bright SDR", "明显抬高中间调 · 校准档 1", 0.16f, 0.03f, 4f)
        val Brighter = ExportPreset("Brighter SDR", "更强的中间调抬升 · 校准档 2", 0.28f, 0.02f, 5f)
        val Vivid = ExportPreset("Vivid SDR", "高亮 + 更鲜艳 · 校准档 3", 0.24f, 0.07f, 12f)
        val all = listOf(Standard, Bright, Brighter, Vivid)
    }
}
