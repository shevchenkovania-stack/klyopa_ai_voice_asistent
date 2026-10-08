package com.aiagent.ai_voice_agent.engine.tts

import android.media.AudioFormat
import android.media.AudioManager as AndroidAudioManager
import android.media.AudioTrack
import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import okio.ByteString
import com.aiagent.ai_voice_agent.engine.EngineManager
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/**
 * TTS Engine — OpenAI TTS (основной) → Edge TTS (fallback) → Android TTS (offline)
 * Трёхуровневая система с автоматическим переключением при ошибках.
 */
class TtsEngine(private val context: Context) {
    companion object {
        private const val TAG = "TtsEngine"
        private const val DEFAULT_VOICE = "ru-RU-DmitryNeural"
        
        // OpenAI TTS config
        private const val OPENAI_TTS_URL = "https://api.openai.com/v1/audio/speech"
        private const val OPENAI_TTS_MODEL = "tts-1"
        private const val OPENAI_TTS_VOICE = "onyx" // onyx (мужской), nova (женский)
        
        // Edge TTS config (fallback)
        private const val TRUSTED_CLIENT_TOKEN = "6A5AA1D4EAFF4E9FB37E23D68491D6F4"
        private const val CHROMIUM_VERSION = "143.0.3650.75"
        private const val CHROMIUM_MAJOR = "143"
        private const val SEC_MS_GEC_VERSION = "1-$CHROMIUM_VERSION"
        private const val WSS_URL = "wss://speech.platform.bing.com/consumer/speech/synthesize/readaloud/edge/v1"
        private const val AUDIO_FORMAT = "audio-24khz-48kbitrate-mono-mp3"
        private const val WIN_EPOCH = 11644473600L
        
        // Retry config
        private const val MAX_RETRIES = 1
        private const val RETRY_DELAY_MS = 1000L

        // Playback timeout
        private const val PLAYBACK_TIMEOUT_MS = 60_000L // 60 секунд

        // TTS Cache config
        private const val TTS_CACHE_MAX_SIZE = 30 // max entries (LRU eviction)

        // PCM streaming config
        private const val PCM_SAMPLE_RATE = 24000
        private const val PCM_CHUNK_SIZE = 4096 // ~85ms of audio at 24kHz/16-bit
    }
    
    var voice: String = DEFAULT_VOICE
    var openaiApiKey: String = ""

    // TTS Cache: text+voice → PCM bytes. LRU eviction.
    // Cache hit = instant playback (no network).
    private val ttsCache = object : LinkedHashMap<String, ByteArray>(TTS_CACHE_MAX_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ByteArray>?): Boolean {
            return size > TTS_CACHE_MAX_SIZE
        }
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private var mediaPlayer: MediaPlayer? = null
    private val mediaPlayerLock = Any()
    private var audioManager: android.media.AudioManager? = null
    private var audioFocusRequest: android.media.AudioFocusRequest? = null
    
    // Android TTS fallback
    private var androidTts: TextToSpeech? = null
    private var androidTtsReady = false
    
    @Volatile
    var isSpeaking = false
        private set

    @Volatile
    private var edgeTtsBlockedUntil: Long = 0L
    
    // Callbacks
    var onTtsStarted: (() -> Unit)? = null
    var onTtsFinished: (() -> Unit)? = null
    var onTtsInterrupted: (() -> Unit)? = null
    
