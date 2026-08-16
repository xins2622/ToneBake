package io.github.tonebake.app

import android.content.Context
import android.net.Uri
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Brightness
import androidx.media3.effect.Contrast
import androidx.media3.effect.HslAdjustment
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import java.io.File

@UnstableApi
class VideoExporter(private val context: Context) {
    private var transformer: Transformer? = null
    private val progressHolder = ProgressHolder()

    fun export(
        input: Uri,
        preset: ExportPreset,
        onProgress: (Int) -> Unit,
        onDone: (File) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        val videoEffects = mutableListOf<Effect>()
        if (preset.calibratedLut) videoEffects += CalibratedLut.create()
        if (preset.brightness != 0f) videoEffects += Brightness(preset.brightness)
        if (preset.contrast != 0f) videoEffects += Contrast(preset.contrast)
        if (preset.saturation != 0f) {
            videoEffects += HslAdjustment.Builder().adjustSaturation(preset.saturation).build()
        }

        val editedItem = EditedMediaItem.Builder(MediaItem.fromUri(input))
            .setEffects(Effects(emptyList(), videoEffects))
            .build()
        val sequence = EditedMediaItemSequence.withAudioAndVideoFrom(listOf(editedItem))
        val composition = Composition.Builder(sequence).setHdrMode(preset.hdrMode).build()

        val outputFile = File(context.cacheDir, "ToneBake_${System.currentTimeMillis()}.mp4")
        if (outputFile.exists()) outputFile.delete()

        val encoderFactory = DefaultEncoderFactory.Builder(context)
            .setRequestedVideoEncoderSettings(
                VideoEncoderSettings.Builder().setBitrate(40_000_000).build()
            )
            .build()

        transformer = Transformer.Builder(context)
            .setVideoMimeType(MimeTypes.VIDEO_H265)
            .setEncoderFactory(encoderFactory)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    onProgress(100)
                    onDone(outputFile)
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException
                ) {
                    onError(exportException)
                }
            })
            .build()
            .also { it.start(composition, outputFile.absolutePath) }

        onProgress(0)
    }

    fun progress(): Int? {
        val current = transformer ?: return null
        return if (current.getProgress(progressHolder) == Transformer.PROGRESS_STATE_AVAILABLE) {
            progressHolder.progress
        } else null
    }

    fun cancel() {
        transformer?.cancel()
        transformer = null
    }
}
