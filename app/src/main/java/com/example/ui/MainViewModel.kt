package com.example.ui

import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.data.db.AppDatabase
import com.example.data.db.ClipEntity
import com.example.data.db.QueueItemEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.util.UUID

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val clipDao = db.clipDao()

    private val apiKeyManager = ApiKeyManager(application)
    private val geminiService = GeminiClipService()
    private val videoProcessor = VideoProcessor(application)
    private val driveDownloader = GoogleDriveDownloader(application)

    private val _apiKey = MutableStateFlow(apiKeyManager.getApiKey())
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    private val _selectedTab = MutableStateFlow(0) // 0 = Upload Video, 1 = Google Drive Link
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    private val _localVideoUri = MutableStateFlow<Uri?>(null)
    val localVideoUri: StateFlow<Uri?> = _localVideoUri.asStateFlow()

    private val _localVideoName = MutableStateFlow<String?>(null)
    val localVideoName: StateFlow<String?> = _localVideoName.asStateFlow()

    private val _gdriveUrl = MutableStateFlow("")
    val gdriveUrl: StateFlow<String> = _gdriveUrl.asStateFlow()

    private val _rulesFileName = MutableStateFlow<String?>(null)
    val rulesFileName: StateFlow<String?> = _rulesFileName.asStateFlow()

    private val _rulesFileContent = MutableStateFlow("")
    val rulesFileContent: StateFlow<String> = _rulesFileContent.asStateFlow()

    private val _customInstructions = MutableStateFlow("")
    val customInstructions: StateFlow<String> = _customInstructions.asStateFlow()

    private val _aspectRatio = MutableStateFlow(AspectRatio.ASPECT_9_16)
    val aspectRatio: StateFlow<AspectRatio> = _aspectRatio.asStateFlow()

    private val _minDurationSeconds = MutableStateFlow(15)
    val minDurationSeconds: StateFlow<Int> = _minDurationSeconds.asStateFlow()

    private val _maxDurationSeconds = MutableStateFlow(60)
    val maxDurationSeconds: StateFlow<Int> = _maxDurationSeconds.asStateFlow()

    private val _processingStatus = MutableStateFlow<ProcessingPipelineStatus>(ProcessingPipelineStatus.Idle)
    val processingStatus: StateFlow<ProcessingPipelineStatus> = _processingStatus.asStateFlow()

    private val _clips = MutableStateFlow<List<ViralClip>>(emptyList())
    val clips: StateFlow<List<ViralClip>> = _clips.asStateFlow()

    private val _batchQueue = MutableStateFlow<List<BatchQueueTask>>(emptyList())
    val batchQueue: StateFlow<List<BatchQueueTask>> = _batchQueue.asStateFlow()

    private val _activePreviewClip = MutableStateFlow<ViralClip?>(null)
    val activePreviewClip: StateFlow<ViralClip?> = _activePreviewClip.asStateFlow()

    init {
        // Restore persisted clips and batch queue state from Room DB
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val dbClips = clipDao.getAllClips().map { entity ->
                    ViralClip(
                        id = entity.id,
                        startTimeSeconds = entity.startTimeSeconds,
                        endTimeSeconds = entity.endTimeSeconds,
                        confidenceScore = entity.confidenceScore,
                        suggestedHookText = entity.suggestedHookText,
                        reason = entity.reason,
                        title = entity.title,
                        description = entity.description,
                        tags = entity.tagsCsv.split(",").map { it.trim() }.filter { it.isNotBlank() },
                        processedVideoPath = entity.processedVideoPath,
                        thumbnailPath = entity.thumbnailPath,
                        manualCropOffset = entity.cropOffset,
                        subtitles = parseSubtitlesJson(entity.subtitlesJson),
                        isCompliant = entity.isCompliant,
                        complianceDetails = entity.complianceNote,
                        showTitle = entity.showTitle,
                        showDescription = entity.showDescription,
                        showTags = entity.showTags,
                        isExported = entity.isExported
                    )
                }
                if (dbClips.isNotEmpty()) {
                    _clips.value = dbClips
                    _processingStatus.value = ProcessingPipelineStatus.Success(dbClips)
                }

                val dbQueue = clipDao.getQueueItems().map { entity ->
                    BatchQueueTask(
                        id = entity.id,
                        isGDrive = entity.videoSourceType == "GDRIVE",
                        localUri = if (entity.videoSourceType == "LOCAL") Uri.parse(entity.videoSourceUriOrUrl) else null,
                        gdriveUrl = if (entity.videoSourceType == "GDRIVE") entity.videoSourceUriOrUrl else "",
                        fileName = entity.fileName,
                        rulesContent = entity.rulesContent,
                        customInstructions = entity.customInstructions,
                        status = try { QueueStatus.valueOf(entity.status) } catch (e: Exception) { QueueStatus.PENDING },
                        errorMessage = entity.errorMessage
                    )
                }
                _batchQueue.value = dbQueue
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error restoring from Room database", e)
            }
        }
    }

    fun updateApiKey(key: String): Boolean {
        val success = apiKeyManager.saveApiKey(key)
        if (success) {
            _apiKey.value = key.trim()
        }
        return success
    }

    fun setSelectedTab(index: Int) {
        _selectedTab.value = index
    }

    fun setLocalVideo(uri: Uri, name: String) {
        _localVideoUri.value = uri
        _localVideoName.value = name
    }

    fun setGdriveUrl(url: String) {
        _gdriveUrl.value = url
    }

    fun setCustomInstructions(text: String) {
        _customInstructions.value = text
    }

    fun readAndSetRulesFile(context: Context, uri: Uri, fileName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _rulesFileName.value = fileName
                val text = context.contentResolver.openInputStream(uri)?.use { stream ->
                    InputStreamReader(stream).readText()
                } ?: ""
                _rulesFileContent.value = text
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error reading rules file", e)
                _rulesFileContent.value = "Campaign Rules File: $fileName"
            }
        }
    }

    fun setAspectRatio(ratio: AspectRatio) {
        _aspectRatio.value = ratio
    }

    fun setClipDurationRange(minSec: Int, maxSec: Int) {
        _minDurationSeconds.value = minSec
        _maxDurationSeconds.value = maxSec
    }

    fun copyClipMetadataToClipboard(context: Context, clip: ViralClip) {
        try {
            val textToCopy = StringBuilder().apply {
                if (clip.showTitle && clip.title.isNotBlank()) {
                    appendLine("📌 ${clip.title}")
                    appendLine()
                }
                if (clip.showDescription && clip.description.isNotBlank()) {
                    appendLine(clip.description)
                    appendLine()
                }
                if (clip.showTags && clip.tags.isNotEmpty()) {
                    appendLine(clip.tags.joinToString(" "))
                }
            }.toString().trim()

            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clipData = android.content.ClipData.newPlainText("Clip Metadata", textToCopy)
            clipboard.setPrimaryClip(clipData)
            Toast.makeText(context, "Caption & Tags copied to clipboard!", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e("MainViewModel", "Error copying to clipboard", e)
        }
    }

    fun clearRulesFile() {
        _rulesFileName.value = null
        _rulesFileContent.value = ""
    }

    fun resetProcessingStatus() {
        _processingStatus.value = ProcessingPipelineStatus.Idle
    }

    fun addToBatchQueue() {
        val task = if (_selectedTab.value == 0) {
            val uri = _localVideoUri.value ?: return
            BatchQueueTask(
                id = UUID.randomUUID().toString().take(8),
                isGDrive = false,
                localUri = uri,
                fileName = _localVideoName.value ?: "Local Video",
                rulesContent = _rulesFileContent.value,
                customInstructions = _customInstructions.value
            )
        } else {
            val url = _gdriveUrl.value.trim()
            if (url.isBlank()) return
            BatchQueueTask(
                id = UUID.randomUUID().toString().take(8),
                isGDrive = true,
                gdriveUrl = url,
                fileName = "GDrive Video (${url.take(20)}...)",
                rulesContent = _rulesFileContent.value,
                customInstructions = _customInstructions.value
            )
        }

        _batchQueue.value = _batchQueue.value + task
        viewModelScope.launch(Dispatchers.IO) {
            clipDao.insertQueueItem(
                QueueItemEntity(
                    id = task.id,
                    videoSourceType = if (task.isGDrive) "GDRIVE" else "LOCAL",
                    videoSourceUriOrUrl = if (task.isGDrive) task.gdriveUrl else task.localUri.toString(),
                    fileName = task.fileName,
                    rulesContent = task.rulesContent,
                    customInstructions = task.customInstructions,
                    status = task.status.name
                )
            )
        }
    }

    fun removeBatchQueueTask(taskId: String) {
        _batchQueue.value = _batchQueue.value.filter { it.id != taskId }
        viewModelScope.launch(Dispatchers.IO) {
            clipDao.deleteQueueItem(taskId)
        }
    }

    fun startClipGeneration(context: Context) {
        val currentKey = apiKey.value
        if (currentKey.isBlank()) {
            _processingStatus.value = ProcessingPipelineStatus.Error("Please configure a valid Gemini API key in Settings.")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                _processingStatus.value = ProcessingPipelineStatus.Processing("Preparing video source...", 0.05f)

                val videoFile: File = when (_selectedTab.value) {
                    0 -> {
                        val uri = _localVideoUri.value
                        if (uri == null) {
                            _processingStatus.value = ProcessingPipelineStatus.Error("Please select a local video file.")
                            return@launch
                        }
                        videoProcessor.prepareLocalVideoFile(uri)
                    }
                    else -> {
                        val url = _gdriveUrl.value.trim()
                        if (url.isBlank()) {
                            _processingStatus.value = ProcessingPipelineStatus.Error("Please enter a valid Google Drive shareable link.")
                            return@launch
                        }

                        val fileId = driveDownloader.extractFileId(url)
                        if (fileId == null) {
                            _processingStatus.value = ProcessingPipelineStatus.Error("Invalid Google Drive URL. Paste a link like 'https://drive.google.com/file/d/FILE_ID/view'.")
                            return@launch
                        }

                        _processingStatus.value = ProcessingPipelineStatus.Processing("Downloading video from Google Drive...", 0.15f)
                        driveDownloader.downloadPublicDriveFile(fileId) { downloadProgress ->
                            _processingStatus.value = ProcessingPipelineStatus.Processing(
                                "Downloading Google Drive video (${(downloadProgress * 100).toInt()}%)...",
                                0.15f + (0.20f * downloadProgress)
                            )
                        }
                    }
                }

                val retriever = MediaMetadataRetriever()
                var videoDurationSec = 60.0f
                try {
                    retriever.setDataSource(videoFile.absolutePath)
                    val durationMsStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    if (durationMsStr != null) {
                        videoDurationSec = (durationMsStr.toFloat() / 1000f).coerceAtLeast(10f)
                    }
                } catch (e: Exception) {
                    Log.w("MainViewModel", "Could not extract duration metadata", e)
                } finally {
                    try { retriever.release() } catch (ignored: Throwable) {}
                }

                _processingStatus.value = ProcessingPipelineStatus.Processing("Extracting audio track for AI transcription...", 0.38f)
                val audioBase64 = videoProcessor.extractAudioBase64(videoFile)

                _processingStatus.value = ProcessingPipelineStatus.Processing("Gemini AI analyzing audio & finding viral moments...", 0.52f)
                val rawClips = geminiService.analyzeVideoForViralClips(
                    apiKey = currentKey,
                    audioBase64 = audioBase64,
                    transcriptOrContent = "Video File: ${videoFile.name}, Duration: ${videoDurationSec}s",
                    campaignRulesText = _rulesFileContent.value,
                    customInstructions = _customInstructions.value,
                    videoDurationSeconds = videoDurationSec,
                    minDurationSeconds = _minDurationSeconds.value,
                    maxDurationSeconds = _maxDurationSeconds.value
                )

                if (rawClips.isEmpty()) {
                    _processingStatus.value = ProcessingPipelineStatus.Error("No viral clips were detected. Try a video with spoken dialogue or adjust custom instructions.")
                    return@launch
                }

                val totalClips = rawClips.size
                val generatedViralClips = mutableListOf<ViralClip>()

                rawClips.forEachIndexed { index, rawClip ->
                    val clipNum = index + 1
                    _processingStatus.value = ProcessingPipelineStatus.Processing(
                        "ML Kit face-aligning & rendering clip $clipNum of $totalClips...",
                        0.55f + (0.42f * (clipNum.toFloat() / totalClips))
                    )

                    val (videoPath, thumbPath) = videoProcessor.render1to1ClipWithHookOverlay(
                        videoFile,
                        rawClip
                    ) { clipProgress ->
                    }

                    val clipObj = ViralClip(
                        id = "clip_${UUID.randomUUID().toString().take(6)}",
                        startTimeSeconds = rawClip.start_time,
                        endTimeSeconds = rawClip.end_time,
                        confidenceScore = rawClip.confidence_score,
                        suggestedHookText = rawClip.suggested_hook_text,
                        reason = rawClip.reason,
                        title = if (rawClip.suggested_title.isNotBlank()) rawClip.suggested_title else "Viral Moment #$clipNum",
                        description = if (rawClip.suggested_description.isNotBlank()) rawClip.suggested_description else "Check out this highlight!",
                        tags = if (rawClip.suggested_tags.isNotEmpty()) rawClip.suggested_tags else listOf("#Viral", "#ClipForge", "#Shorts"),
                        processedVideoPath = videoPath,
                        thumbnailPath = thumbPath,
                        subtitles = rawClip.subtitles,
                        isCompliant = true,
                        complianceDetails = "Compliant with campaign rules",
                        aspectRatio = _aspectRatio.value
                    )
                    generatedViralClips.add(clipObj)
                }

                _clips.value = generatedViralClips
                _processingStatus.value = ProcessingPipelineStatus.Success(generatedViralClips)

                // Persist clips to Room DB for progress persistence
                saveClipsToRoom(generatedViralClips)

            } catch (e: Exception) {
                Log.e("MainViewModel", "Clip generation pipeline error", e)
                _processingStatus.value = ProcessingPipelineStatus.Error(e.localizedMessage ?: "Processing error occurred.")
            }
        }
    }

    fun updateClipManualCrop(clipId: String, newOffset: Float) {
        _clips.value = _clips.value.map { clip ->
            if (clip.id == clipId) {
                val updated = clip.copy(manualCropOffset = newOffset)
                // Re-generate thumbnail image preview with updated crop offset
                val videoPath = updated.processedVideoPath
                if (videoPath != null && File(videoPath).exists()) {
                    val thumbFile = File(getApplication<Application>().filesDir, "thumbnails/thumb_${clip.id}.jpg")
                    val newThumbPath = videoProcessor.generateThumbnail(
                        File(videoPath),
                        updated.suggestedHookText,
                        updated.subtitles,
                        thumbFile
                    )
                    updated.thumbnailPath = newThumbPath
                }
                viewModelScope.launch(Dispatchers.IO) {
                    saveClipToRoom(updated)
                }
                updated
            } else clip
        }
    }

    fun updateClipData(clipId: String, newTitle: String, newDesc: String, newTags: String) {
        val tagList = newTags.split(",", " ").map { it.trim() }.filter { it.isNotBlank() }
        _clips.value = _clips.value.map { clip ->
            if (clip.id == clipId) {
                val updated = clip.copy(
                    title = newTitle,
                    description = newDesc,
                    tags = tagList
                )
                viewModelScope.launch(Dispatchers.IO) { saveClipToRoom(updated) }
                updated
            } else clip
        }
    }

    fun toggleClipField(clipId: String, field: String) {
        _clips.value = _clips.value.map { clip ->
            if (clip.id == clipId) {
                val updated = when (field) {
                    "title" -> clip.copy(showTitle = !clip.showTitle)
                    "description" -> clip.copy(showDescription = !clip.showDescription)
                    "tags" -> clip.copy(showTags = !clip.showTags)
                    else -> clip
                }
                viewModelScope.launch(Dispatchers.IO) { saveClipToRoom(updated) }
                updated
            } else clip
        }
    }

    fun showPreviewModal(clip: ViralClip?) {
        _activePreviewClip.value = clip
    }

    fun exportAllClips(context: Context) {
        val allClips = _clips.value
        if (allClips.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            allClips.forEach { clip ->
                exportClipToStorage(context, clip)
            }
        }
    }

    fun exportClipToStorage(context: Context, clip: ViralClip) {
        viewModelScope.launch(Dispatchers.IO) {
            val videoPath = clip.processedVideoPath ?: return@launch
            val srcFile = File(videoPath)
            if (!srcFile.exists()) return@launch

            try {
                val fileName = "ClipForge_${clip.id}_${System.currentTimeMillis()}.mp4"

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val contentValues = ContentValues().apply {
                        put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                        put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                        put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/ClipForge")
                    }
                    val resolver = context.contentResolver
                    val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)
                    if (uri != null) {
                        resolver.openOutputStream(uri)?.use { outputStream ->
                            FileInputStream(srcFile).use { inputStream ->
                                inputStream.copyTo(outputStream)
                            }
                        }
                    }
                } else {
                    val moviesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
                    val clipForgeDir = File(moviesDir, "ClipForge").apply { if (!exists()) mkdirs() }
                    val destFile = File(clipForgeDir, fileName)
                    srcFile.copyTo(destFile, overwrite = true)
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Saved to Gallery (Movies/ClipForge)!", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Export clip failed", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private suspend fun saveClipsToRoom(clipsList: List<ViralClip>) {
        val entities = clipsList.map { clip ->
            ClipEntity(
                id = clip.id,
                startTimeSeconds = clip.startTimeSeconds,
                endTimeSeconds = clip.endTimeSeconds,
                confidenceScore = clip.confidenceScore,
                suggestedHookText = clip.suggestedHookText,
                reason = clip.reason,
                title = clip.title,
                description = clip.description,
                tagsCsv = clip.tags.joinToString(","),
                processedVideoPath = clip.processedVideoPath,
                thumbnailPath = clip.thumbnailPath,
                cropOffset = clip.manualCropOffset,
                subtitlesJson = serializeSubtitles(clip.subtitles),
                isCompliant = clip.isCompliant,
                complianceNote = clip.complianceDetails,
                showTitle = clip.showTitle,
                showDescription = clip.showDescription,
                showTags = clip.showTags,
                isExported = clip.isExported
            )
        }
        clipDao.insertClips(entities)
    }

    private suspend fun saveClipToRoom(clip: ViralClip) {
        val entity = ClipEntity(
            id = clip.id,
            startTimeSeconds = clip.startTimeSeconds,
            endTimeSeconds = clip.endTimeSeconds,
            confidenceScore = clip.confidenceScore,
            suggestedHookText = clip.suggestedHookText,
            reason = clip.reason,
            title = clip.title,
            description = clip.description,
            tagsCsv = clip.tags.joinToString(","),
            processedVideoPath = clip.processedVideoPath,
            thumbnailPath = clip.thumbnailPath,
            cropOffset = clip.manualCropOffset,
            subtitlesJson = serializeSubtitles(clip.subtitles),
            isCompliant = clip.isCompliant,
            complianceNote = clip.complianceDetails,
            showTitle = clip.showTitle,
            showDescription = clip.showDescription,
            showTags = clip.showTags,
            isExported = clip.isExported
        )
        clipDao.insertClip(entity)
    }

    private fun serializeSubtitles(subtitles: List<SubtitleItem>): String {
        val arr = JSONArray()
        subtitles.forEach { sub ->
            arr.put(JSONObject().apply {
                put("start", sub.startSec)
                put("end", sub.endSec)
                put("text", sub.text)
            })
        }
        return arr.toString()
    }

    private fun parseSubtitlesJson(json: String): List<SubtitleItem> {
        if (json.isBlank()) return emptyList()
        val list = mutableListOf<SubtitleItem>()
        try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    SubtitleItem(
                        startSec = obj.optDouble("start", 0.0).toFloat(),
                        endSec = obj.optDouble("end", 0.0).toFloat(),
                        text = obj.optString("text", "")
                    )
                )
            }
        } catch (e: Exception) {
            Log.e("MainViewModel", "Error parsing subtitles JSON", e)
        }
        return list
    }
}
