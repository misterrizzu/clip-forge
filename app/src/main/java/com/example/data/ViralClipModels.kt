package com.example.data

import android.net.Uri

sealed class VideoInputType {
    data class LocalVideo(val uri: Uri, val fileName: String) : VideoInputType()
    data class GoogleDriveUrl(val url: String) : VideoInputType()
}

enum class Platform(val label: String, val icon: String) {
    YOUTUBE("YouTube", "▶"),
    INSTAGRAM("Instagram", "📷"),
    TIKTOK("TikTok", "🎵"),
    X("X (Twitter)", "𝕏")
}

enum class SubtitleFontSize(val label: String) {
    SMALL("Small"),
    MEDIUM("Medium"),
    LARGE("Large")
}

enum class SubtitleTextColor(val label: String, val colorHex: String) {
    WHITE("White", "#FFFFFF"),
    YELLOW("Yellow", "#FFEB3B")
}

enum class SubtitleBgOpacity(val label: String, val alphaInt: Int) {
    NONE("None", 0),
    LIGHT("Light", 100),
    DARK("Dark", 190)
}

enum class SubtitlePosition(val label: String) {
    BOTTOM("Bottom (20%)"),
    TOP("Top (20%)")
}

data class SubtitleSettings(
    val fontSize: SubtitleFontSize = SubtitleFontSize.MEDIUM,
    val textColor: SubtitleTextColor = SubtitleTextColor.YELLOW,
    val bgOpacity: SubtitleBgOpacity = SubtitleBgOpacity.DARK,
    val position: SubtitlePosition = SubtitlePosition.BOTTOM
)

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
    val viral_score: Int = 75,
    val hook_score: Int = 75,
    val emotion_score: Int = 70,
    val curiosity_score: Int = 70,
    val completion_score: Int = 75,
    val value_score: Int = 70,
    val confidence_score: Float = 0.75f,
    val hook_text: String = "",
    val why_viral: String = "",
    val caption_line: String = "",
    val title: String = "",
    val suggested_hook_text: String = hook_text,
    val reason: String = why_viral,
    val suggested_title: String = title,
    val suggested_description: String = caption_line,
    val suggested_tags: List<String> = emptyList(),       // combined legacy field
    val handles: List<String> = emptyList(),               // @handles only (real, from rules)
    val hashtags: List<String> = emptyList(),              // #Hashtags only (content-relevant)
    val seo_keywords: List<String> = emptyList(),          // plain SEO words for YouTube
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
    val viralScore: Int = 75,
    val suggestedHookText: String,
    val reason: String,
    var title: String,
    var description: String,
    var tags: List<String>,
    var handles: List<String> = emptyList(),           // @handles (real, from rules)
    var hashtags: List<String> = emptyList(),           // #Hashtags (content-relevant)
    var seoKeywords: List<String> = emptyList(),        // plain SEO keywords for YouTube
    var processedVideoPath: String? = null,
    var thumbnailPath: String? = null,
    var manualCropOffset: Float = 0f,
    var subtitles: List<SubtitleItem> = emptyList(),
    var isCompliant: Boolean = true,
    var complianceDetails: String = "Compliant with campaign rules",
    var showTitle: Boolean = true,
    var showDescription: Boolean = true,
    var showTags: Boolean = true,
    var isExported: Boolean = false,
    var isUsed: Boolean = false,                        // Marks clip as already posted/used
    var selectedPlatforms: Set<Platform> = setOf(Platform.INSTAGRAM, Platform.TIKTOK),
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
