package com.aiagent.ai_voice_agent.engine.stt

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Whisper API speech-to-text engine
 * Supports both Groq (free, fast) and OpenAI endpoints
 */
class SttEngine {
    companion object {
        private const val TAG = "SttEngine"
        private const val GROQ_WHISPER_URL = "https://api.groq.com/openai/v1/audio/transcriptions"
        private const val OPENAI_WHISPER_URL = "https://api.openai.com/v1/audio/transcriptions"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Transcribe audio file to text
     */
    suspend fun transcribe(
        audioFile: File,
        apiKey: String,
        language: String = "ru",
        provider: String = "groq"
    ): String = withContext(Dispatchers.IO) {
        Log.d(TAG, "Transcribing: ${audioFile.name} (${audioFile.length()} bytes), lang=$language, provider=$provider")
        
        if (apiKey.isEmpty()) {
            Log.e(TAG, "API key is EMPTY!")
            return@withContext ""
        }
        if (!audioFile.exists()) {
            Log.e(TAG, "File does not exist: ${audioFile.absolutePath}")
            return@withContext ""
        }
        if (audioFile.length() < 100) {
            Log.e(TAG, "File too small (${audioFile.length()} bytes), likely empty")
            return@withContext ""
        }

        // Determine correct MIME type from file extension
        val mimeType = when (audioFile.extension.lowercase()) {
            "m4a" -> "audio/mp4"
            "mp3" -> "audio/mpeg"
            "wav" -> "audio/wav"
            "ogg" -> "audio/ogg"
            "flac" -> "audio/flac"
            "webm" -> "audio/webm"
            "mpga" -> "audio/mpeg"
            else -> "audio/mp4"
        }
        Log.d(TAG, "MIME type: $mimeType for .${audioFile.extension}")

        // Select endpoint based on provider
        val url = if (provider == "groq") GROQ_WHISPER_URL else OPENAI_WHISPER_URL
        // У Groq своего whisper-1 нет — там whisper-large-v3-turbo
        val model = if (provider == "groq") "whisper-large-v3-turbo" else "whisper-1"
        Log.d(TAG, "Using endpoint: $url model: $model")

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "file",
                audioFile.name,
                audioFile.asRequestBody(mimeType.toMediaType())
            )
            .addFormDataPart("model", model)
            .addFormDataPart("language", language)
            .addFormDataPart("response_format", "text")
            .build()

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $apiKey")
            .post(requestBody)
            .build()

        try {
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                Log.e(TAG, "Whisper error ${response.code}: $body")
                return@withContext ""
            }

            // Whisper returns plain text with response_format=text
            val text = body.trim()
            Log.d(TAG, "Transcribed: '$text' (${text.length} chars)")
            return@withContext text
        } catch (e: Exception) {
            Log.e(TAG, "Transcription error: ${e.message}", e)
            return@withContext ""
        }
    }
}
