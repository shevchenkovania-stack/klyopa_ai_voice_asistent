package com.aiagent.ai_voice_agent.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import com.aiagent.ai_voice_agent.MainActivity
import com.aiagent.ai_voice_agent.engine.wakeword.VoskWakeWordDetector

/**
 * WakeWordService — "тупой" процессор.
 *
 * НЕ управляет микрофоном. НЕ открывает AudioRecord.
 * Только:
 * - Держит Vosk модель в памяти
 * - Принимает PCM через feedAudioBuffer()
 * - Уведомляет о wake word через onWakeWordDetected
 * - Будит экран при обнаружении
 *
 * Микрофон — в AudioSessionManager.
 */
class WakeWordService : Service() {
    companion object {
        const val TAG = "WakeWordService"
        const val CHANNEL_ID = "wake_word_channel"
        const val NOTIFICATION_ID = 1
        const val ACTION_START = "start"
        const val ACTION_STOP = "stop"
        const val ACTION_SET_WAKE_WORD = "set_wake_word"
        const val EXTRA_WAKE_WORD = "wake_word"
        const val PREFS_NAME = "wakeup_prefs"
        const val PREF_WAKE_WORD = "wake_word"
        const val DEFAULT_WAKE_WORD = "клёпа"

        var isRunning = false
            private set
        var isReady = false
            private set

        var onWakeWordDetected: ((String) -> Unit)? = null
        var onServiceReadyChanged: ((Boolean) -> Unit)? = null

        private var _instance: WakeWordService? = null
        fun resetTrigger() { _instance?.resetWakeWordTrigger() }
        private var diagNullCount = 0L
        fun feedAudioBuffer(buffer: ShortArray, read: Int) {
            if (_instance == null) {
                diagNullCount++
                if (diagNullCount % 200 == 1L) {
                    android.util.Log.d("WakeWordService", "[DIAG] feedAudioBuffer: _instance=NULL (dropped $diagNullCount buffers)")
                }
            }
            _instance?.feedAudioImpl(buffer, read)
        }
    }

    private fun resetWakeWordTrigger() { detector?.resetTrigger() }

    @Volatile
    private var detector: VoskWakeWordDetector? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var prefs: SharedPreferences

