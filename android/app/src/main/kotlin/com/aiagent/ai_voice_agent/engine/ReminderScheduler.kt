/**
 * ReminderScheduler — планировщик push-напоминаний через AlarmManager.
 *
 * Архитектура:
 *  1) SetReminderTool вызывает ReminderScheduler.schedule(...)
 *  2) Scheduler записывает напоминание в SharedPreferences (чтобы пережить reboot)
 *     и ставит exact alarm через AlarmManager.setExactAndAllowWhileIdle().
 *  3) В момент срабатывания AlarmManager будит ReminderReceiver.
 *  4) Receiver показывает системное push-уведомление через NotificationManager.
 *  5) После reboot — BootReceiver восстанавливает все сохранённые напоминания.
 */
package com.aiagent.ai_voice_agent.engine

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.SystemClock
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * Одно напоминание: id, заголовок, время срабатывания (мс).
 */
data class Reminder(val id: Int, val title: String, val triggerAtMs: Long)

/**
 * Планировщик: ставит/снимает/восстанавливает напоминания.
 */
object ReminderScheduler {
    private const val TAG = "ReminderScheduler"
    private const val PREF_NAME = "klyopa_reminders"
    private const val KEY_LIST = "reminders"
    private const val ACTION_FIRE = "com.aiagent.ai_voice_agent.REMINDER_FIRE"
    const val EXTRA_REMINDER_ID = "reminder_id"
    const val EXTRA_REMINDER_TITLE = "reminder_title"
    const val CHANNEL_ID = "klyopa_reminders"

    /**
     * Поставить напоминание.
     * @return id напоминания (для возможной отмены).
     */
    fun schedule(ctx: Context, title: String, triggerAtMs: Long): Int {
        val context = ctx.applicationContext
        val prefs = prefs(context)
        val list = load(prefs).toMutableList()

        // Уникальный ID
        val id = (list.maxOfOrNull { it.id } ?: 1000) + 1
        val reminder = Reminder(id, title, triggerAtMs)
        list.add(reminder)
        save(prefs, list)

        setAlarm(context, reminder)
        Log.d(TAG, "Запланировано #$id: '$title' на ${java.text.SimpleDateFormat("d MMM HH:mm", java.util.Locale.getDefault()).format(triggerAtMs)}")
        return id
    }

    /**
     * Отменить напоминание по id.
     */
    fun cancel(ctx: Context, id: Int) {
        val context = ctx.applicationContext
        val prefs = prefs(context)
        val list = load(prefs).toMutableList()
        list.removeAll { it.id == id }
        save(prefs, list)
        cancelAlarm(context, id)
        Log.d(TAG, "Отменено #$id")
    }

    /**
     * Восстановить все напоминания после reboot (вызывается из BootReceiver).
     */
    fun rescheduleAll(ctx: Context) {
        val context = ctx.applicationContext
        val list = load(prefs(context))
        val now = System.currentTimeMillis()
        var restored = 0
        for (r in list) {
            if (r.triggerAtMs > now) {
                setAlarm(context, r)
                restored++
            }
        }
        Log.d(TAG, "Восстановлено напоминаний после reboot: $restored из ${list.size}")
    }

    /**
     * Создать канал уведомлений (вызвать один раз при старте приложения).
     */
    fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Напоминания Клёпы",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Push-уведомления от голосового ассистента"
                    enableVibration(true)
                }
                nm.createNotificationChannel(channel)
            }
        }
    }

    // ---------- internals ----------

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    private fun load(prefs: SharedPreferences): List<Reminder> {
        return try {
            val json = prefs.getString(KEY_LIST, "[]") ?: "[]"
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Reminder(o.getInt("id"), o.getString("title"), o.getLong("triggerAtMs"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка чтения напоминаний: ${e.message}")
            emptyList()
        }
    }

    private fun save(prefs: SharedPreferences, list: List<Reminder>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().apply {
                put("id", it.id)
                put("title", it.title)
                put("triggerAtMs", it.triggerAtMs)
            })
        }
        prefs.edit().putString(KEY_LIST, arr.toString()).apply()
    }

    private fun setAlarm(ctx: Context, r: Reminder) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(ctx, ReminderReceiver::class.java).apply {
            action = ACTION_FIRE
            putExtra(EXTRA_REMINDER_ID, r.id)
            putExtra(EXTRA_REMINDER_TITLE, r.title)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        val pi = PendingIntent.getBroadcast(ctx, r.id, intent, flags)

        // setExactAndAllowWhileIdle — срабатывает даже в Doze
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, r.triggerAtMs, pi)
        } else {
            am.setExact(AlarmManager.RTC_WAKEUP, r.triggerAtMs, pi)
        }
    }

    private fun cancelAlarm(ctx: Context, id: Int) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(ctx, ReminderReceiver::class.java).apply { action = ACTION_FIRE }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        val pi = PendingIntent.getBroadcast(ctx, id, intent, flags)
        am.cancel(pi)
    }
}

/**
 * Receiver: срабатывает в момент напоминания, показывает push-уведомление.
 */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val id = intent.getIntExtra(ReminderScheduler.EXTRA_REMINDER_ID, -1)
        val title = intent.getStringExtra(ReminderScheduler.EXTRA_REMINDER_TITLE) ?: "Напоминание"
        Log.d("ReminderReceiver", "Срабатывание #$id: $title")

        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            android.app.Notification.Builder(ctx, ReminderScheduler.CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            android.app.Notification.Builder(ctx)
                .setPriority(android.app.Notification.PRIORITY_HIGH)
        }

        val notification = builder
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Клёпа напоминает")
            .setContentText(title)
            .setAutoCancel(true)
            .setDefaults(android.app.Notification.DEFAULT_ALL)
            .build()

        try {
            nm.notify(id, notification)
        } catch (e: Exception) {
            Log.e("ReminderReceiver", "Ошибка показа уведомления: ${e.message}")
        }

        // Удаляем из списка (одноразовое)
        val prefs = ctx.getSharedPreferences("klyopa_reminders", Context.MODE_PRIVATE)
        val list = try {
            val json = prefs.getString("reminders", "[]") ?: "[]"
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Reminder(o.getInt("id"), o.getString("title"), o.getLong("triggerAtMs"))
            }.toMutableList()
        } catch (e: Exception) { mutableListOf() }
        list.removeAll { it.id == id }
        val newArr = JSONArray()
        list.forEach {
            newArr.put(JSONObject().apply {
                put("id", it.id); put("title", it.title); put("triggerAtMs", it.triggerAtMs)
            })
        }
        prefs.edit().putString("reminders", newArr.toString()).apply()
    }
}

/**
 * Receiver: восстанавливает напоминания после перезагрузки устройства.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d("BootReceiver", "Восстанавливаю напоминания после reboot")
            ReminderScheduler.rescheduleAll(ctx)
        }
    }
}
