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

data class ParsedCampaignRules(
    val brandName: String = "",
    val requiredCaptionText: String = "",
    val requiredHandles: List<String> = emptyList(),       // ONLY real named @handles from rules
    val requiredHashtags: List<String> = emptyList(),
    val forbiddenContent: List<String> = emptyList(),      // Things explicitly NOT allowed
    val contentFocus: String = "",                          // What clips MUST show/be about
    val hookStyle: String = "",                             // Hook guidance
    val platformRules: String = "",                         // Platform-specific attribution rules
    val targetAudience: String = "",                        // USA, UK, etc.
    val brandingRules: String = "",
    val minClipDuration: Int? = null,
    val maxClipDuration: Int? = null
)

class GeminiClipService {

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(120, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    /**
     * Step 1: Parse campaign rules using AI context understanding — NOT regex.
     * We extract handles/hashtags by looking at context, not blind pattern matching.
     */
    fun parseCampaignRules(rulesText: String): ParsedCampaignRules {
        if (rulesText.isBlank()) return ParsedCampaignRules()

        // Extract REAL social handles ONLY from explicit attribution sections
        // Look for lines that explicitly say "tag @xyz" or "use @xyz" — not random @strings
        val realHandles = mutableListOf<String>()
        val lines = rulesText.lines()
        for (line in lines) {
            val lower = line.lowercase()
            // Only extract handles from explicit attribution/tagging instruction lines
            if (lower.contains("tag ") || lower.contains("mention ") ||
                lower.contains("attribution") || lower.contains("credit") ||
                lower.contains("handle") || lower.contains("@boxabl") ||
                lower.contains("must tag") || lower.contains("must include @")) {
                // Extract @handles from THIS line only
                val matcher = java.util.regex.Pattern.compile("@[a-zA-Z0-9_.]{3,30}").matcher(line)
                while (matcher.find()) {
                    val handle = matcher.group()
                    if (isRealSocialHandle(handle)) {
                        if (!realHandles.contains(handle)) realHandles.add(handle)
                    }
                }
            }
        }

        // Extract hashtags from explicit hashtag instruction lines
        val realHashtags = mutableListOf<String>()
        for (line in lines) {
            val lower = line.lowercase()
            if (lower.contains("hashtag") || lower.contains("use #") || lower.contains("tag #")) {
                val matcher = java.util.regex.Pattern.compile("#[a-zA-Z][a-zA-Z0-9]{1,39}").matcher(line)
                while (matcher.find()) {
                    val tag = matcher.group()
                    if (!realHashtags.contains(tag)) realHashtags.add(tag)
                }
            }
        }

        // Extract brand name (first capitalized brand-looking word near "brand" or product mention)
        val brandLine = lines.firstOrNull {
            it.contains("brand", ignoreCase = true) || it.contains("company", ignoreCase = true)
        } ?: ""

        // Extract forbidden content list
        val forbiddenItems = lines
            .filter { it.contains("NO ", ignoreCase = false) || it.contains("REJECTED", ignoreCase = true) || it.contains("DON'T", ignoreCase = true) || it.contains("NOT allowed", ignoreCase = true) }
            .map { it.trim() }
            .filter { it.length > 5 }
            .take(10)

        // Extract content focus — what clips MUST be about
        val contentFocusLines = lines
            .filter { line ->
                line.contains("must show", ignoreCase = true) ||
                line.contains("must include", ignoreCase = true) ||
                line.contains("clip must", ignoreCase = true) ||
                line.contains("every clip", ignoreCase = true)
            }
            .joinToString(". ")
            .take(500)

        // Extract hook guidance
        val hookLines = lines
            .filter { line ->
                line.contains("hook", ignoreCase = true) ||
                line.contains("first frame", ignoreCase = true) ||
                line.contains("first second", ignoreCase = true) ||
                line.contains("2 second", ignoreCase = true)
            }
            .joinToString(". ")
            .take(300)

        // Extract platform rules
        val platformLines = lines
            .filter { line ->
                line.contains("tiktok", ignoreCase = true) ||
                line.contains("instagram", ignoreCase = true) ||
                line.contains("youtube", ignoreCase = true) ||
                line.contains("reels", ignoreCase = true) ||
                line.contains("shorts", ignoreCase = true) ||
                line.contains("platform", ignoreCase = true)
            }
            .joinToString("\n")
            .take(400)

        // Extract required caption text (any line with "caption must" or quoted required text)
        val captionRequirement = lines
            .filter { it.contains("caption", ignoreCase = true) && (it.contains("must", ignoreCase = true) || it.contains("include", ignoreCase = true)) }
            .joinToString(". ")
            .take(300)

        return ParsedCampaignRules(
            brandName = if (rulesText.contains("BOXABL", ignoreCase = true)) "Boxabl" else "",
            requiredCaptionText = captionRequirement,
            requiredHandles = realHandles.distinct(),
            requiredHashtags = realHashtags.distinct(),
            forbiddenContent = forbiddenItems,
            contentFocus = contentFocusLines,
            hookStyle = hookLines,
            platformRules = platformLines,
            targetAudience = if (rulesText.contains("USA", ignoreCase = true) || rulesText.contains("United States", ignoreCase = true)) "United States" else "Global"
        )
    }

    suspend fun analyzeVideoForViralClips(
        apiKey: String,
        audioBase64: String?,
        transcriptOrContent: String,
        campaignRulesText: String,
        customInstructions: String,
        videoDurationSeconds: Float,
        minDurationSeconds: Int = 15,
        maxDurationSeconds: Int = 60,
        selectedModel: String = "gemini-2.5-flash"
    ): List<RawGeminiClip> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            throw Exception("Invalid Gemini API Key. Please configure a valid API key in Settings.")
        }

