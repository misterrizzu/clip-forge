package com.example.data

import android.content.Context
import android.graphics.*
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.util.Base64
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.effect.Crop
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.util.UUID
import kotlin.coroutines.resume

class VideoProcessor(private val context: Context) {

    private val outputDir: File by lazy {
        File(context.filesDir, "clips").apply { if (!exists()) mkdirs() }
    }

    private val thumbsDir: File by lazy {
        File(context.filesDir, "thumbnails").apply { if (!exists()) mkdirs() }
    }

    suspend fun prepareLocalVideoFile(sourceUri: Uri): File = withContext(Dispatchers.IO) {
        if (sourceUri.scheme == "file") {
            val path = sourceUri.path
            if (path != null && File(path).exists()) return@withContext File(path)
        }

        val destFile = File(outputDir, "source_input_${System.currentTimeMillis()}.mp4")
        context.contentResolver.openInputStream(sourceUri)?.use { input ->
            FileOutputStream(destFile).use { output ->
                input.copyTo(output)
            }
        }
        return@withContext destFile
    }

    suspend fun extractAudioBase64(videoFile: File): String? = withContext(Dispatchers.IO) {
        val audioFile = File(outputDir, "audio_track_${System.currentTimeMillis()}.m4a")
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null

        try {
            extractor.setDataSource(videoFile.absolutePath)
            var audioTrackIndex = -1
            var audioFormat: MediaFormat? = null

            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    audioFormat = format
                    break
                }
            }

            if (audioTrackIndex == -1 || audioFormat == null) {
                Log.w("VideoProcessor", "No audio track found in video file")
                return@withContext null
            }

            extractor.selectTrack(audioTrackIndex)
            muxer = MediaMuxer(audioFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val muxerTrackIndex = muxer.addTrack(audioFormat)
            muxer.start()

            val maxBufferSize = audioFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 64 * 1024)
            val buffer = ByteBuffer.allocate(maxBufferSize)
            val bufferInfo = android.media.MediaCodec.BufferInfo()

            while (true) {
                bufferInfo.size = extractor.readSampleData(buffer, 0)
                if (bufferInfo.size < 0) {
                    break
                }
                bufferInfo.presentationTimeUs = extractor.sampleTime
                bufferInfo.flags = extractor.sampleFlags
                muxer.writeSampleData(muxerTrackIndex, buffer, bufferInfo)
                extractor.advance()
            }

            muxer.stop()
            muxer.release()
            extractor.release()

