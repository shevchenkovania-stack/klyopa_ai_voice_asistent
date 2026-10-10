package com.aiagent.ai_voice_agent.engine.tts

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
 * TTS Engine — Edge TTS ru-RU-DmitryNeural (основной) → OpenAI TTS onyx (online-страховка, мужской)
 * → Android TTS (offline, pitch понижен). Гендер всегда мужской: женский голос не проскакивает.
 * onTtsFinished срабатывает ровно один раз при любом исходе — цикл слушания не умирает молча.
 */
class TtsEngine(private val context: Context) {
    companion object {
        private const val TAG = "TtsEngine"
        private const val DEFAULT_VOICE = "ru-RU-DmitryNeural"
        
        // Edge TTS config (основной голос)
        private const val TRUSTED_CLIENT_TOKEN = "6A5AA1D4EAFF4E9FB37E23D68491D6F4"
        private const val CHROMIUM_VERSION = "143.0.3650.75"
        private const val CHROMIUM_MAJOR = "143"
        private const val SEC_MS_GEC_VERSION = "1-$CHROMIUM_VERSION"
        private const val WSS_URL = "wss://speech.platform.bing.com/consumer/speech/synthesize/readaloud/edge/v1"
        private const val AUDIO_FORMAT = "audio-24khz-48kbitrate-mono-mp3"
        private const val WIN_EPOCH = 11644473600L
        
        // Retry config — 2 попытки Edge, потом online-страховка (OpenAI), потом Android offline
        private const val MAX_ATTEMPTS = 2
        private const val RETRY_DELAY_MS = 1000L
        private const val EDGE_BLOCKED_MS = 12_000L // circuit breaker: короткий, чтобы скорее вернуться к Dmitry

        // OpenAI TTS (online-страховка, мужской голос) — когда Edge 403, но интернет есть
        private const val OPENAI_TTS_URL = "https://api.openai.com/v1/audio/speech"
        private const val OPENAI_TTS_MODEL = "tts-1"
        private const val OPENAI_TTS_VOICE = "onyx" // мужской, низкий — держим гендер Клёпы

        // Playback timeout
        private const val PLAYBACK_TIMEOUT_MS = 60_000L // 60 секунд

        // TTS Cache config
        private const val TTS_CACHE_MAX_SIZE = 30 // max entries (LRU eviction)
    }
    
    var voice: String = DEFAULT_VOICE

    // Ключ OpenAI для online-страховки TTS (мужской голос), когда Edge 403.
    var openaiApiKey: String? = null

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

