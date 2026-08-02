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

data class ParsedCampaignRules(
    val requiredCaptionText: String = "",
    val requiredHandles: List<String> = emptyList(),
    val requiredHashtags: List<String> = emptyList(),
    val platformRules: String = "",
    val minClipDuration: Int? = null,
    val maxClipDuration: Int? = null,
    val brandingRules: String = ""
)

class GeminiClipService {

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(90, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(90, TimeUnit.SECONDS)
        .build()

    /**
     * Step 1: Extract structured fields from rules file text before analyzing clips.
     */
    fun parseCampaignRules(rulesText: String): ParsedCampaignRules {
        if (rulesText.isBlank()) return ParsedCampaignRules()

        val rawHandles = mutableListOf<String>()
        val handleMatcher = Pattern.compile("@[a-zA-Z0-9_]{4,30}").matcher(rulesText)
        while (handleMatcher.find()) {
            val handle = handleMatcher.group()
            if (isValidSocialHandle(handle)) {
                rawHandles.add(handle)
            }
        }

        val rawHashtags = mutableListOf<String>()
        val hashtagMatcher = Pattern.compile("#[a-zA-Z0-9_]{2,40}").matcher(rulesText)
        while (hashtagMatcher.find()) {
            rawHashtags.add(hashtagMatcher.group())
        }

        // Extract REQUIRED_CAPTION_TEXT if explicitly quoted or indicated
        var captionText = ""
        val captionLine = rulesText.lines().firstOrNull {
            it.contains("caption", ignoreCase = true) || it.contains("required text", ignoreCase = true) || it.contains("must include", ignoreCase = true)
        }
        if (captionLine != null) {
            captionText = captionLine.replace(Regex("(?i)^(required text|caption|must include)[:\\s]*"), "").trim()
        }

        return ParsedCampaignRules(
            requiredCaptionText = captionText,
            requiredHandles = rawHandles.distinct(),
            requiredHashtags = rawHashtags.distinct(),
            platformRules = extractMatchingSection(rulesText, listOf("platform", "posting", "tiktok", "reels", "youtube")),
            brandingRules = extractMatchingSection(rulesText, listOf("brand", "logo", "watermark", "overlay"))
        )
    }

    private fun extractMatchingSection(text: String, keywords: List<String>): String {
        return text.lines()
            .filter { line -> keywords.any { kw -> line.contains(kw, ignoreCase = true) } }
            .joinToString("\n")
            .take(500)
    }

    suspend fun analyzeVideoForViralClips(
        apiKey: String,
        audioBase64: String?,
        transcriptOrContent: String,
        campaignRulesText: String,
        customInstructions: String,
        videoDurationSeconds: Float,
        minDurationSeconds: Int = 15,
        maxDurationSeconds: Int = 60
    ): List<RawGeminiClip> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            throw Exception("Invalid Gemini API Key. Please configure a valid API key in Settings.")
        }

        // Step 1: Pre-parse campaign rules file locally
        val campaignRules = parseCampaignRules(campaignRulesText)

        val effectiveMinDuration = campaignRules.minClipDuration ?: minDurationSeconds
        val effectiveMaxDuration = campaignRules.maxClipDuration ?: maxDurationSeconds

        val systemPrompt = """
            You are an expert viral video editor and content strategist.
            Analyze the provided video transcript and speech audio to find high-engagement, viral clips.

            CRITICAL DIRECTIVES:
            1. CLIP SELECTION & DURATIONS:
               - Video total duration: $videoDurationSeconds seconds.
               - Each clip MUST be strictly between $effectiveMinDuration and $effectiveMaxDuration seconds.
               - Timestamps MUST be within 0.0 and $videoDurationSeconds seconds.

            2. CONTENT GROUNDING (STRICT RULE):
               - "suggested_hook_text", "suggested_title", and clip captions MUST come directly from what the speaker ACTUALLY SAYS in that specific clip transcript.
               - NEVER invent topics or subjects from outside the specific clip dialogue.
               - DO NOT output spammy, random, or auto-generated @handles.

            3. CAMPAIGN RULES & METADATA FORMAT:
               - "suggested_description" (caption): MUST contain the hook text followed by the exact REQUIRED_CAPTION_TEXT from rules (if any): "${campaignRules.requiredCaptionText}".
               - "suggested_tags": Include ONLY real, explicitly named @handles from rules: ${campaignRules.requiredHandles.joinToString(", ")}. IF NO HANDLES WERE SPECIFIED IN RULES, RETURN AN EMPTY ARRAY. DO NOT GENERATE @HANDLES.
               - "hashtags": 3-5 real topic hashtags based strictly on what the speaker says in this clip.

            Return JSON matching this EXACT structure:
            {
              "clips": [
                {
                  "start_time": float,
                  "end_time": float,
                  "confidence_score": float (0.50 - 0.99),
                  "suggested_hook_text": "Exact hook line spoken in clip",
                  "suggested_title": "Descriptive title based on what speaker says in clip",
                  "suggested_description": "Hook line + campaign required caption text",
                  "suggested_tags": ["@real_handle_from_rules_only"],
                  "hashtags": ["#Topic1", "#Topic2", "#Topic3"],
                  "reason": "Why this moment is high engagement",
                  "subtitles": [{"start_sec": float, "end_sec": float, "text": "spoken words"}]
                }
              ]
            }
        """.trimIndent()