            if (audioFile.exists() && audioFile.length() > 0) {
                if (audioFile.length() < 15 * 1024 * 1024) {
                    val bytes = FileInputStream(audioFile).use { it.readBytes() }
                    audioFile.delete()
                    return@withContext Base64.encodeToString(bytes, Base64.NO_WRAP)
                }
            }
        } catch (e: Exception) {
            Log.e("VideoProcessor", "Error extracting audio track", e)
        } finally {
            try { extractor.release() } catch (ignored: Throwable) {}
        }
        return@withContext null
    }

    private suspend fun detectSpeakerFaceCenterNormalized(
        videoFile: File,
        startMs: Long,
        endMs: Long
    ): Float = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        var averageCenterX = 0.5f
        try {
            retriever.setDataSource(videoFile.absolutePath)
            val durationMs = (endMs - startMs).coerceAtLeast(1000L)
            val sampledPoints = listOf(
                startMs + (durationMs * 0.2f).toLong(),
                startMs + (durationMs * 0.5f).toLong(),
                startMs + (durationMs * 0.8f).toLong()
            )

            val detectedPositions = mutableListOf<Float>()

            for (timeUs in sampledPoints.map { it * 1000L }) {
                val bitmap = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                if (bitmap != null) {
                    val faceX = detectFaceCenterXFromBitmap(bitmap)
                    if (faceX != null) {
                        detectedPositions.add(faceX)
                    }
                }
            }

            if (detectedPositions.isNotEmpty()) {
                averageCenterX = detectedPositions.average().toFloat()
                Log.d("VideoProcessor", "ML Kit detected face average X position: $averageCenterX")
            } else {
                Log.d("VideoProcessor", "No faces detected in sampled frames; using default center crop.")
            }
        } catch (e: Exception) {
            Log.e("VideoProcessor", "Error in ML Kit face detection", e)
        } finally {
            try { retriever.release() } catch (ignored: Throwable) {}
        }
        return@withContext averageCenterX
    }

    private suspend fun detectFaceCenterXFromBitmap(bitmap: Bitmap): Float? =
        suspendCancellableCoroutine { continuation ->
            try {
                val image = InputImage.fromBitmap(bitmap, 0)
                val options = FaceDetectorOptions.Builder()
                    .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                    .build()
                val detector = FaceDetection.getClient(options)

                detector.process(image)
                    .addOnSuccessListener { faces ->
                        if (faces.isNotEmpty()) {
                            val mainFace = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
                            if (mainFace != null) {
                                val centerX = mainFace.boundingBox.centerX().toFloat()
                                val normX = (centerX / bitmap.width.toFloat()).coerceIn(0.0f, 1.0f)
                                if (continuation.isActive) continuation.resume(normX)
                                return@addOnSuccessListener
                            }
                        }
                        if (continuation.isActive) continuation.resume(null)
                    }
                    .addOnFailureListener {
                        if (continuation.isActive) continuation.resume(null)
                    }
            } catch (e: Exception) {
                if (continuation.isActive) continuation.resume(null)
            }
        }

    suspend fun render1to1ClipWithHookOverlay(
        sourceVideoFile: File,
        clip: RawGeminiClip,
        manualCropOffset: Float = 0f,
        showHookBanner: Boolean = true,
        showSubtitlesBanner: Boolean = true,
        onProgress: (Float) -> Unit
    ): Pair<String, String?> = withContext(Dispatchers.IO) {
        val clipId = UUID.randomUUID().toString().take(8)
        val outputFile = File(outputDir, "clip_$clipId.mp4")
        val thumbFile = File(thumbsDir, "thumb_$clipId.jpg")

        val startMs = (clip.start_time * 1000).toLong()
        val endMs = (clip.end_time * 1000).toLong()

        // Combine ML Kit Face Center with Manual Offset adjustment (-0.5f to +0.5f)
        val detectedCenterXNorm = detectSpeakerFaceCenterNormalized(sourceVideoFile, startMs, endMs)
        val finalCenterXNorm = (detectedCenterXNorm + manualCropOffset).coerceIn(0.1f, 0.9f)

        val retriever = MediaMetadataRetriever()
        var videoWidth = 1920f
        var videoHeight = 1080f
        try {
            retriever.setDataSource(sourceVideoFile.absolutePath)
            videoWidth = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toFloatOrNull() ?: 1920f
            videoHeight = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toFloatOrNull() ?: 1080f
        } catch (ignored: Throwable) {
        } finally {
            try { retriever.release() } catch (ignored: Throwable) {}
        }

        val aspectScale = (videoHeight / videoWidth).coerceAtMost(1.0f)
        val cropSpanMedia3 = aspectScale * 2.0f
        val faceCenterMedia3 = (finalCenterXNorm - 0.5f) * 2.0f

        var left = faceCenterMedia3 - (cropSpanMedia3 / 2f)
        var right = faceCenterMedia3 + (cropSpanMedia3 / 2f)

        if (left < -1.0f) {
            left = -1.0f
            right = left + cropSpanSpanSafe(cropSpanMedia3)
        } else if (right > 1.0f) {
            right = 1.0f
            left = right - cropSpanSpanSafe(cropSpanMedia3)
        }

        val cropEffect = Crop(left, right, -1.0f, 1.0f)

        try {
            val mediaItem = MediaItem.Builder()
                .setUri(Uri.fromFile(sourceVideoFile))
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(startMs)
                        .setEndPositionMs(endMs)
                        .build()
                )
                .build()

            val editedMediaItem = EditedMediaItem.Builder(mediaItem)
                .setEffects(Effects(emptyList(), listOf(cropEffect)))
                .build()

            val sequence = EditedMediaItemSequence(editedMediaItem)
            val composition = Composition.Builder(listOf(sequence)).build()

            val renderSuccess = renderWithTransformer(composition, outputFile, onProgress)

            if (!renderSuccess || !outputFile.exists() || outputFile.length() == 0L) {
                Log.w("VideoProcessor", "Transformer export failed, fallback trimming video with MediaMuxer")
                val trimmed = trimVideoWithMediaMuxer(sourceVideoFile, outputFile, startMs, endMs)
                if (!trimmed || !outputFile.exists() || outputFile.length() == 0L) {
                    Log.e("VideoProcessor", "MediaMuxer trimming failed as well, copying source as last resort")
                    sourceVideoFile.copyTo(outputFile, overwrite = true)
                }
            }
        } catch (e: Exception) {
            Log.e("VideoProcessor", "Error during Transformer rendering, attempting MediaMuxer trimming", e)
            val trimmed = trimVideoWithMediaMuxer(sourceVideoFile, outputFile, startMs, endMs)
            if (!trimmed || !outputFile.exists() || outputFile.length() == 0L) {
                sourceVideoFile.copyTo(outputFile, overwrite = true)
            }
        }

        // Generate Thumbnail Image with Hook Text + Burned Spoken Subtitle Overlay
        val thumbPath = generateThumbnail(
            outputFile,
            clip.suggested_hook_text,
            clip.subtitles,
            thumbFile,
            showHookBanner = showHookBanner,
            showSubtitlesBanner = showSubtitlesBanner
        )

        return@withContext Pair(outputFile.absolutePath, thumbPath)
    }

    private fun cropSpanSpanSafe(span: Float): Float = span.coerceIn(0.1f, 2.0f)

    private suspend fun renderWithTransformer(
        composition: Composition,
        outputFile: File,
        onProgress: (Float) -> Unit
    ): Boolean = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { continuation ->
            val transformer = Transformer.Builder(context)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        onProgress(1.0f)
                        if (continuation.isActive) continuation.resume(true)
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exportException: ExportException
                    ) {
                        Log.e("VideoProcessor", "Transformer export error", exportException)
                        if (continuation.isActive) continuation.resume(false)
                    }
                })
                .build()

            try {
                transformer.start(composition, outputFile.absolutePath)
            } catch (e: Exception) {
                Log.e("VideoProcessor", "Start transformer exception", e)
                if (continuation.isActive) continuation.resume(false)
            }
        }
    }

    private suspend fun trimVideoWithMediaMuxer(
        sourceVideoFile: File,
        outputFile: File,
        startMs: Long,
        endMs: Long
    ): Boolean = withContext(Dispatchers.IO) {
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        try {
            extractor.setDataSource(sourceVideoFile.absolutePath)
            val trackCount = extractor.trackCount
            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            val trackIndexMap = HashMap<Int, Int>()
            val startUs = startMs * 1000L
            val endUs = endMs * 1000L

            var maxBufferSize = 1024 * 1024

            for (i in 0 until trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("video/") || mime.startsWith("audio/")) {
                    extractor.selectTrack(i)
                    val dstIndex = muxer.addTrack(format)
                    trackIndexMap[i] = dstIndex
                    if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                        val newSize = format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
                        if (newSize > maxBufferSize) {
                            maxBufferSize = newSize
                        }
                    }
                }
            }

            if (trackIndexMap.isEmpty()) {
                Log.e("VideoProcessor", "No video/audio tracks found to trim")
                return@withContext false
            }

            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
            muxer.start()

            val buffer = ByteBuffer.allocate(maxBufferSize)
            val bufferInfo = android.media.MediaCodec.BufferInfo()

            while (true) {
                val trackIndex = extractor.sampleTrackIndex
                if (trackIndex < 0) break

                val sampleTimeUs = extractor.sampleTime
                if (sampleTimeUs > endUs) break

                if (trackIndexMap.containsKey(trackIndex)) {
                    bufferInfo.size = extractor.readSampleData(buffer, 0)
                    if (bufferInfo.size < 0) break

                    val adjustedTimeUs = (sampleTimeUs - startUs).coerceAtLeast(0L)
                    bufferInfo.presentationTimeUs = adjustedTimeUs
                    bufferInfo.flags = extractor.sampleFlags

                    val dstTrack = trackIndexMap[trackIndex]!!
                    muxer.writeSampleData(dstTrack, buffer, bufferInfo)
                }
                extractor.advance()
            }

            muxer.stop()
            muxer.release()
            muxer = null
            extractor.release()
            Log.d("VideoProcessor", "Successfully trimmed video with MediaMuxer to ${outputFile.length()} bytes")
            true
        } catch (e: Exception) {
            Log.e("VideoProcessor", "MediaMuxer trimming failed", e)
            try { muxer?.release() } catch (ignored: Throwable) {}
            try { extractor.release() } catch (ignored: Throwable) {}
            false
        }
    }

    fun generateThumbnail(
        videoFile: File,
        hookText: String,
        subtitles: List<SubtitleItem>,
        thumbFile: File,
        aspectRatio: AspectRatio = AspectRatio.ASPECT_9_16,
        showHookBanner: Boolean = true,
        showSubtitlesBanner: Boolean = true
    ): String? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(videoFile.absolutePath)
            var bitmap = retriever.getFrameAtTime(1000000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.frameAtTime

            if (bitmap != null) {
                val targetW: Int
                val targetH: Int

                if (aspectRatio == AspectRatio.ASPECT_9_16) {
                    targetW = 1080
                    targetH = 1920
                } else {
                    targetW = 1080
                    targetH = 1080
                }

                val resultBitmap = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(resultBitmap)
                canvas.drawColor(Color.BLACK)

                // Draw video frame
                if (aspectRatio == AspectRatio.ASPECT_9_16) {
                    val frameHeight = targetW // Square video in center of 9:16 canvas
                    val frameY = (targetH - frameHeight) / 2f
                    val srcSquare = bitmap.width.coerceAtMost(bitmap.height)
                    val srcX = (bitmap.width - srcSquare) / 2
                    val srcY = (bitmap.height - srcSquare) / 2
                    val croppedSrc = Bitmap.createBitmap(bitmap, srcX, srcY, srcSquare, srcSquare)
                    val dstRect = RectF(0f, frameY, targetW.toFloat(), frameY + frameHeight)
                    canvas.drawBitmap(croppedSrc, null, dstRect, null)
                } else {
                    val srcSquare = bitmap.width.coerceAtMost(bitmap.height)
                    val srcX = (bitmap.width - srcSquare) / 2
                    val srcY = (bitmap.height - srcSquare) / 2
                    val croppedSrc = Bitmap.createBitmap(bitmap, srcX, srcY, srcSquare, srcSquare)
                    val dstRect = RectF(0f, 0f, targetW.toFloat(), targetH.toFloat())
                    canvas.drawBitmap(croppedSrc, null, dstRect, null)
                }

                // Top Hook Text Banner (Conditionally rendered)
                if (showHookBanner) {
                    val bannerPaint = Paint().apply {
                        color = Color.parseColor("#E6000000") // 90% black
                        style = Paint.Style.FILL
                    }
                    val bannerHeight = (targetH * 0.16f)
                    canvas.drawRect(0f, 0f, targetW.toFloat(), bannerHeight, bannerPaint)

                    val borderPaint = Paint().apply {
                        color = Color.parseColor("#FF6C00")
                        strokeWidth = 8f
                    }
                    canvas.drawLine(0f, bannerHeight, targetW.toFloat(), bannerHeight, borderPaint)

                    val textPaint = Paint().apply {
                        color = Color.WHITE
                        textSize = targetW * 0.048f
                        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                        textAlign = Paint.Align.CENTER
                        isAntiAlias = true
                    }

                    val text = if (hookText.isNotBlank()) hookText else "VIRAL MOMENT 🔥"
                    val textX = targetW / 2f
                    val textY = bannerHeight / 2f + (textPaint.textSize / 3f)

                    canvas.drawText(text, textX, textY, textPaint)
                }

                // Bottom Spoken Subtitle Burn-In Banner (Conditionally rendered)
                if (showSubtitlesBanner) {
                    val subText = subtitles.firstOrNull()?.text ?: "Watch until the end! 🔥"
                    if (subText.isNotBlank()) {
                        val subBgPaint = Paint().apply {
                            color = Color.parseColor("#CC000000")
                            style = Paint.Style.FILL
                        }
                        val subBannerY = targetH * 0.80f
                        val subBannerHeight = targetH * 0.15f
                        canvas.drawRect(0f, subBannerY, targetW.toFloat(), subBannerY + subBannerHeight, subBgPaint)

                        val subPaint = Paint().apply {
                            color = Color.YELLOW
                            textSize = targetW * 0.045f
                            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                            textAlign = Paint.Align.CENTER
                            isAntiAlias = true
                        }
                        val subY = subBannerY + (subBannerHeight / 2f) + (subPaint.textSize / 3f)
                        val textX = targetW / 2f
                        canvas.drawText("💬 \"$subText\"", textX, subY, subPaint)
                    }
                }

                FileOutputStream(thumbFile).use { out ->
                    resultBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                }
                return thumbFile.absolutePath
            }
            null
        } catch (e: Exception) {
            Log.e("VideoProcessor", "Failed to generate thumbnail", e)
            null
        } finally {
            try { retriever.release() } catch (ignored: Throwable) {}
        }
    }
}
