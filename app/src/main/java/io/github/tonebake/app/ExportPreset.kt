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
        val VividSoft = ExportPreset("Vivid Soft", "略柔和于当前 Vivid", 0.21f, 0.05f, 10f)
        val VividReference = ExportPreset("Vivid Reference", "当前最接近 B 的基准", 0.24f, 0.07f, 12f)
        val VividPlus = ExportPreset("Vivid Plus", "在基准上略增亮度、对比度和饱和度", 0.27f, 0.09f, 14f)
        val all = listOf(Standard, VividSoft, VividReference, VividPlus)
    }
}
