package com.aiagent.ai_voice_agent.helpers

import android.bluetooth.BluetoothManager
import android.content.Context
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.provider.AlarmClock
import android.provider.Settings
import android.view.KeyEvent
import android.content.Intent

/**
 * Handles system controls: WiFi, Bluetooth, flashlight, volume, brightness, media.
 */
class SystemController(private val context: Context) {
    
    private var flashlightOn = false
    
    fun setTimer(seconds: Int, label: String) {
        val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, seconds)
            putExtra(AlarmClock.EXTRA_MESSAGE, label)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            android.util.Log.e("Timer", "Failed to set timer: ${e.message}")
        }
    }

    fun handleSystemControl(action: String, value: Int?): Map<String, Any?> {
        return try {
            when (action) {
                "toggle_wifi" -> toggleWifi()
                "toggle_bluetooth" -> toggleBluetooth()
                "toggle_flashlight" -> toggleFlashlight()
                "set_volume" -> setVolume(value ?: 50)
                "set_brightness" -> setBrightness(value ?: 128)
                else -> mapOf("success" to false, "message" to "Неизвестное действие: $action")
            }
        } catch (e: Exception) {
            mapOf("success" to false, "message" to "Ошибка: ${e.message}")
        }
    }

    fun handleMediaControl(action: String) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val keyCode = when (action) {
            "play", "pause" -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
            "next" -> KeyEvent.KEYCODE_MEDIA_NEXT
            "previous" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
            "volume_up" -> {
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                return
            }
            "volume_down" -> {
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                return
            }
            else -> return
        }
        
        val event = KeyEvent(KeyEvent.ACTION_DOWN, keyCode)
        audioManager.dispatchMediaKeyEvent(event)
    }

    private fun toggleWifi(): Map<String, Any?> {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
        val newState = !wifiManager.isWifiEnabled
        @Suppress("DEPRECATION")
        wifiManager.isWifiEnabled = newState
        val state = if (newState) "включен" else "выключен"
        return mapOf("success" to true, "message" to "WiFi $state")
    }

    private fun toggleBluetooth(): Map<String, Any?> {
        val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = btManager.adapter ?: return mapOf("success" to false, "message" to "Bluetooth недоступен")
        val newState = !adapter.isEnabled
        if (newState) adapter.enable() else adapter.disable()
        val state = if (newState) "включен" else "выключен"
        return mapOf("success" to true, "message" to "Bluetooth $state")
    }

    private fun toggleFlashlight(): Map<String, Any?> {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraId = cameraManager.cameraIdList.firstOrNull() 
            ?: return mapOf("success" to false, "message" to "Камера не найдена")
        flashlightOn = !flashlightOn
        cameraManager.setTorchMode(cameraId, flashlightOn)
        val state = if (flashlightOn) "включен" else "выключен"
        return mapOf("success" to true, "message" to "Фонарик $state")
    }

    private fun setVolume(level: Int): Map<String, Any?> {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val targetVolume = (level.coerceIn(0, 100) / 100.0 * maxVolume).toInt()
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVolume, 0)
        return mapOf("success" to true, "message" to "Громкость установлена на $level%")
    }

    private fun setBrightness(level: Int): Map<String, Any?> {
        val brightness = level.coerceIn(0, 255)
        try {
            Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
            Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, brightness)
            return mapOf("success" to true, "message" to "Яркость установлена на ${brightness * 100 / 255}%")
        } catch (e: Exception) {
            return mapOf("success" to false, "message" to "Нет разрешения на изменение яркости. Дайте разрешение в настройках.")
        }
    }
}
