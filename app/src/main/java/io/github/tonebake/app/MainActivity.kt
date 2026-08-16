package io.github.tonebake.app

import android.content.ContentValues
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import java.io.File

@UnstableApi
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { ToneBakeScreen() } }
    }

    @Composable
    private fun ToneBakeScreen() {
        var input by remember { mutableStateOf<android.net.Uri?>(null) }
        var preset by remember { mutableStateOf(ExportPreset.Bright) }
        var status by remember { mutableStateOf("选择一个 HLG/HDR 视频开始") }
        var busy by remember { mutableStateOf(false) }
        val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            input = uri
            if (uri != null) status = "视频已选择 · 默认推荐 Bright SDR"
        }
        val exporter = remember { VideoExporter(this) }

        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text("ToneBake", style = MaterialTheme.typography.headlineLarge)
            Text("把 HLG / HDR 视频烘焙成兼容性更好的 SDR。", style = MaterialTheme.typography.bodyLarge)
            Button(onClick = { picker.launch("video/*") }, enabled = !busy) { Text(if (input == null) "选择视频" else "重新选择") }
            HorizontalDivider()
            Text("导出方案", style = MaterialTheme.typography.titleMedium)
            ExportPreset.all.forEach { item ->
                Row(Modifier.fillMaxWidth().selectable(
                    selected = preset == item,
                    enabled = !busy,
                    onClick = { preset = item }
                ).padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = preset == item, onClick = { preset = item }, enabled = !busy)
                    Column(Modifier.padding(start = 8.dp)) { Text(item.title); Text(item.subtitle, style = MaterialTheme.typography.bodySmall) }
                }
            }
            Button(onClick = {
                val uri = input ?: return@Button
                busy = true; status = "正在进行 HDR → SDR…"
                exporter.export(uri, preset, {}, { file -> saveToGallery(file); busy = false; status = "完成 · 已保存到 Movies/ToneBake" }, { e -> busy = false; status = "导出失败：${e.message}" })
            }, enabled = input != null && !busy, modifier = Modifier.fillMaxWidth()) { Text(if (busy) "转换中…" else "导出 SDR") }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(status, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.weight(1f))
            Text("v0.1 alpha · Bright SDR 将以已确认的 B 参考视频继续真机校准", style = MaterialTheme.typography.labelSmall)
        }
    }

    private fun saveToGallery(file: File) {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, file.name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/ToneBake")
        }
        contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)?.let { uri ->
            contentResolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
        }
    }
}
