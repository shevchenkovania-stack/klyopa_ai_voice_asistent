package com.aiagent.ai_voice_agent

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.aiagent.ai_voice_agent.helpers.WakeWordListener
import com.aiagent.ai_voice_agent.engine.EngineManager
import io.flutter.plugin.common.MethodChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AgentForegroundService : Service() {
    
    private var wakeWordListener: WakeWordListener? = null
    private var wakeWord: String = ""
    private var wakeWordTriggered = false  // Prevent multiple triggers per session
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onCreate() {
        super.onCreate()
        instance = this
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val title = intent?.getStringExtra("title") ?: "AI Agent"
        val content = intent?.getStringExtra("content") ?: "Слушаю..."
        wakeWord = intent?.getStringExtra("wakeWord") ?: ""
        
        startForegroundNotification(title, content)
        
        // Start wake word detection in foreground service (works in background!)
        if (wakeWord.isNotEmpty()) {
            startWakeWordDetection()
        }
        
        return START_STICKY
    }
    
    private fun startWakeWordDetection() {
        android.util.Log.d("WakeWordService", "[START] Starting wake word detection for: '$wakeWord'")
        stopWakeWordDetection()
        wakeWordTriggered = false
        
        wakeWordListener = WakeWordListener(this).apply {
            setWakeWord(wakeWord)
            onWakeWordMatched = {
                if (!wakeWordTriggered) {
                    wakeWordTriggered = true
                    onWakeWordDetected()
                }
            }
            onPartialResult = { text ->
                android.util.Log.d("WakeWordService", "[CHECK] '$text'")
            }
            onError = { error ->
                android.util.Log.e("WakeWordService", "[LISTENER_ERROR] $error")
            }
            startListening()
        }
    }
    
    private fun stopWakeWordDetection() {
        android.util.Log.d("WakeWordService", "[STOP] Stopping wake word detection")
        wakeWordListener?.stopListening()
        wakeWordListener = null
    }
    
    private fun onWakeWordDetected() {
        android.util.Log.d("WakeWordService", "[WAKE] ========== onWakeWordDetected() CALLED ==========")
        android.util.Log.d("WakeWordService", "[WAKE] wakeWord='$wakeWord', engineNull=${MainActivity.engineInstance == null}")
        
        // Stop listening while processing
        stopWakeWordDetection()
        
        // Wake up screen
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "WakeWord:wakeLock"
        )
        wakeLock.acquire(10000)
        android.util.Log.d("WakeWordService", "[WAKE] WakeLock acquired")
        
        // === PRIMARY: AlarmManager.setAlarmClock() ===
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 42, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerTime = System.currentTimeMillis() + 200
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmInfo = android.app.AlarmManager.AlarmClockInfo(triggerTime, pendingIntent)
            alarmManager.setAlarmClock(alarmInfo, pendingIntent)
            android.util.Log.d("WakeWordService", "[WAKE] setAlarmClock() SUCCEEDED (Android 12+)")
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            android.util.Log.d("WakeWordService", "[WAKE] setExactAndAllowWhileIdle() SUCCEEDED (pre-12)")
        }
        
        // === NOTIFY DART ===
        try {
            val engine = MainActivity.engineInstance
            if (engine != null) {
                MethodChannel(engine.dartExecutor.binaryMessenger, "com.aiagent.ai_voice_agent/wakeword")
                    .invokeMethod("onWakeWordDetected", null)
                android.util.Log.d("WakeWordService", "[WAKE] Dart callback invoked")
            } else {
                android.util.Log.e("WakeWordService", "[WAKE] FlutterEngine is NULL")
            }
        } catch (e: Exception) {
            android.util.Log.e("WakeWordService", "[WAKE] Dart callback FAILED: ${e.message}")
        }
        
        // === BACKGROUND PROCESSING via Kotlin engine ===
        // Record audio and process through engine while app comes to foreground
        if (EngineManager.isInitialized) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    android.util.Log.d("WakeWordService", "[BG] Starting background recording...")
                    val audioFile = recordAudio()
                    if (audioFile != null) {
                        android.util.Log.d("WakeWordService", "[BG] Audio recorded: ${audioFile.absolutePath}")
                        val response = EngineManager.processCommand(audioFile)
                        android.util.Log.d("WakeWordService", "[BG] Engine response: $response")
                        
                        // Notify Dart about the response
                        try {
                            val dartEngine = MainActivity.engineInstance
                            if (dartEngine != null) {
                                MethodChannel(dartEngine.dartExecutor.binaryMessenger, "com.aiagent.ai_voice_agent/wakeword")
                                    .invokeMethod("onBackgroundResponse", response)
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("WakeWordService", "[BG] Dart notify failed: ${e.message}")
                        }
                    } else {
                        android.util.Log.e("WakeWordService", "[BG] Audio recording failed")
                    }
                } catch (e: Exception) {
                    android.util.Log.e("WakeWordService", "[BG] Processing error: ${e.message}")
                } finally {
                    // Restart wake word detection after processing
                    android.os.Handler(mainLooper).postDelayed({
                        startWakeWordDetection()
                    }, 2000)
                }
            }
        }
        
        // Release wakeLock after delay
        android.os.Handler(mainLooper).postDelayed({
            if (wakeLock.isHeld) wakeLock.release()
            android.util.Log.d("WakeWordService", "[WAKE] WakeLock released")
        }, 10000)
        
        android.util.Log.d("WakeWordService", "[WAKE] ========== onWakeWordDetected() DONE ==========")
    }
    
    /** Record audio from microphone (3 seconds max or silence) */
    private fun recordAudio(): java.io.File? {
        return try {
            val sampleRate = 16000
            val channelConfig = AudioFormat.CHANNEL_IN_MONO
            val audioFormat = AudioFormat.ENCODING_PCM_16BIT
            val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
            
            if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
                android.util.Log.e("WakeWordService", "[REC] Invalid buffer size")
                return null
            }
            
            val audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )
            
            val audioFile = java.io.File(cacheDir, "wake_command.wav")
            val buffer = ShortArray(bufferSize / 2)
            val allSamples = mutableListOf<Short>()
            var silenceCount = 0
            val maxSilenceFrames = 30 // ~1.5 seconds of silence
            val maxFrames = 16000 * 5 / (bufferSize / 2) // max 5 seconds
            
            audioRecord.startRecording()
            var framesRead = 0
            
            while (framesRead < maxFrames) {
                val read = audioRecord.read(buffer, 0, buffer.size)
                if (read > 0) {
                    allSamples.addAll(buffer.take(read).toList())
                    
                    // Check silence
                    val avgAmplitude = buffer.take(read).map { kotlin.math.abs(it.toInt()) }.average()
                    if (avgAmplitude < 100) {
                        silenceCount++
                        if (silenceCount > maxSilenceFrames && framesRead > 16) break
                    } else {
                        silenceCount = 0
                    }
                    framesRead++
                } else {
                    break
                }
            }
            
            audioRecord.stop()
            audioRecord.release()
            
            if (allSamples.isEmpty()) {
                android.util.Log.e("WakeWordService", "[REC] No audio samples recorded")
                return null
            }
            
            // Write WAV file
            writeWav(audioFile, allSamples.toShortArray(), sampleRate)
            android.util.Log.d("WakeWordService", "[REC] Recorded ${allSamples.size} samples")
            audioFile
        } catch (e: Exception) {
            android.util.Log.e("WakeWordService", "[REC] Error: ${e.message}")
            null
        }
    }
    
    /** Write PCM samples to WAV file */
    private fun writeWav(file: java.io.File, samples: ShortArray, sampleRate: Int) {
        val dataSize = samples.size * 2
        val outputStream = file.outputStream()
        
        // WAV header
        outputStream.write("RIFF".toByteArray())
        outputStream.write(intToByteArray(36 + dataSize))
        outputStream.write("WAVE".toByteArray())
        outputStream.write("fmt ".toByteArray())
        outputStream.write(intToByteArray(16)) // chunk size
        outputStream.write(shortToByteArray(1)) // PCM format
        outputStream.write(shortToByteArray(1)) // mono
        outputStream.write(intToByteArray(sampleRate))
        outputStream.write(intToByteArray(sampleRate * 2)) // byte rate
        outputStream.write(shortToByteArray(2)) // block align
        outputStream.write(shortToByteArray(16)) // bits per sample
        outputStream.write("data".toByteArray())
        outputStream.write(intToByteArray(dataSize))
        
        // Write samples
        for (sample in samples) {
            outputStream.write(shortToByteArray(sample))
        }
        outputStream.close()
    }
    
    private fun intToByteArray(value: Int): ByteArray = byteArrayOf(
        (value and 0xFF).toByte(),
        (value shr 8 and 0xFF).toByte(),
        (value shr 16 and 0xFF).toByte(),
        (value shr 24 and 0xFF).toByte()
    )
    
    private fun shortToByteArray(value: Short): ByteArray = byteArrayOf(
        (value.toInt() and 0xFF).toByte(),
        (value.toInt() shr 8 and 0xFF).toByte()
    )
    
    private fun startForegroundNotification(title: String, content: String) {
        // Create notification channel
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "AI Voice Agent",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Фоновый голосовой помощник"
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
        
        // Build notification
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        
        startForeground(NOTIFICATION_ID, notification)
    }
    
    override fun onDestroy() {
        stopWakeWordDetection()
        instance = null
        stopForeground(true)
        super.onDestroy()
    }
    
    companion object {
        const val CHANNEL_ID = "ai_voice_agent_channel"
        const val ALERT_CHANNEL_ID = "ai_voice_agent_alert"
        const val NOTIFICATION_ID = 888
        const val WAKE_ALERT_NOTIFICATION_ID = 889
        
        // Static reference to running service instance
        var instance: AgentForegroundService? = null
            private set
        
        /// Restart wake word detection after command processing
        fun restartWakeWord() {
            instance?.let { service ->
                android.util.Log.d("WakeWordService", "Restarting wake word detection")
                service.startWakeWordDetection()
            }
        }
        
        /// Stop wake word detection (to free mic for app recording)
        fun stopWakeWord() {
            instance?.let { service ->
                android.util.Log.d("WakeWordService", "Stopping wake word detection (mic requested by app)")
                service.stopWakeWordDetection()
            }
        }
        
        fun start(context: Context, title: String, content: String, wakeWord: String = "") {
            val intent = Intent(context, AgentForegroundService::class.java).apply {
                putExtra("title", title)
                putExtra("content", content)
                putExtra("wakeWord", wakeWord)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        
        fun stop(context: Context) {
            context.stopService(Intent(context, AgentForegroundService::class.java))
        }
    }
}