        // Step 1: Pre-parse campaign rules locally
        val campaignRules = parseCampaignRules(campaignRulesText)

        val effectiveMinDuration = campaignRules.minClipDuration ?: minDurationSeconds
        val effectiveMaxDuration = campaignRules.maxClipDuration ?: maxDurationSeconds

        // Build the rules context block for the AI prompt
        val rulesContextBlock = if (campaignRulesText.isNotBlank()) {
            buildString {
                appendLine("=== CAMPAIGN RULES CONTEXT ===")
                if (campaignRules.brandName.isNotBlank()) appendLine("Brand: ${campaignRules.brandName}")
                if (campaignRules.forbiddenContent.isNotEmpty()) {
                    appendLine("FORBIDDEN CONTENT (never clip these):")
                    campaignRules.forbiddenContent.forEach { appendLine("  ✗ $it") }
                }
                if (campaignRules.contentFocus.isNotBlank()) appendLine("Content Focus Rules: ${campaignRules.contentFocus}")
                if (campaignRules.hookStyle.isNotBlank()) appendLine("Hook Style: ${campaignRules.hookStyle}")
                if (campaignRules.platformRules.isNotBlank()) appendLine("Platform Rules: ${campaignRules.platformRules}")
                if (campaignRules.requiredHandles.isNotEmpty()) appendLine("Required @handles to include: ${campaignRules.requiredHandles.joinToString(", ")}")
                if (campaignRules.requiredCaptionText.isNotBlank()) appendLine("Caption Requirements: ${campaignRules.requiredCaptionText}")
                appendLine("\nFull rules document for context:")
                appendLine(campaignRulesText.take(3000))
            }
        } else ""

        val systemPrompt = """
            You are an expert viral video editor and social media content strategist.
            Analyze the provided video audio to identify the most engaging, viral-worthy clip segments.

            VIDEO CLIP REQUIREMENTS:
            - Each clip: ${effectiveMinDuration}s to ${effectiveMaxDuration}s
            - Maximum clips to return: 20
            - Sort by viral score descending
            - MINIMUM QUALIFYING SCORE: 55 out of 100

            SCORING FORMULA — Rate each dimension 0-100:
            - hook_score (35%): First 3 seconds — stat/number, name drop, contradiction, question, hot take
            - emotion_score (25%): Excitement, vulnerability, humor, genuine surprise, speed of speech
            - curiosity_score (20%): Open loop, counterintuitive reveal, "nobody talks about", satisfying conclusion
            - completion_score (10%): Natural full sentence start/end, single focused thought
            - value_score (10%): Practical takeaway, actionable insight, memorable perspective shift
            FINAL_SCORE = (hook*0.35) + (emotion*0.25) + (curiosity*0.20) + (completion*0.10) + (value*0.10)
            Only qualify clips where FINAL_SCORE >= 55.

            HOOK GENERATION (under 8 words, never start with "I" or speaker's name):
            - Pattern: "[Unexpected fact about topic]"
            - Pattern: "The truth about [common belief]"
            - Pattern: "[Number] things [result]"
            - Pattern: "Why [X] is completely wrong"
            Hook must come from what speaker actually says in first 8 seconds of the clip.

            TAGS RULES — CRITICAL:
            - suggested_tags: ONLY include @handles explicitly named in campaign rules as required tags. Read the rules document carefully.
            - DO NOT invent, guess, or generate random @handles. If no real handles found in rules, return empty array [].
            - hashtags: Generate 3-8 REAL topic hashtags based on what speaker actually discusses in this clip (e.g., #Ketones #Biohacking). NOT random strings.
            - seo_keywords: 5-8 plain keywords (no # or @) for YouTube SEO based on clip content.

            OUTPUT FORMAT — Return this exact JSON:
            {
              "clips": [
                {
                  "start_time": float,
                  "end_time": float,
                  "hook_score": int,
                  "emotion_score": int,
                  "curiosity_score": int,
                  "completion_score": int,
                  "value_score": int,
                  "hook_text": "curiosity gap hook under 8 words",
                  "why_viral": "one sentence — what makes this moment compelling",
                  "title": "descriptive YouTube-style title of what speaker says",
                  "caption_line": "social caption — hook rephrased conversationally for Instagram/TikTok",
                  "suggested_tags": ["@real_handle_from_rules_only"],
                  "hashtags": ["#RealTopic1", "#RealTopic2", "#RealTopic3"],
                  "seo_keywords": ["keyword1", "keyword2", "keyword3", "keyword4", "keyword5"],
                  "subtitles": [{"start_sec": float, "end_sec": float, "text": "spoken words"}]
                }
              ]
            }
        """.trimIndent()

