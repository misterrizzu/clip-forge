package com.example.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class GoogleDriveDownloader(private val context: Context) {

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    fun extractFileId(url: String): String? {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return null

        // Pattern 1: https://drive.google.com/file/d/FILE_ID/view...
        val pattern1 = Pattern.compile("/file/d/([a-zA-Z0-9_-]+)")
        val matcher1 = pattern1.matcher(trimmed)
        if (matcher1.find()) return matcher1.group(1)

        // Pattern 2: id=FILE_ID
        val pattern2 = Pattern.compile("[?&]id=([a-zA-Z0-9_-]+)")
        val matcher2 = pattern2.matcher(trimmed)
        if (matcher2.find()) return matcher2.group(1)

        // Pattern 3: direct ID string if user just pasted the file ID
        if (trimmed.length in 25..50 && !trimmed.contains("/")) {
            return trimmed
        }

        return null
    }

    suspend fun downloadPublicDriveFile(
        fileId: String,
        onProgress: (Float) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val destFile = File(context.cacheDir, "gdrive_video_${fileId}_${System.currentTimeMillis()}.mp4")

        // First attempt direct export download URL
        var downloadUrl = "https://drive.google.com/uc?export=download&id=$fileId"
        var request = Request.Builder().url(downloadUrl).build()

        var response = okHttpClient.newCall(request).execute()

        // Handle Google Drive virus scan warning page for large files (>100MB)
        val bodyString = if (response.headers["Content-Type"]?.contains("text/html") == true) {
            response.body?.string() ?: ""
        } else {
            ""
        }

        if (bodyString.isNotBlank() && bodyString.contains("confirm=")) {
            // Extract confirm token
            val confirmPattern = Pattern.compile("confirm=([a-zA-Z0-9_-]+)")
            val matcher = confirmPattern.matcher(bodyString)
            if (matcher.find()) {
                val token = matcher.group(1)
                downloadUrl = "https://drive.google.com/uc?export=download&confirm=$token&id=$fileId"
                request = Request.Builder().url(downloadUrl).build()
                response = okHttpClient.newCall(request).execute()
            }
        }

        if (!response.isSuccessful) {
            throw Exception("Google Drive download failed (HTTP ${response.code}). Make sure the file access is set to 'Anyone with the link can view'.")
        }

        val contentType = response.headers["Content-Type"] ?: ""
        if (contentType.contains("text/html")) {
            throw Exception("Unable to download video file. Please ensure the Google Drive link is publicly accessible ('Anyone with the link').")
        }

        val body = response.body ?: throw Exception("Empty response body received from Google Drive.")
        val contentLength = body.contentLength()

        body.byteStream().use { inputStream ->
            FileOutputStream(destFile).use { outputStream ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                var totalBytesRead = 0L

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    totalBytesRead += bytesRead
                    if (contentLength > 0) {
                        onProgress(totalBytesRead.toFloat() / contentLength.toFloat())
                    }
                }
            }
        }

        if (!destFile.exists() || destFile.length() < 1024) {
            destFile.delete()
            throw Exception("Downloaded file is invalid or too small. Check that the file is a valid video.")
        }

        Log.d("GoogleDriveDownloader", "Successfully downloaded GDrive file ${destFile.length()} bytes")
        return@withContext destFile
    }
}
