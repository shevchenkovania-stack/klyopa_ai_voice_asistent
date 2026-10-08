package com.aiagent.ai_voice_agent.services

import android.app.Notification
import android.content.Context
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * NotificationListenerService — agent can read and interact with notifications.
 */
class AgentNotificationListener : NotificationListenerService() {

    companion object {
        private const val TAG = "NotificationListener"

        var instance: AgentNotificationListener? = null
            private set

        // Cache recent notifications
        private val recentNotifications = mutableListOf<NotificationInfo>()
        private const val MAX_CACHED = 50

        fun isServiceRunning(): Boolean = instance != null

        /**
         * Get list of active notifications as formatted string.
         */
        fun getActiveNotifications(context: Context): String {
            val svc = instance
            if (svc == null) {
                return "NotificationListener не запущен. Включи его в настройках уведомлений."
            }

            return try {
                val sbns = svc.activeNotifications ?: return "Нет активных уведомлений"
                if (sbns.isEmpty()) return "Нет активных уведомлений"

                val infos = sbns.mapNotNull { sbn ->
                    NotificationInfo.from(sbn)
                }

                if (infos.isEmpty()) return "Нет читаемых уведомлений"

                infos.joinToString("\n\n") { info ->
                    buildString {
                        appendLine("📱 ${info.appName}")
                        appendLine("  ${info.title}")
                        if (info.text.isNotEmpty()) appendLine("  ${info.text}")
                        if (info.isOngoing) append("  [постоянное]")
                    }
                }
            } catch (e: Exception) {
                "Ошибка чтения уведомлений: ${e.message}"
            }
        }

        /**
         * Get notifications as JSON for agent processing.
         */
        fun getActiveNotificationsJson(): String {
            val svc = instance ?: return "[]"
            val sbns = svc.activeNotifications ?: return "[]"

            val arr = JSONArray()
            for (sbn in sbns) {
                val info = NotificationInfo.from(sbn) ?: continue
                arr.put(JSONObject().apply {
                    put("app", info.appName)
                    put("title", info.title)
                    put("text", info.text)
                    put("package", info.packageName)
                    put("ongoing", info.isOngoing)
                    put("id", info.id)
                })
            }
            return arr.toString()
        }

        /**
         * Dismiss a notification by package and tag.
         */
        fun dismissNotification(packageName: String, tag: String, id: Int): Boolean {
            val svc = instance ?: return false
            return try {
                svc.cancelNotification(packageName, tag, id)
                true
            } catch (e: Exception) {
                Log.e(TAG, "dismissNotification error: ${e.message}")
                false
            }
        }
    }

    data class NotificationInfo(
        val appName: String,
        val title: String,
        val text: String,
        val packageName: String,
        val isOngoing: Boolean,
        val id: Int,
        val tag: String,
        val timestamp: Long
    ) {
        companion object {
            fun from(sbn: StatusBarNotification): NotificationInfo? {
                val notification = sbn.notification ?: return null
                val extras = notification.extras

                val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
                val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
                val appName = try {
                    sbn.packageName.substringAfterLast('.')
                } catch (e: Exception) {
                    sbn.packageName
                }

                return NotificationInfo(
                    appName = appName,
                    title = title,
                    text = text,
                    packageName = sbn.packageName,
                    isOngoing = notification.flags and Notification.FLAG_ONGOING_EVENT != 0,
                    id = sbn.id,
                    tag = sbn.tag ?: "",
                    timestamp = sbn.postTime
                )
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.d(TAG, "NotificationListener created")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        val info = NotificationInfo.from(sbn) ?: return
        recentNotifications.add(0, info)
        if (recentNotifications.size > MAX_CACHED) {
            recentNotifications.removeAt(recentNotifications.size - 1)
        }
        Log.d(TAG, "Notification posted: ${info.appName} - ${info.title}")
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        sbn ?: return
        Log.d(TAG, "Notification removed: ${sbn.packageName}")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        Log.d(TAG, "NotificationListener destroyed")
    }
}