    /**
     * Инициализировать Android TTS как fallback.
     * Вызывать один раз при старте.
     */
    fun initAndroidTts() {
        androidTts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = androidTts?.setLanguage(Locale("ru"))
                androidTtsReady = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
                if (androidTtsReady) {
                    // Скорость и тон чуть быстрее для естественности
                    androidTts?.setPitch(1.0f)
                    androidTts?.setSpeechRate(1.0f)
                    Log.d(TAG, "Android TTS initialized (fallback ready)")
                } else {
                    Log.w(TAG, "Android TTS: Russian language not available")
                }
            } else {
                Log.w(TAG, "Android TTS init failed: status=$status")
            }
        }
    }

    /**
     * Synthesize and play text.
     * OpenAI TTS (основной) → Edge TTS (fallback) → Android TTS (offline)
     */
    suspend fun speak(text: String) = withContext(Dispatchers.IO) {
        // Clean up previous TTS silently (no interruption callbacks — we're about to play new TTS)
        synchronized(mediaPlayerLock) {
            if (isSpeaking) {
                try {
                    currentAudioTrack?.let { at ->
                        if (at.playState == AudioTrack.PLAYSTATE_PLAYING) at.stop()
                        at.release()
                    }
                    currentAudioTrack = null
                    mediaPlayer?.let { mp ->
                        if (mp.isPlaying) mp.stop()
                        mp.release()
                    }
                    mediaPlayer = null
                } catch (_: Exception) {}
                // Don't call stop() — it fires onTtsInterrupted which cascades into
                // session state changes (THINKING → IDLE) and cancels the pipeline.
            }
            isSpeaking = true
        }
        onTtsStarted?.invoke()

        requestAudioFocus()

        try {
            val voiceInfo = "═══════════════════════════════════════\n" +
                    "🔊 ГОВОРЮ ГОЛОСОМ: [$voice]\n" +
                    "═══════════════════════════════════════"
            Log.d(TAG, voiceInfo)
            EngineManager.logToFlutter("TTS", "info", "🔊 Голос: $voice")

            val isAndroidVoice = voice.equals("android", ignoreCase = true)

            if (!isAndroidVoice && !voice.startsWith("ru-RU-")) {
                val errorMsg = "❌ ОШИБКА: Голос НЕ русский! [$voice] — должен быть ru-RU-DmitryNeural"
                Log.e(TAG, errorMsg)
                EngineManager.logToFlutter("TTS", "error", errorMsg)
                throw Exception(errorMsg)
            }

            Log.d(TAG, "Synthesizing: '$text'")
            var audioBytes: ByteArray? = null

            // 0) Android TTS — если voice="android", сразу используем
            if (isAndroidVoice) {
                Log.d(TAG, "TTS: Android TTS (оффлайн, безлимитный)")
                EngineManager.logToFlutter("TTS", "info", "Android TTS (оффлайн)...")
                speakWithAndroidTts(text)
                onTtsFinished?.invoke()
                return@withContext
            }

            // 1) TTS Cache check (мгновенное воспроизведение без сети)
            val cacheKey = "$text|$voice"
            val cachedPcm = synchronized(ttsCache) { ttsCache[cacheKey] }
            if (cachedPcm != null) {
                Log.d(TAG, "TTS Cache HIT: '$text' (${cachedPcm.size} bytes)")
                EngineManager.logToFlutter("TTS", "info", "TTS Cache (мгновенно)")
                playPcmBytes(cachedPcm)
                onTtsFinished?.invoke()
                return@withContext
            }

            // 2) OpenAI TTS Streaming (основной — первый звук через ~100ms)
            if (openaiApiKey.isNotEmpty()) {
                Log.d(TAG, "TTS: попытка OpenAI Streaming TTS...")
                EngineManager.logToFlutter("TTS", "info", "OpenAI Streaming TTS...")
                val streamed = synthesizeAndPlayStreaming(text, openaiApiKey, cacheKey = cacheKey)
                if (streamed) {
                    onTtsFinished?.invoke()
                    return@withContext
                }
                Log.w(TAG, "TTS: Streaming не сработал, fallback на full-buffer...")
                audioBytes = synthesizeWithOpenAI(text, openaiApiKey)
            }

            // 3) Edge TTS fallback (если OpenAI не сработал)
            if (audioBytes == null || audioBytes.isEmpty()) {
                if (System.currentTimeMillis() < edgeTtsBlockedUntil) {
                    Log.d(TAG, "TTS: Edge TTS blocked (circuit breaker, ${((edgeTtsBlockedUntil - System.currentTimeMillis()) / 1000)}s left)")
                } else {
                    Log.d(TAG, "TTS: OpenAI не сработал, пробую Edge TTS...")
                    EngineManager.logToFlutter("TTS", "info", "Edge TTS...")
                    audioBytes = synthesizeWithEdgeTTS(text)
                    if (audioBytes == null || audioBytes.isEmpty()) {
                        edgeTtsBlockedUntil = System.currentTimeMillis() + 300_000L
                        Log.w(TAG, "TTS: Edge TTS failed, circuit breaker 5min")
                    }
                }
            }

            // 4) Android TTS offline fallback (русский)
            if (audioBytes == null || audioBytes.isEmpty()) {
                Log.w(TAG, "TTS: онлайн TTS не сработали, пробую Android TTS...")
                EngineManager.logToFlutter("TTS", "warning", "Android TTS (offline fallback)...")
                speakWithAndroidTts(text)
                onTtsFinished?.invoke()
                return@withContext
            }

            Log.d(TAG, "✅ TTS success: ${audioBytes.size} bytes (голос=$voice)")
            EngineManager.logToFlutter("TTS", "success", "✅ OK (голос=$voice, ${audioBytes.size} байт)")
            playAudio(audioBytes)
            onTtsFinished?.invoke()
        } catch (e: CancellationException) {
            // Coroutine cancelled (filler TTS interrupted by response) — exit silently
            // Do NOT call onTtsFinished — it would corrupt the state machine
            Log.d(TAG, "TTS cancelled (silent exit)")
            synchronized(mediaPlayerLock) { isSpeaking = false }
            throw e // Re-throw so withContext propagates cancellation properly
        } catch (e: Exception) {
            Log.e(TAG, "❌ TTS EXCEPTION: ${e.message}", e)
            EngineManager.logToFlutter("TTS", "error", "❌ EXCEPTION: ${e.message}")
            onTtsFinished?.invoke()
        } finally {
            synchronized(mediaPlayerLock) {
                isSpeaking = false
            }
        }
    }

    /**
     * Stop playback (barge-in or manual stop)
     */
    fun stop() {
        val wasSpeaking = isSpeaking
        
        synchronized(mediaPlayerLock) {
            try {
                // Stop AudioTrack (streaming TTS)
                currentAudioTrack?.let { at ->
                    if (at.playState == AudioTrack.PLAYSTATE_PLAYING) {
                        at.stop()
                    }
                    at.release()
                }
                currentAudioTrack = null

                // Stop MediaPlayer (file-based TTS)
                mediaPlayer?.let { mp ->
                    if (mp.isPlaying) {
                        mp.stop()
                    }
                    mp.release()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Stop error: ${e.message}")
            }
            mediaPlayer = null
            isSpeaking = false
        }
        
        // Освободить AudioFocus ПОСЛЕ остановки плеера
        releaseAudioFocus()
        
        // Notify if interrupted
        if (wasSpeaking) {
            Log.d(TAG, "TTS interrupted (barge-in)")
            onTtsInterrupted?.invoke()
        }
    }

    // ==================== AudioFocus ====================

    private fun requestAudioFocus() {
        audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            audioFocusRequest = android.media.AudioFocusRequest.Builder(android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                                .setOnAudioFocusChangeListener { focusChange ->
                                    when (focusChange) {
                                        android.media.AudioManager.AUDIOFOCUS_LOSS -> {
                                            // Permanent loss — another app took over
                                            Log.d(TAG, "AudioFocus: permanent LOSS, stopping TTS")
                                            stop()
                                        }
                                        android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                                            // Transient loss — IGNORE (could be our own TTS audio routing)
                                            // Only stop if we're NOT the ones speaking
                                            Log.d(TAG, "AudioFocus: transient loss (ignoring, isSpeaking=$isSpeaking)")
                                        }
                                        android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                                            // Уменьшить громкость
                                            mediaPlayer?.setVolume(0.2f, 0.2f)
                                        }
                                        android.media.AudioManager.AUDIOFOCUS_GAIN -> {
                                            // Восстановить громкость
                                            mediaPlayer?.setVolume(1.0f, 1.0f)
                                        }
                                    }
                                }
                .build()
            audioManager?.requestAudioFocus(audioFocusRequest!!)
        } else {
            @Suppress("DEPRECATION")
            audioManager?.requestAudioFocus({ }, android.media.AudioManager.STREAM_MUSIC, android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        }
        Log.d(TAG, "AudioFocus requested")
    }

    private fun releaseAudioFocus() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager?.abandonAudioFocus { }
        }
        Log.d(TAG, "AudioFocus released")
    }

    // ==================== Sec-MS-GEC Token Generation ====================

    /**
     * Generate Sec-MS-GEC token (same algorithm as Flutter edge_tts)
     * 1. Get Unix timestamp
     * 2. Add Windows epoch offset
     * 3. Round down to nearest 5 minutes
     * 4. Convert to 100-nanosecond intervals
     * 5. Concatenate with trusted client token
     * 6. SHA-256 hash -> uppercase hex
     */
    private fun generateSecMsGec(): String {
        val nowSeconds = System.currentTimeMillis() / 1000
        var ticks = nowSeconds + WIN_EPOCH
        ticks -= ticks % 300 // round to nearest 5 minutes
        val ticks100ns = ticks * 10000000L
        
        val strToHash = "$ticks100ns$TRUSTED_CLIENT_TOKEN"
        val md = MessageDigest.getInstance("SHA-256")
        val hash = md.digest(strToHash.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02X".format(it) }
    }

    /**
     * Generate random UUID hex string (no dashes)
     */
    private fun generateUuidHex(): String {
        return UUID.randomUUID().toString().replace("-", "")
    }

    /**
     * Generate random MUID cookie
     */
    private fun generateMuid(): String {
        val random = SecureRandom()
        return (1..32).map { random.nextInt(16).toString(16) }.joinToString("").uppercase()
    }

    /**
     * Generate JavaScript-style timestamp
     */
    private fun generateTimestamp(): String {
        val now = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        val weekdays = arrayOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        val months = arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
        
        val wd = weekdays[now.get(Calendar.DAY_OF_WEEK) - 1]
        val mo = months[now.get(Calendar.MONTH)]
        val d = "%02d".format(now.get(Calendar.DAY_OF_MONTH))
        val h = "%02d".format(now.get(Calendar.HOUR_OF_DAY))
        val mi = "%02d".format(now.get(Calendar.MINUTE))
        val s = "%02d".format(now.get(Calendar.SECOND))
        
        return "$wd $mo $d ${now.get(Calendar.YEAR)} $h:$mi:$s GMT+0000 (Coordinated Universal Time)"
    }

    // ==================== OpenAI TTS ====================

    /**
     * Synthesize text to audio bytes via OpenAI TTS API
     * Стабильный, одинаковый голос на всех устройствах.
     * Voice: onyx (мужской), nova (женский), echo, alloy, fable, shimmer
     */
    private suspend fun synthesizeWithOpenAI(text: String, apiKey: String, voice: String = "onyx"): ByteArray? {
        return try {
            val escapedText = text.replace("\\", "\\\\").replace("\"", "\\\"")
            val json = """
                {
                    "model": "$OPENAI_TTS_MODEL",
                    "input": "$escapedText",
                    "voice": "$voice",
                    "response_format": "mp3",
                    "speed": 1.0
                }
            """.trimIndent()

            val request = Request.Builder()
                .url(OPENAI_TTS_URL)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(json.toRequestBody("application/json".toMediaTypeOrNull()))
                .build()

            val response = client.newCall(request).execute()
            
            if (response.isSuccessful) {
                response.body?.bytes()
            } else {
                Log.e(TAG, "OpenAI TTS failed: ${response.code} ${response.message}")
                EngineManager.logToFlutter("TTS", "error", "OpenAI TTS: ${response.code} ${response.message}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "OpenAI TTS exception: ${e.message}", e)
            EngineManager.logToFlutter("TTS", "error", "OpenAI TTS exception: ${e.message}")
            null
        }
    }

    // ==================== OpenAI TTS Streaming (PCM → AudioTrack) ====================

    /**
     * Streaming synthesis: запрашиваем PCM у OpenAI и играем через AudioTrack.
     * Первый звук через ~100-200ms (vs 2-3s для full-buffer).
     *
     * @return true если streaming успешно проигран, false если ошибка (fallback на обычный путь)
     */
    private suspend fun synthesizeAndPlayStreaming(text: String, apiKey: String, voiceName: String = "onyx", cacheKey: String? = null): Boolean {
        return try {
            val escapedText = text.replace("\\", "\\\\").replace("\"", "\\\"")
            val json = """
                {
                    "model": "$OPENAI_TTS_MODEL",
                    "input": "$escapedText",
                    "voice": "$voiceName",
                    "response_format": "wav",
                    "speed": 1.0
                }
            """.trimIndent()

            val request = Request.Builder()
                .url(OPENAI_TTS_URL)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(json.toRequestBody("application/json".toMediaTypeOrNull()))
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.e(TAG, "Streaming TTS failed: ${response.code}")
                return false
            }

            val inputStream = response.body?.byteStream()
            if (inputStream == null) {
                Log.e(TAG, "Streaming TTS: no body stream")
                return false
            }

            // Создаём AudioTrack для PCM streaming
            val bufferSize = AudioTrack.getMinBufferSize(
                PCM_SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(PCM_CHUNK_SIZE * 2)

            val audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(PCM_SAMPLE_RATE)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            synchronized(mediaPlayerLock) {
                currentAudioTrack = audioTrack
            }

            val startTime = System.currentTimeMillis()
            audioTrack.play()
            Log.d(TAG, "Streaming TTS: AudioTrack started")

            // WAV header is 44 bytes — skip it to get raw PCM
            var wavHeaderRemaining = 44

            // Читаем PCM-чанки из network и пишем в AudioTrack
            val buffer = ByteArray(PCM_CHUNK_SIZE)
            var totalBytes = 0
            var firstChunkTime = -1L
            // Collect PCM bytes for caching
            val pcmCollector = if (cacheKey != null) java.io.ByteArrayOutputStream() else null

            inputStream.use { input ->
                while (true) {
                    // Проверяем остановку
                    if (!isSpeaking) {
                        Log.d(TAG, "Streaming TTS: stopped by user")
                        break
                    }

                    val read = input.read(buffer)
                    if (read == -1) break
                    if (read == 0) continue

                    // Skip WAV header
                    if (wavHeaderRemaining > 0) {
                        val skip = minOf(read, wavHeaderRemaining)
                        wavHeaderRemaining -= skip
                        if (skip >= read) continue
                        // Shift remaining data after header
                        val pcmLen = read - skip
                        System.arraycopy(buffer, skip, buffer, 0, pcmLen)
                        audioTrack.write(buffer, 0, pcmLen)
                        totalBytes += pcmLen
                        pcmCollector?.write(buffer, 0, pcmLen)
                        firstChunkTime = System.currentTimeMillis()
                        continue
                    }

                    if (firstChunkTime == -1L) {
                        firstChunkTime = System.currentTimeMillis()
                        Log.d(TAG, "Streaming TTS: first chunk in ${firstChunkTime - startTime}ms")
                    }

                    audioTrack.write(buffer, 0, read)
                    totalBytes += read
                    pcmCollector?.write(buffer, 0, read)
                }
            }

            // Ждём пока AudioTrack доиграет
            audioTrack.stop()
            audioTrack.release()

            synchronized(mediaPlayerLock) {
                currentAudioTrack = null
            }

            // Cache PCM data for instant replay
            if (pcmCollector != null && cacheKey != null && isSpeaking) {
                val pcmData = pcmCollector.toByteArray()
                if (pcmData.isNotEmpty()) {
                    synchronized(ttsCache) { ttsCache[cacheKey] = pcmData }
                    Log.d(TAG, "TTS Cache: stored '$text' (${pcmData.size} bytes)")
                }
            }

            val elapsed = System.currentTimeMillis() - startTime
            Log.d(TAG, "Streaming TTS: done — $totalBytes bytes, ${elapsed}ms total, first chunk at ${firstChunkTime - startTime}ms")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Streaming TTS error: ${e.message}", e)
            synchronized(mediaPlayerLock) {
                try { currentAudioTrack?.release() } catch (_: Exception) {}
                currentAudioTrack = null
            }
            false
        }
    }

    @Volatile
    private var currentAudioTrack: AudioTrack? = null

    /**
     * Play PCM bytes directly through AudioTrack (used for TTS cache hits).
     * PCM format: 24000 Hz, 16-bit mono, little-endian.
     */
    private suspend fun playPcmBytes(pcmData: ByteArray) = withContext(Dispatchers.IO) {
        val bufferSize = AudioTrack.getMinBufferSize(
            PCM_SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(PCM_CHUNK_SIZE * 2)

        val audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(PCM_SAMPLE_RATE)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        synchronized(mediaPlayerLock) {
            currentAudioTrack = audioTrack
        }

        audioTrack.play()

        // Write in chunks
        var offset = 0
        while (offset < pcmData.size && isSpeaking) {
            val chunkSize = minOf(PCM_CHUNK_SIZE, pcmData.size - offset)
            audioTrack.write(pcmData, offset, chunkSize)
            offset += chunkSize
        }

        audioTrack.stop()
        audioTrack.release()

        synchronized(mediaPlayerLock) {
            currentAudioTrack = null
        }
    }

    // ==================== Edge TTS WebSocket ====================

    /**
     * Synthesize text to audio bytes via Edge TTS WebSocket
     * Same protocol as Flutter's edge_tts package
     */
    private suspend fun synthesizeWithEdgeTTS(text: String): ByteArray? = suspendCancellableCoroutine { continuation ->
        val connectionId = generateUuidHex()
        val requestId = generateUuidHex()
        val gec = generateSecMsGec()
        val muid = generateMuid()
        
        val url = "$WSS_URL?TrustedClientToken=$TRUSTED_CLIENT_TOKEN" +
                "&ConnectionId=$connectionId" +
                "&Sec-MS-GEC=$gec" +
                "&Sec-MS-GEC-Version=$SEC_MS_GEC_VERSION"

        val audioChunks = mutableListOf<ByteArray>()
        var completed = false
        // Guard against double resume (timeout + onClosed race)
        var resumed = false

        fun resumeOnce(value: ByteArray?) {
            if (!resumed) {
                resumed = true
                try {
                    continuation.resume(value)
                } catch (e: Exception) {
                    Log.w(TAG, "Resume failed (coroutine likely cancelled): ${e.message}")
                }
            }
        }

        val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$CHROMIUM_MAJOR.0.0.0 Safari/537.36 Edg/$CHROMIUM_MAJOR.0.0.0"

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .header("Accept-Encoding", "gzip, deflate, br, zstd")
            .header("Accept-Language", "en-US,en;q=0.9")
            .header("Pragma", "no-cache")
            .header("Cache-Control", "no-cache")
            .header("Origin", "chrome-extension://jdiccldimpdaibmpdkjnbmckianbfold")
            .header("Cookie", "muid=$muid;")
            .build()

        val webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket connected")
                
                val timestamp = generateTimestamp()
                
                // Send speech.config
                val configMsg = "X-Timestamp:$timestamp\r\n" +
                        "Content-Type:application/json; charset=utf-8\r\n" +
                        "Path:speech.config\r\n" +
                        "\r\n" +
                        """{"context":{"synthesis":{"audio":{"metadataoptions":{"sentenceBoundaryEnabled":"false","wordBoundaryEnabled":"false"},"outputFormat":"$AUDIO_FORMAT"}}}}"""
                webSocket.send(configMsg)
                
                // Send SSML
                val ssml = buildSsml(text)
                val ssmlMsg = "X-RequestId:$requestId\r\n" +
                        "Content-Type:application/ssml+xml\r\n" +
                        "X-Timestamp:${timestamp}Z\r\n" +
                        "Path:ssml\r\n" +
                        "\r\n" +
                        ssml
                webSocket.send(ssmlMsg)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                // Text messages - check for turn.end
                if (text.contains("Path:turn.end")) {
                    Log.d(TAG, "TTS turn.end received")
                    completed = true
                    webSocket.close(1000, "Done")
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                // Binary messages are audio chunks
                val data = bytes.toByteArray()
                if (data.size >= 2) {
                    val headerLen = ((data[0].toInt() and 0xFF) shl 8) or (data[1].toInt() and 0xFF)
                    if (data.size > 2 + headerLen) {
                        // Check if header contains Content-Type:audio/mpeg
                        val headerBytes = data.copyOfRange(2, 2 + headerLen)
                        val headerText = String(headerBytes, Charsets.UTF_8)
                        if (headerText.contains("Content-Type:audio/mpeg")) {
                            val audioData = data.copyOfRange(2 + headerLen, data.size)
                            audioChunks.add(audioData)
                        }
                    }
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closing: $code $reason")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closed, chunks: ${audioChunks.size}")
                val result = if (audioChunks.isNotEmpty()) {
                    audioChunks.fold(ByteArray(0)) { acc, bytes -> acc + bytes }
                } else null
                resumeOnce(result)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure: ${t.message}, response: ${response?.code}")
                EngineManager.logToFlutter("TTS", "warning", "WebSocket сбой: ${t.message}", "HTTP ${response?.code ?: "N/A"}")
                resumeOnce(null)
            }
        })

        // Timeout after 30 seconds
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            if (!completed) {
                Log.w(TAG, "TTS timeout, closing WebSocket")
                webSocket.close(1000, "Timeout")
                val result = if (audioChunks.isNotEmpty()) {
                    audioChunks.fold(ByteArray(0)) { acc, bytes -> acc + bytes }
                } else null
                resumeOnce(result)
            }
        }, 30000)
    }

    // ==================== Android TTS Fallback ====================

    /**
     * Fallback: озвучить через встроенный Android TTS.
     * Работает оффлайн, голос менее натуральный, но зато всегда работает.
     */
    private suspend fun speakWithAndroidTts(text: String) = withContext(Dispatchers.Main) {
        // Инициализировать если ещё не готов (TextToSpeech нужен main thread!)
        if (androidTts == null) {
            Log.w(TAG, "Android TTS not initialized, attempting lazy init on Main")
            initAndroidTts()  // Уже на Main thread (withContext Dispatchers.Main)
            // Подождать пока инициализируется
            delay(800)
        }
        
        if (androidTts == null || !androidTtsReady) {
            Log.e(TAG, "Android TTS NOT available — agent will be silent")
            EngineManager.logToFlutter("TTS", "error", "Android TTS тоже недоступен — агент будет молчать!")
            onTtsFinished?.invoke()
            return@withContext
        }

        Log.d(TAG, "Android TTS speaking: '$text'")
        EngineManager.logToFlutter("TTS", "info", "Android TTS озвучивает (fallback)")
        
        val status = androidTts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "edge_fallback_${System.currentTimeMillis()}")
        if (status != TextToSpeech.SUCCESS) {
            Log.e(TAG, "Android TTS speak() failed: status=$status")
            onTtsFinished?.invoke()
            return@withContext
        }
        
        // Ждём окончания через polling (Android TTS не даёт callback для завершения)
        while (androidTts?.isSpeaking == true) {
            delay(200)
        }
        
        Log.d(TAG, "Android TTS playback completed")
        onTtsFinished?.invoke()
    }

    /**
     * Build SSML with voice name in long format (same as Flutter edge_tts)
     */
    private fun buildSsml(text: String): String {
        // Long voice name format требуется Edge TTS WebSocket для корректного выбора голоса
        // "ru-RU-DmitryNeural" → "Microsoft Server Speech Text to Speech Voice (ru-RU, DmitryNeural)"
        val parts = voice.split("-", limit = 3)
        val locale = if (parts.size >= 2) "${parts[0]}-${parts[1]}" else "ru-RU"
        val name = if (parts.size >= 3) parts[2] else voice
        val longVoiceName = "Microsoft Server Speech Text to Speech Voice ($locale, $name)"
        val escaped = text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
        
        return "<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='en-US'>" +
                "<voice name='$longVoiceName'>" +
                "<prosody pitch='+50Hz' rate='+5%' volume='+0%'>" +
                escaped +
                "</prosody></voice></speak>"
    }

    // ==================== Audio Playback ====================

    private suspend fun playAudio(bytes: ByteArray) = withContext(Dispatchers.IO) {
        val file = File(context.cacheDir, "tts_response.mp3")
        FileOutputStream(file).use { it.write(bytes) }

        // Безопасно освобождаем старый плеер
        synchronized(mediaPlayerLock) {
            try { mediaPlayer?.release() } catch (_: Exception) {}
            mediaPlayer = null
        }
        
        // Ждём окончания воспроизведения
        val completionLatch = kotlinx.coroutines.CompletableDeferred<Unit>()
        
        val mp = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            setDataSource(file.absolutePath)
            setOnCompletionListener { player ->
                try { player.release() } catch (_: Exception) {}
                synchronized(mediaPlayerLock) { mediaPlayer = null }
                file.delete()
                completionLatch.complete(Unit)
            }
            setOnErrorListener { player, _, _ ->
                try { player.release() } catch (_: Exception) {}
                synchronized(mediaPlayerLock) { mediaPlayer = null }
                file.delete()
                completionLatch.complete(Unit)
                true
            }
            prepare()
            start()
        }
        
        synchronized(mediaPlayerLock) { mediaPlayer = mp }
        
        // Ждём окончания проигрывания с таймаутом
        val completed = withTimeoutOrNull(PLAYBACK_TIMEOUT_MS) {
            completionLatch.await()
        }
        if (completed == null) {
            Log.w(TAG, "Playback timeout after ${PLAYBACK_TIMEOUT_MS}ms")
            synchronized(mediaPlayerLock) {
                try { mediaPlayer?.release() } catch (_: Exception) {}
                mediaPlayer = null
            }
            file.delete()
            completionLatch.complete(Unit)
        }
        Log.d(TAG, "Playback completed")
    }

    /**
     * Release resources
     */
    fun shutdown() {
        stop()
        androidTts?.stop()
        androidTts?.shutdown()
        androidTts = null
        androidTtsReady = false
    }
}