    // stop() (barge-in/cancel) помечает прерывание — speak() не вызывает onTtsFinished,
    // обработчик barge-in сам переключает состояние (иначе запись barge-in сломается).
    @Volatile
    private var pendingInterrupt = false
    
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
                    // Offline-страховка: понижаем pitch, чтобы не звучать женски (Клёпа — мальчик).
                    androidTts?.setPitch(0.7f)
                    androidTts?.setSpeechRate(0.95f)
                    val chosen = androidTts?.voice
                    Log.d(TAG, "Android TTS initialized (fallback, мужской тембр pitch=0.7): voice=${chosen?.name}")
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
     * Edge TTS (основной) → Android TTS (offline). Ровно один onTtsFinished на любой исход.
     */
    suspend fun speak(text: String) = withContext(Dispatchers.IO) {
        // Clean up previous TTS silently (no interruption callbacks — we're about to play new TTS)
        synchronized(mediaPlayerLock) {
            if (isSpeaking) {
                try {
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
            pendingInterrupt = false
        }
        onTtsStarted?.invoke()

        requestAudioFocus()

        // Ровно один fire на любой исход — цикл слушания не умрёт молча
        var finishFired = false
        fun fireFinished() {
            if (!finishFired) {
                finishFired = true
                onTtsFinished?.invoke()
            }
        }

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

            // 1) Android TTS — если voice="android", сразу используем
            if (isAndroidVoice) {
                Log.d(TAG, "TTS: Android TTS (оффлайн, безлимитный)")
                EngineManager.logToFlutter("TTS", "info", "Android TTS (оффлайн)...")
                speakWithAndroidTts(text)
                return@withContext // fireFinished() сработает в finally
            }

            // 2) TTS Cache check (мгновенное воспроизведение без сети)
            val cacheKey = "$text|$voice"
            val cached = synchronized(ttsCache) { ttsCache[cacheKey] }
            if (cached != null) {
                Log.d(TAG, "TTS Cache HIT: '$text' (${cached.size} bytes)")
                EngineManager.logToFlutter("TTS", "info", "TTS Cache (мгновенно)")
                playAudio(cached)
                return@withContext
            }

            // 3) Edge TTS — основной и единственный сетевой голос (ru-RU-DmitryNeural).
            //    OpenAI TTS убран из цепочки: его onyx менял тембр Клёпы при смене провайдера.
            if (System.currentTimeMillis() >= edgeTtsBlockedUntil) {
                var attempt = 0
                var audioBytes: ByteArray? = null
                while (attempt < MAX_ATTEMPTS && (audioBytes == null || audioBytes.isEmpty())) {
                    attempt++
                    if (attempt > 1) {
                        Log.w(TAG, "TTS: Edge retry $attempt/$MAX_ATTEMPTS...")
                        EngineManager.logToFlutter("TTS", "warning", "Edge TTS: попытка $attempt/$MAX_ATTEMPTS...")
                        delay(RETRY_DELAY_MS)
                    }
                    audioBytes = synthesizeWithEdgeTTS(text)
                }
                if (audioBytes != null && audioBytes.isNotEmpty()) {
                    edgeTtsBlockedUntil = 0L
                    if (isSpeaking) synchronized(ttsCache) { ttsCache[cacheKey] = audioBytes }
                    Log.d(TAG, "✅ TTS Edge success: ${audioBytes.size} bytes (голос=$voice)")
                    EngineManager.logToFlutter("TTS", "success", "✅ OK (голос=$voice, ${audioBytes.size} байт)")
                    playAudio(audioBytes)
                    return@withContext
                }
                edgeTtsBlockedUntil = System.currentTimeMillis() + EDGE_BLOCKED_MS
                Log.w(TAG, "TTS: Edge TTS failed, circuit breaker ${EDGE_BLOCKED_MS / 1000}s")
            } else {
                Log.d(TAG, "TTS: Edge TTS blocked (circuit breaker, ${((edgeTtsBlockedUntil - System.currentTimeMillis()) / 1000)}s left)")
            }

            // 4) OpenAI TTS — online-страховка с мужским голосом (когда Edge 403/заблокирован,
            //    но интернет есть). Не даём провалиться в женский Android-голос.
            if (!openaiApiKey.isNullOrBlank()) {
                val oa = synthesizeWithOpenAI(text)
                if (oa != null && oa.isNotEmpty()) {
                    if (isSpeaking) synchronized(ttsCache) { ttsCache[cacheKey] = oa }
                    Log.d(TAG, "✅ TTS OpenAI fallback success: ${oa.size} bytes (voice=$OPENAI_TTS_VOICE, мужской)")
                    EngineManager.logToFlutter("TTS", "warning", "⚠️ Edge недоступен → OpenAI (мужской, $OPENAI_TTS_VOICE)")
                    playAudio(oa)
                    return@withContext
                }
            }

            // 5) Android TTS — последняя offline-страховка (голос другой, но молчание хуже)
            Log.w(TAG, "TTS: онлайн недоступен, пробую Android TTS...")
            EngineManager.logToFlutter("TTS", "warning", "Android TTS (offline fallback)...")
            speakWithAndroidTts(text)
        } catch (e: CancellationException) {
            // Coroutine cancelled (barge-in/cancel pipeline) — exit silently.
            // stop() уже пометил прерывание; fire в finally будет пропущен.
            Log.d(TAG, "TTS cancelled (silent exit)")
            synchronized(mediaPlayerLock) { isSpeaking = false }
            throw e // Re-throw so withContext propagates cancellation properly
        } catch (e: Exception) {
            Log.e(TAG, "❌ TTS EXCEPTION: ${e.message}", e)
            EngineManager.logToFlutter("TTS", "error", "❌ EXCEPTION: ${e.message}")
        } finally {
            synchronized(mediaPlayerLock) { isSpeaking = false }
            if (pendingInterrupt) {
                // Прерывание через stop() — слушание переключает обработчик barge-in/cancel
                pendingInterrupt = false
                Log.d(TAG, "TTS interrupted — finished fired by interrupt handler")
            } else {
                fireFinished()
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
            pendingInterrupt = true
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

    /**
     * Online-страховка: синтез через OpenAI TTS (мужской голос onyx).
     * Возвращает mp3-байты или null при любой ошибке/отсутствии ключа.
     */
    private suspend fun synthesizeWithOpenAI(text: String): ByteArray? {
        val key = openaiApiKey
        if (key.isNullOrBlank()) return null
        return try {
            val body = org.json.JSONObject()
                .put("model", OPENAI_TTS_MODEL)
                .put("input", text)
                .put("voice", OPENAI_TTS_VOICE)
                .put("response_format", "mp3")
                .put("speed", 1.0)
                .toString()
            val req = Request.Builder()
                .url(OPENAI_TTS_URL)
                .header("Authorization", "Bearer $key")
                .header("Content-Type", "application/json")
                .post(body.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull()))
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    Log.w(TAG, "OpenAI TTS HTTP ${resp.code}")
                    return null
                }
                val bytes = resp.body?.bytes()
                if (bytes != null && bytes.isNotEmpty()) bytes else null
            }
        } catch (e: Exception) {
            Log.w(TAG, "OpenAI TTS error: ${e.message}")
            null
        }
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
            return@withContext // onTtsFinished вызовет finally в speak()
        }

        Log.d(TAG, "Android TTS speaking: '$text'")
        EngineManager.logToFlutter("TTS", "info", "Android TTS озвучивает (fallback)")
        
        val status = androidTts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "edge_fallback_${System.currentTimeMillis()}")
        if (status != TextToSpeech.SUCCESS) {
            Log.e(TAG, "Android TTS speak() failed: status=$status")
            return@withContext
        }
        
        // Ждём окончания через polling (Android TTS не даёт callback для завершения),
        // с дедлайном — зависший TTS не должен глушить цикл слушания.
        val deadline = System.currentTimeMillis() + PLAYBACK_TIMEOUT_MS
        while (androidTts?.isSpeaking == true && System.currentTimeMillis() < deadline) {
            delay(200)
        }
        
        Log.d(TAG, "Android TTS playback completed")
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
        
        // Ждём окончания: естественное завершение, stop() (isSpeaking=false) или timeout
        val completed = withTimeoutOrNull(PLAYBACK_TIMEOUT_MS) {
            while (isSpeaking && !completionLatch.isCompleted) {
                delay(100)
            }
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
