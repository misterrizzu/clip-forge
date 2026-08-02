package com.example.data

import android.net.Uri

sealed class VideoInputType {
    data class LocalVideo(val uri: Uri, val fileName: String) : VideoInputType()
    data class GoogleDriveUrl(val url: String) : VideoInputType()
}

data class SubtitleItem(
    val startSec: Float,
    val endSec: Float,
    val text: String
)

data class RawGeminiClipResponse(
    val clips: List<RawGeminiClip> = emptyList()
)

data class RawGeminiClip(
    val start_time: Float,
    val end_time: Float,
    val confidence_score: Float = 0.9f,
    val suggested_hook_text: String = "",
    val reason: String = "",
    val suggested_title: String = "",
    val suggested_description: String = "",
    val suggested_tags: List<String> = emptyList(),
    val subtitles: List<SubtitleItem> = emptyList()
)

data class ProjectFolder(
    val id: String,
    val name: String,
    val videoFileName: String,
    val clipCount: Int,
    val thumbnailPath: String?,
    val createdAtMs: Long
)

enum class AspectRatio(val label: String, val ratioWidth: Int, val ratioHeight: Int) {
    ASPECT_9_16("9:16 Vertical (Shorts/Reels)", 9, 16),
    ASPECT_1_1("1:1 Square", 1, 1)
}

data class ViralClip(
    val id: String,
    val projectId: String = "default_project",
    val startTimeSeconds: Float,
    val endTimeSeconds: Float,
    val confidenceScore: Float,
    val suggestedHookText: String,
    val reason: String,
    var title: String,
    var description: String,
    var tags: List<String>,
    var processedVideoPath: String? = null,
    var thumbnailPath: String? = null,
    var manualCropOffset: Float = 0f, // -0.5 to +0.5 manual shift
    var subtitles: List<SubtitleItem> = emptyList(),
    var isCompliant: Boolean = true,
    var complianceDetails: String = "Compliant with campaign rules",
    var showTitle: Boolean = true,
    var showDescription: Boolean = true,
    var showTags: Boolean = true,
    var isExported: Boolean = false,
    var aspectRatio: AspectRatio = AspectRatio.ASPECT_9_16,
    var showHookBanner: Boolean = true,
    var showSubtitlesBanner: Boolean = true
) {
    val durationSeconds: Int
        get() = (endTimeSeconds - startTimeSeconds).toInt().coerceAtLeast(1)

    fun formattedTimeRange(): String {
        fun formatSec(s: Float): String {
            val totalSec = s.toInt()
            val m = totalSec / 60
            val sec = totalSec % 60
            return String.format("%02d:%02d", m, sec)
        }
        return "${formatSec(startTimeSeconds)} - ${formatSec(endTimeSeconds)}"
    }
}

data class BatchQueueTask(
    val id: String,
    val isGDrive: Boolean,
    val localUri: Uri? = null,
    val gdriveUrl: String = "",
    val fileName: String,
    val rulesContent: String = "",
    val customInstructions: String = "",
    var status: QueueStatus = QueueStatus.PENDING,
    var errorMessage: String? = null
)

enum class QueueStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED
}

sealed class ProcessingPipelineStatus {
    object Idle : ProcessingPipelineStatus()
    data class Processing(val stepText: String, val progressFraction: Float) : ProcessingPipelineStatus()
    data class ReviewCandidateClips(val candidates: List<RawGeminiClip>, val videoFile: java.io.File) : ProcessingPipelineStatus()
    data class Success(val clips: List<ViralClip>) : ProcessingPipelineStatus()
    data class Error(val message: String) : ProcessingPipelineStatus()
}