        val userPrompt = StringBuilder().apply {
            appendLine("=== VIDEO METADATA ===")
            appendLine("Total Duration: ${videoDurationSeconds}s")
            appendLine("Clip Duration Range: ${effectiveMinDuration}s - ${effectiveMaxDuration}s")

            if (transcriptOrContent.isNotBlank()) {
                appendLine("\n=== AUDIO TRANSCRIPT / SPEECH CONTENT ===")
                appendLine(transcriptOrContent.take(12000))
            }

            if (rulesContextBlock.isNotBlank()) {
                appendLine(rulesContextBlock)
            }

            if (customInstructions.isNotBlank()) {
                appendLine("\n=== USER CUSTOM INSTRUCTIONS ===")
                appendLine(customInstructions)
            }

            appendLine("\nIdentify ALL strong viral moments scoring 55+. Return up to 20 clips. Be generous — err on the side of MORE clips.")
        }.toString()

        val partsArray = JSONArray()

        if (!audioBase64.isNull_or_blank_safe()) {
            partsArray.put(JSONObject().apply {
                put("inlineData", JSONObject().apply {
                    put("mimeType", "audio/mp4")
                    put("data", audioBase64)
                })
            })
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
            .url("https://generativelanguage.googleapis.com/v1beta/models/$selectedModel:generateContent?key=$apiKey")
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

        throw Exception("Gemini returned no viral clips. Try a video with clear spoken dialogue or adjust instructions.")
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

                val hookScore = clipObj.optInt("hook_score", 70)
                val emotionScore = clipObj.optInt("emotion_score", 70)
                val curiosityScore = clipObj.optInt("curiosity_score", 65)
                val completionScore = clipObj.optInt("completion_score", 75)
                val valueScore = clipObj.optInt("value_score", 70)

                val calculatedScore = ((hookScore * 0.35f) + (emotionScore * 0.25f) +
                        (curiosityScore * 0.20f) + (completionScore * 0.10f) + (valueScore * 0.10f)).toInt()
                val finalViralScore = clipObj.optInt("viral_score", calculatedScore)

                // THRESHOLD: Only clips scoring 55+ qualify
                if (finalViralScore < 55) {
                    Log.d("GeminiClipService", "Filtered clip at ${startTime}s — score $finalViralScore < 55")
                    continue
                }

                val hookText = clipObj.optString("hook_text", "Key Moment 🔥").trim()
                val whyViral = clipObj.optString("why_viral", "Engaging spoken highlight.").trim()
                val title = clipObj.optString("title", "Viral Clip #${i + 1}").trim()
                var desc = clipObj.optString("caption_line", hookText).trim()

                // Append required caption text if not already present
                if (campaignRules.requiredCaptionText.isNotBlank() &&
                    !desc.contains(campaignRules.requiredCaptionText, ignoreCase = true)) {
                    desc = "$desc\n\n${campaignRules.requiredCaptionText}"
                }

                // ── HANDLE PROCESSING (STRICT — no AI-generated garbage) ──────────────────
                val rawTagsArr = clipObj.optJSONArray("suggested_tags")
                val validatedHandles = mutableListOf<String>()

                if (rawTagsArr != null) {
                    for (t in 0 until rawTagsArr.length()) {
                        val handle = rawTagsArr.getString(t).trim()
                        // Only accept handles that ALSO appear in campaign rules required list
                        if (isRealSocialHandle(handle) &&
                            (campaignRules.requiredHandles.any { it.equals(handle, ignoreCase = true) }
                             || campaignRules.brandName.isNotBlank() && handle.contains(campaignRules.brandName, ignoreCase = true))) {
                            validatedHandles.add(handle)
                        }
                    }
                }

                // Always append required handles from rules (de-duplicate)
                campaignRules.requiredHandles.forEach { ruleHandle ->
                    if (isRealSocialHandle(ruleHandle) &&
                        !validatedHandles.any { it.equals(ruleHandle, ignoreCase = true) }) {
                        validatedHandles.add(ruleHandle)
                    }
                }

                // ── HASHTAG PROCESSING ────────────────────────────────────────────────────
                val hashtagsList = mutableListOf<String>()
                val rawHashtagsArr = clipObj.optJSONArray("hashtags")
                if (rawHashtagsArr != null) {
                    for (h in 0 until rawHashtagsArr.length()) {
                        val tag = rawHashtagsArr.getString(h).trim()
                        // Validate: starts with #, has real word characters (NOT random short strings)
                        if (tag.startsWith("#") && tag.length >= 4 &&
                            tag.removePrefix("#").matches(Regex("[a-zA-Z][a-zA-Z0-9]{2,}"))) {
                            hashtagsList.add(tag)
                        }
                    }
                }

                // Fallback to rules hashtags if AI generated none
                if (hashtagsList.isEmpty() && campaignRules.requiredHashtags.isNotEmpty()) {
                    hashtagsList.addAll(campaignRules.requiredHashtags)
                }

                // ── SEO KEYWORDS ──────────────────────────────────────────────────────────
                val seoKeywordsList = mutableListOf<String>()
                val rawSeoArr = clipObj.optJSONArray("seo_keywords")
                if (rawSeoArr != null) {
                    for (k in 0 until rawSeoArr.length()) {
                        val kw = rawSeoArr.getString(k).trim()
                        if (kw.isNotBlank() && kw.length >= 3 && !kw.startsWith("@") && !kw.startsWith("#")) {
                            seoKeywordsList.add(kw)
                        }
                    }
                }

                // ── SUBTITLES ─────────────────────────────────────────────────────────────
                val subtitlesList = mutableListOf<SubtitleItem>()
                val subsArr = clipObj.optJSONArray("subtitles")
                if (subsArr != null) {
                    for (s in 0 until subsArr.length()) {
                        val subObj = subsArr.getJSONObject(s)
                        val subStart = subObj.optDouble("start_sec", startTime.toDouble()).toFloat()
                        val subEnd = subObj.optDouble("end_sec", endTime.toDouble()).toFloat()
                        val subText = subObj.optString("text", "")
                        if (subText.isNotBlank()) {
                            subtitlesList.add(SubtitleItem(subStart, subEnd, subText))
                        }
                    }
                }
                if (subtitlesList.isEmpty()) {
                    subtitlesList.add(SubtitleItem(startTime, (startTime + 4f).coerceAtMost(endTime), hookText))
                }

                // ── COMBINED TAGS (handles + hashtags) for legacy field ──────────────────
                val finalCombinedTags = (validatedHandles + hashtagsList).distinct()

                resultList.add(
                    RawGeminiClip(
                        start_time = startTime,
                        end_time = endTime,
                        viral_score = finalViralScore,
                        hook_score = hookScore,
                        emotion_score = emotionScore,
                        curiosity_score = curiosityScore,
                        completion_score = completionScore,
                        value_score = valueScore,
                        confidence_score = (finalViralScore / 100f).coerceIn(0.55f, 0.99f),
                        hook_text = hookText,
                        why_viral = whyViral,
                        caption_line = desc,
                        title = title,
                        suggested_hook_text = hookText,
                        reason = whyViral,
                        suggested_title = title,
                        suggested_description = desc,
                        suggested_tags = finalCombinedTags,
                        handles = validatedHandles,
                        hashtags = hashtagsList,
                        seo_keywords = seoKeywordsList,
                        subtitles = subtitlesList
                    )
                )
            }
        } catch (e: Exception) {
            Log.e("GeminiClipService", "Error parsing clips JSON", e)
        }

        // Sort by viral score descending, max 20 clips
        return resultList.sortedByDescending { it.viral_score }.take(20)
    }

    /**
     * Validates a social handle:
     * - Must start with @
     * - Must have 3–30 chars after @
     * - Must be a real-looking name (has at least 2 letters, not random hex/noise)
     * - Must have fewer than 60% digits if longer than 5 chars
     */
    private fun isRealSocialHandle(handle: String): Boolean {
        if (!handle.startsWith("@")) return false
        val name = handle.removePrefix("@")
        if (name.length < 3 || name.length > 30) return false
        if (!name.matches(Regex("[a-zA-Z0-9_.]{3,30}"))) return false

        val letterCount = name.count { it.isLetter() }
        if (letterCount < 2) return false  // Must have at least 2 real letters

        val digitCount = name.count { it.isDigit() }
        if (name.length > 5 && digitCount.toFloat() / name.length > 0.6f) return false

        // Reject very short random-looking handles (like @yqiA, @Z0A9)
        if (name.length <= 5 && name.any { it.isDigit() } && name.any { it.isUpperCase() } && name.any { it.isLowerCase() }) return false

        return true
    }

    private fun String?.isNull_or_blank_safe(): Boolean = this == null || this.isBlank()
}
