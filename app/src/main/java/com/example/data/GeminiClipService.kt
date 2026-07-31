package com.example.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class GeminiClipService {

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(90, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(90, TimeUnit.SECONDS)
        .build()

    suspend fun analyzeVideoForViralClips(
        apiKey: String,
        audioBase64: String?,
        transcriptOrContent: String,
        campaignRulesText: String,
        customInstructions: String,
        videoDurationSeconds: Float
    ): List<RawGeminiClip> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            throw Exception("Invalid Gemini API Key. Please configure a valid API key in Settings.")
        }

        val systemPrompt = """
            You are an expert viral video editor and content strategist. 
            Analyze video transcriptions, campaign rules, and custom instructions to find ALL high-engagement, viral clips.
            Video total duration is: $videoDurationSeconds seconds.
            Each clip MUST be between 15 and 60 seconds long and within 0.0 and $videoDurationSeconds seconds.
            Return a JSON object containing an array "clips" with objects having fields:
            - "start_time": float seconds (e.g. 15.0)
            - "end_time": float seconds (e.g. 45.0)
            - "confidence_score": float between 0.50 and 0.99
            - "suggested_hook_text": short punchy text overlay (e.g., "Wait till you hear this! 😱")
            - "reason": concise explanation of why this clip is viral
            - "suggested_title": viral social media title for TikTok/Reels/Shorts
            - "suggested_description": engaging caption with call to action
            - "suggested_tags": array of 3-5 trending hashtags
            - "subtitles": array of 3-8 spoken phrase subtitle objects: [{"start_sec": float, "end_sec": float, "text": string}]
        """.trimIndent()

        val userPrompt = StringBuilder().apply {
            appendLine("=== VIDEO METADATA & CONTENT ===")
            appendLine("Video Duration: ${videoDurationSeconds}s")
            if (transcriptOrContent.isNotBlank()) {
                appendLine("Transcript / Speech Context:\n${transcriptOrContent.take(12000)}")
            }
            if (campaignRulesText.isNotBlank()) {
                appendLine("\n=== CAMPAIGN RULES ===")
                appendLine(campaignRulesText.take(3000))
            }
            if (customInstructions.isNotBlank()) {
                appendLine("\n=== CUSTOM USER INSTRUCTIONS ===")
                appendLine(customInstructions)
            }
            appendLine("\nIdentify ALL valid viral moments with spoken subtitle lines. Output JSON matching the specified schema.")
        }.toString()

        val partsArray = JSONArray()

        if (!audioBase64.isNull_or_blank_safe()) {
            val audioDataObj = JSONObject().apply {
                put("inlineData", JSONObject().apply {
                    put("mimeType", "audio/mp4")
                    put("data", audioBase64)
                })
            }
            partsArray.put(audioDataObj)
        }

        partsArray.put(JSONObject().put("text", userPrompt))

        val reqJson = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", partsArray)
                })
            })
            put("systemInstruction", JSONObject().apply {
                put("parts", JSONArray().apply {
                    put(JSONObject().put("text", systemPrompt))
                })
            })
            put("generationConfig", JSONObject().apply {
                put("responseMimeType", "application/json")
                put("temperature", 0.4)
            })
        }

        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey")
            .post(reqJson.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val httpResponse = okHttpClient.newCall(request).execute()
        val responseText = httpResponse.body?.string() ?: ""

        if (!httpResponse.isSuccessful) {
            Log.e("GeminiClipService", "Gemini API HTTP ${httpResponse.code}: $responseText")
            throw Exception("Gemini API error (HTTP ${httpResponse.code}): ${parseErrorMessage(responseText)}")
        }

        val jsonResponse = JSONObject(responseText)
        val candidates = jsonResponse.optJSONArray("candidates")
        if (candidates != null && candidates.length() > 0) {
            val firstCandidate = candidates.getJSONObject(0)
            val contentObj = firstCandidate.optJSONObject("content")
            val parts = contentObj?.optJSONArray("parts")
            if (parts != null && parts.length() > 0) {
                val rawOutput = parts.getJSONObject(0).optString("text", "")
                if (rawOutput.isNotBlank()) {
                    val parsedClips = parseRawClipsJson(rawOutput, videoDurationSeconds, campaignRulesText)
                    if (parsedClips.isNotEmpty()) {
                        return@withContext parsedClips
                    }
                }
            }
        }

        throw Exception("Gemini returned no viral clips for this video. Try adjusting custom instructions or using a longer video with spoken dialogue.")
    }

    private fun parseErrorMessage(responseText: String): String {
        return try {
            val json = JSONObject(responseText)
            val err = json.optJSONObject("error")
            err?.optString("message") ?: "API call failed."
        } catch (e: Exception) {
            "API call failed."
        }
    }

    private fun parseRawClipsJson(
        rawJsonText: String,
        maxDurationSeconds: Float,
        campaignRulesText: String
    ): List<RawGeminiClip> {
        val resultList = mutableListOf<RawGeminiClip>()
        val requiredHandlesAndTags = extractCampaignRequirements(campaignRulesText)

        try {
            val rootObj = JSONObject(rawJsonText)
            val clipsArr = rootObj.optJSONArray("clips") ?: JSONArray()
            for (i in 0 until clipsArr.length()) {
                val clipObj = clipsArr.getJSONObject(i)
                var startTime = clipObj.optDouble("start_time", 0.0).toFloat()
                var endTime = clipObj.optDouble("end_time", 30.0).toFloat()

                if (maxDurationSeconds > 0) {
                    startTime = startTime.coerceIn(0f, maxDurationSeconds - 5f)
                    endTime = endTime.coerceIn(startTime + 5f, maxDurationSeconds)
                }

                val confidence = clipObj.optDouble("confidence_score", 0.90).toFloat()
                val hookText = clipObj.optString("suggested_hook_text", "Wait till you hear this! 🔥")
                val reason = clipObj.optString("reason", "High emotional peak and engagement moment.")
                var title = clipObj.optString("suggested_title", "Viral Highlight #${i + 1}")
                var desc = clipObj.optString("suggested_description", "Check out this key moment!")

                val tagsList = mutableListOf<String>()
                val tagsArr = clipObj.optJSONArray("suggested_tags")
                if (tagsArr != null) {
                    for (t in 0 until tagsArr.length()) {
                        tagsList.add(tagsArr.getString(t))
                    }
                }

                // Parse Spoken Subtitles
                val subtitlesList = mutableListOf<SubtitleItem>()
                val subsArr = clipObj.optJSONArray("subtitles")
                if (subsArr != null) {
                    for (s in 0 until subsArr.length()) {
                        val subObj = subsArr.getJSONObject(s)
                        val sStart = subObj.optDouble("start_sec", (startTime + (s * 3)).toDouble()).toFloat()
                        val sEnd = subObj.optDouble("end_sec", (sStart + 2.5f).toDouble()).toFloat()
                        val text = subObj.optString("text", "")
                        if (text.isNotBlank()) {
                            subtitlesList.add(SubtitleItem(sStart, sEnd, text))
                        }
                    }
                }

                // If no subtitles were returned, construct fallbacks based on hookText
                if (subtitlesList.isEmpty()) {
                    subtitlesList.add(SubtitleItem(startTime, startTime + 4.0f, hookText))
                    subtitlesList.add(SubtitleItem(startTime + 4.0f, endTime, "Watch until the end! 🔥"))
                }

                // Enforce campaign compliance rules by auto-appending required handles and tags if missing
                requiredHandlesAndTags.handles.forEach { handle ->
                    if (!desc.contains(handle, ignoreCase = true)) {
                        desc = "$desc\nTag: $handle"
                    }
                }
                requiredHandlesAndTags.tags.forEach { tag ->
                    if (!tagsList.any { it.equals(tag, ignoreCase = true) }) {
                        tagsList.add(tag)
                    }
                }

                resultList.add(
                    RawGeminiClip(
                        start_time = startTime,
                        end_time = endTime,
                        confidence_score = confidence,
                        suggested_hook_text = hookText,
                        reason = reason,
                        suggested_title = title,
                        suggested_description = desc,
                        suggested_tags = tagsList,
                        subtitles = subtitlesList
                    )
                )
            }
        } catch (e: Exception) {
            Log.e("GeminiClipService", "Error parsing clips JSON string", e)
        }
        return resultList
    }

    private data class CampaignRequirements(
        val handles: List<String>,
        val tags: List<String>
    )

    private fun extractCampaignRequirements(text: String): CampaignRequirements {
        if (text.isBlank()) return CampaignRequirements(emptyList(), emptyList())
        val handles = mutableListOf<String>()
        val tags = mutableListOf<String>()

        val handleMatcher = Pattern.compile("@[a-zA-Z0-9_.]+").matcher(text)
        while (handleMatcher.find()) {
            handles.add(handleMatcher.group())
        }

        val tagMatcher = Pattern.compile("#[a-zA-Z0-9_]+").matcher(text)
        while (tagMatcher.find()) {
            tags.add(tagMatcher.group())
        }

        return CampaignRequirements(handles.distinct(), tags.distinct())
    }

    private fun String?.isNull_or_blank_safe(): Boolean = this == null || this.isBlank()
}
