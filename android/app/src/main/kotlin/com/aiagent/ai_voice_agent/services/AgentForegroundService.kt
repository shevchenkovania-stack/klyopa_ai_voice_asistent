package com.aiagent.ai_voice_agent.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.aiagent.ai_voice_agent.MainActivity
import com.aiagent.ai_voice_agent.engine.EngineManager

/**
 * Foreground service — держит нативный голосовой движок Клёпы живым в фоне.
 *
 * Голосовой конвейер (запись/STT/агент/TTS) уже нативный и живёт в синглтоне EngineManager
 * на applicationContext. Этот сервис лишь удерживает процесс от убийства системой и
 * даёт легальный доступ к микрофону в фоне (тип microphone на Android 14+).
 *
 * Wake-word НЕ реализуется здесь — только фоновая живучесть.
 */
class AgentForegroundService : Service() {

    companion object {
        private const val TAG = "AgentFgService"

        const val CHANNEL_ID = "agent_foreground"
        const val NOTIF_ID = 4201

        const val ACTION_START = "com.aiagent.ai_voice_agent.action.FG_START"
        const val ACTION_STOP = "com.aiagent.ai_voice_agent.action.FG_STOP"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                Log.d(TAG, "ACTION_STOP — останавливаю сессию и сервис")
                try {
                    if (EngineManager.isInitialized) {
                        EngineManager.stopContinuousSession()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Ошибка остановки сессии: ${e.message}")
                }
                stopForegroundCompat()
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                Log.d(TAG, "ACTION_START — поднимаю foreground и запускаю сессию")
                startForegroundCompat()
                try {
                    if (!EngineManager.isInitialized) {
                        EngineManager.initialize(applicationContext)
                    }
                    EngineManager.startContinuousSession()
                } catch (e: Exception) {
                    Log.e(TAG, "Ошибка запуска сессии: ${e.message}")
                }
                return START_STICKY
            }
        }
    }

    private fun startForegroundCompat() {
        ensureChannel()
        val notification = buildNotification()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIF_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                )
            } else {
                startForeground(NOTIF_ID, notification)
            }
        } catch (e: Exception) {
            // На некоторых прошивках startForeground с типом может бросить — fallback без типа
            Log.e(TAG, "startForeground с типом упал, fallback: ${e.message}")
            try {
                startForeground(NOTIF_ID, notification)
            } catch (e2: Exception) {
                Log.e(TAG, "startForeground fallback тоже упал: ${e2.message}")
            }
        }
    }

    private fun stopForegroundCompat() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        } catch (e: Exception) {
            Log.e(TAG, "stopForeground error: ${e.message}")
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Клёпа на связи",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Клёпа слушает и работает в фоне"
                    setShowBadge(false)
                    enableVibration(false)
                    setSound(null, null)
                }
                nm.createNotificationChannel(channel)
            }
        }
    }

    private fun buildNotification(): Notification {
        // Тап по уведомлению — открыть приложение
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val piFlags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        val openPi = PendingIntent.getActivity(this, 0, openIntent, piFlags)

        // Кнопка «Стоп» — остановить сессию и сервис
        val stopIntent = Intent(this, AgentForegroundService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPi = PendingIntent.getService(this, 1, stopIntent, piFlags)

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this).setPriority(Notification.PRIORITY_LOW)
        }

        return builder
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Клёпа на связи")
            .setContentText("Слушаю и работаю в фоне")
            .setContentIntent(openPi)
            .setOngoing(true)
            .addAction(
                Notification.Action.Builder(
                    android.R.drawable.ic_media_pause,
                    "Стоп",
                    stopPi
                ).build()
            )
            .build()
    }
}