    private var cooldownUntil = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        // Guard against duplicate initialization (system restart, double startService)
        if (_instance != null) {
            Log.w(TAG, "[INIT] Already running — skipping duplicate onCreate (PID=${android.os.Process.myPid()})")
            return
        }
        _instance = this
        isRunning = true
        Log.i(TAG, "[INIT] onCreate (PID=${android.os.Process.myPid()})")
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        detector = VoskWakeWordDetector(this).apply {
            onWakeWordDetected = { text -> handleWakeWordDetected(text) }
            onPartialResult = { /* optional logging */ }
        }
        Thread {
            Log.i(TAG, "[INIT] Loading Vosk model...")
            val ok = detector?.load() ?: false
            if (ok) {
                val wakeWord = prefs.getString(PREF_WAKE_WORD, DEFAULT_WAKE_WORD) ?: DEFAULT_WAKE_WORD
                detector?.setWakeWord(wakeWord)
                isReady = true
                Log.i(TAG, "[INIT] Wake word set to: '$wakeWord'")
            } else {
                Log.e(TAG, "[INIT] Vosk model load FAILED")
                isReady = false
            }
            mainHandler.post { onServiceReadyChanged?.invoke(isReady) }
        }.apply { name = "vosk-init" }.start()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                // Сервис живёт, держит Vosk в памяти.
                // Микрофон — в AudioSessionManager.
                createNotificationChannel()
                try {
                    startForeground(NOTIFICATION_ID, createNotification())
                } catch (e: Exception) {
                    Log.e(TAG, "[AUDIO] startForeground FAILED: ${e.message}", e)
                }
            }
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            ACTION_SET_WAKE_WORD -> {
                val newWord = intent.getStringExtra(EXTRA_WAKE_WORD)
                if (!newWord.isNullOrEmpty()) {
                    setWakeWord(newWord)
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        detector?.release()
        detector = null
        isReady = false
        isRunning = false
        _instance = null
        super.onDestroy()
    }

    // ===== "ТУПОЙ" ИНТЕРФЕЙС =====

    /**
     * ЕДИНСТВЕННЫЙ метод для подачи аудио.
     * Вызывается из AudioSessionManager через WakeWordAudioConsumer.
     * Просто передаёт PCM в Vosk. Ничего больше не делает.
     */
    private var audioFrameCount = 0L
    private fun feedAudioImpl(buffer: ShortArray, read: Int) {
        if (System.currentTimeMillis() < cooldownUntil) return
        audioFrameCount++
        if (audioFrameCount % 100 == 1L) {
            Log.d(TAG, "[DIAG] feedAudio: frame#$audioFrameCount, read=$read, detector=${if (detector != null) "OK" else "NULL"}")
        }
        detector?.feedAudio(buffer, read)
    }

    // ===== INTERNAL =====

    private fun setWakeWord(name: String) {
        val normalized = name.lowercase().trim()
        prefs.edit().putString(PREF_WAKE_WORD, normalized).apply()
        detector?.setWakeWord(normalized)
        Log.d(TAG, "[CONFIG] Wake word updated to: '$normalized'")
    }

    private fun handleWakeWordDetected(text: String) {
        if (System.currentTimeMillis() < cooldownUntil) return
        cooldownUntil = System.currentTimeMillis() + 3000

        Log.i(TAG, "[WAKE] ========== WAKE WORD DETECTED: '$text' ==========")

        // 1. Будим экран (WakeLock без release — сам отпустится через 10с)
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            val wakeLock = pm.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "WakeWordService:wakeLock"
            )
            wakeLock.acquire(10_000)
        } catch (e: Exception) {
            Log.e(TAG, "[WAKE] WakeLock error: ${e.message}")
        }

        // 2. Пытаемся поднять Activity
        try {
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(MainActivity.EXTRA_WAKE_TRIGGERED, true)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "[WAKE] startActivity error: ${e.message}")
        }

        // 3. Full-screen notification — fallback для Android 10+
        showWakeNotification()

        // 4. Запускаем pipeline (AudioSessionManager сам переключит владельца)
        mainHandler.post {
            onWakeWordDetected?.invoke(text)
        }
    }

    private fun showWakeNotification() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val alertChannel = NotificationChannel(
                    "wake_alert_channel",
                    "Wake Word Alert",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Alert when wake word is detected"
                    enableVibration(true)
                }
                val manager = getSystemService(NotificationManager::class.java)
                manager.createNotificationChannel(alertChannel)
            }

            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(MainActivity.EXTRA_WAKE_TRIGGERED, true)
            }
            val pendingIntent = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = Notification.Builder(this, "wake_alert_channel")
                .setContentTitle("Клёпа")
                .setContentText("Слушаю команду...")
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setFullScreenIntent(pendingIntent, true)
                .setPriority(Notification.PRIORITY_HIGH)
                .setCategory(Notification.CATEGORY_CALL)
                .setAutoCancel(true)
                .build()

            val manager = getSystemService(NotificationManager::class.java)
            manager.notify(NOTIFICATION_ID + 1, notification)
        } catch (e: Exception) {
            Log.e(TAG, "[WAKE] Notification error: ${e.message}")
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Wake Word Detection",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Continuous listening for wake word"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val wakeWord = prefs.getString(PREF_WAKE_WORD, DEFAULT_WAKE_WORD) ?: DEFAULT_WAKE_WORD
        val displayName = wakeWord.replaceFirstChar { it.uppercase() }

        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("Клёпа в фоне")
                .setContentText("Слушаю \"$displayName\"...")
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("Клёпа в фоне")
                .setContentText("Слушаю \"$displayName\"...")
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build()
        }
    }
}