        val userPrompt = StringBuilder().apply {
            appendLine("=== VIDEO TRANSCRIPT & AUDIO METADATA ===")
            appendLine("Total Duration: ${videoDurationSeconds}s")
            if (transcriptOrContent.isNotBlank()) {
                appendLine("Actual Transcript / Speech Content:\n${transcriptOrContent.take(12000)}")
            }

            if (campaignRulesText.isNotBlank()) {
                appendLine("\n=== CAMPAIGN RULES DOCUMENT ===")
                appendLine(campaignRulesText.take(3000))
                if (campaignRules.requiredHandles.isNotEmpty()) {
                    appendLine("Allowed @handles: ${campaignRules.requiredHandles.joinToString(", ")}")
                }
            }

            if (customInstructions.isNotBlank()) {
                appendLine("\n=== USER CUSTOM INSTRUCTIONS ===")
                appendLine(customInstructions)
            }

            appendLine("\nIdentify all viral clip segments matching duration requirements. Ground all text in actual clip speech.")
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
                put("temperature", 0.3)
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
                    val parsedClips = parseRawClipsJson(rawOutput, videoDurationSeconds, campaignRules)
                    if (parsedClips.isNotEmpty()) {
                        return@withContext parsedClips
                    }
                }
            }
        }

        throw Exception("Gemini returned no viral clips for this video. Try adjusting custom instructions or using a video with clearer spoken dialogue.")
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
        campaignRules: ParsedCampaignRules
    ): List<RawGeminiClip> {
        val resultList = mutableListOf<RawGeminiClip>()

        try {
            val rootObj = JSONObject(rawJsonText)
            val clipsArr = rootObj.optJSONArray("clips") ?: JSONArray()
            for (i in 0 until clipsArr.length()) {
                val clipObj = clipsArr.getJSONObject(i)
                var startTime = clipObj.optDouble("start_time", 0.0).toFloat()
                var endTime = clipObj.optDouble("end_time", 30.0).toFloat()

                if (maxDurationSeconds > 0) {
                    startTime = startTime.coerceIn(0f, (maxDurationSeconds - 5f).coerceAtLeast(0f))
                    endTime = endTime.coerceIn(startTime + 5f, maxDurationSeconds)
                }

                val confidence = clipObj.optDouble("confidence_score", 0.90).toFloat()
                val hookText = clipObj.optString("suggested_hook_text", "Key Highlight 🔥")
                val reason = clipObj.optString("reason", "Spoken highlight from clip.")
                val title = clipObj.optString("suggested_title", "Viral Clip #${i + 1}")
                var desc = clipObj.optString("suggested_description", hookText)

                // Enforce required caption text if specified in rules
                if (campaignRules.requiredCaptionText.isNotBlank() && !desc.contains(campaignRules.requiredCaptionText, ignoreCase = true)) {
                    desc = "$desc\n\n${campaignRules.requiredCaptionText}"
                }

                // Process & Validate Handles (Strict filter: real handles only, >= 4 chars)
                val rawTagsArr = clipObj.optJSONArray("suggested_tags")
                val validatedHandles = mutableListOf<String>()

                if (rawTagsArr != null) {
                    for (t in 0 until rawTagsArr.length()) {
                        val handle = rawTagsArr.getString(t).trim()
                        if (isValidSocialHandle(handle)) {
                            validatedHandles.add(handle)
                        }
                    }
                }

                // Append explicitly required handles from campaign rules if missing
                campaignRules.requiredHandles.forEach { ruleHandle ->
                    if (isValidSocialHandle(ruleHandle) && !validatedHandles.any { it.equals(ruleHandle, ignoreCase = true) }) {
                        validatedHandles.add(ruleHandle)
                    }
                }

                // Process Hashtags (Topic hashtags based on clip content)
                val hashtagsList = mutableListOf<String>()
                val rawHashtagsArr = clipObj.optJSONArray("hashtags")
                if (rawHashtagsArr != null) {
                    for (h in 0 until rawHashtagsArr.length()) {
                        val tag = rawHashtagsArr.getString(h).trim()
                        if (tag.startsWith("#") && tag.length >= 3) {
                            hashtagsList.add(tag)
                        }
                    }
                }

                // Fallback to rules hashtags if empty
                if (hashtagsList.isEmpty() && campaignRules.requiredHashtags.isNotEmpty()) {
                    hashtagsList.addAll(campaignRules.requiredHashtags)
                }

                // Combine valid handles and hashtags into final tags list
                val finalCombinedTags = (validatedHandles + hashtagsList).distinct()

                // Parse Subtitles
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

                if (subtitlesList.isEmpty()) {
                    subtitlesList.add(SubtitleItem(startTime, (startTime + 4.0f).coerceAtMost(endTime), hookText))
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
                        suggested_tags = finalCombinedTags,
                        subtitles = subtitlesList
                    )
                )
            }
        } catch (e: Exception) {
            Log.e("GeminiClipService", "Error parsing clips JSON string", e)
        }
        return resultList
    }

    /**
     * Validates whether a handle string is a real social media handle and not spammy/random string.
     * Criteria:
     * - Must start with '@'
     * - Total length (excluding @) must be >= 3 characters (handle length >= 4 including '@')
     * - Must not look like purely random hex/hash strings (e.g. @a7f3k9p2)
     */
    private fun isValidSocialHandle(handle: String): Boolean {
        if (!handle.startsWith("@")) return false
        val cleanName = handle.removePrefix("@")
        if (cleanName.length < 3 || cleanName.length > 30) return false

        // Check if string is random spammy alphanumeric noise (e.g., random hash with high digit density or keyboard mash)
        val digitCount = cleanName.count { it.isDigit() }
        if (cleanName.length > 6 && digitCount.toFloat() / cleanName.length > 0.6f) {
            return false
        }

        // Must match standard handle regex
        return Pattern.matches("^[a-zA-Z0-9_.]{3,30}$", cleanName)
    }

    private fun String?.isNull_or_blank_safe(): Boolean = this == null || this.isBlank()
}

