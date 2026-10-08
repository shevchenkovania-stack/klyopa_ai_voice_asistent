package com.aiagent.ai_voice_agent.engine.tools

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import com.aiagent.ai_voice_agent.engine.core.AgentTool
import com.aiagent.ai_voice_agent.engine.core.ToolParam
import com.aiagent.ai_voice_agent.engine.core.ToolResult

/**
 * System control: WiFi, Bluetooth, Flashlight, Volume, Brightness
 */
class SystemControlTool(private val context: Context) : AgentTool {
    override val name = "system_control"
    override val description = "Управлять настройками телефона: WiFi, Bluetooth, фонарик, громкость, яркость. Действия: toggle_wifi, toggle_bluetooth, toggle_flashlight, set_volume, set_brightness."
    override val parameters = listOf(
        ToolParam("action", "string", "Действие: toggle_wifi, toggle_bluetooth, toggle_flashlight, set_volume, set_brightness", required = true),
        ToolParam("value", "number", "Значение для set_volume (0-100) или set_brightness (0-255)", required = false)
    )

    override suspend fun execute(params: Map<String, Any?>): ToolResult {
        val action = params["action"] as? String ?: return ToolResult.failure("Не указано действие")

        return when (action) {
            "toggle_wifi" -> toggleWifi()
            "toggle_bluetooth" -> toggleBluetooth()
            "toggle_flashlight" -> toggleFlashlight()
            "set_volume" -> setVolume(params["value"] as? Number)
            "set_brightness" -> setBrightness(params["value"] as? Number)
            else -> ToolResult.failure("Неизвестное действие: $action")
        }
    }

    private fun toggleWifi(): ToolResult {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ — open WiFi settings panel
                val intent = Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                ToolResult.success("✓ Открыты настройки WiFi (Android 10+ не позволяет переключать программно)")
            } else {
                @Suppress("DEPRECATION")
                val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                @Suppress("DEPRECATION")
                val newState = !wifiManager.isWifiEnabled
                @Suppress("DEPRECATION")
                wifiManager.isWifiEnabled = newState
                ToolResult.success("✓ WiFi ${if (newState) "включён" else "выключен"}")
            }
        } catch (e: Exception) {
            ToolResult.failure("Ошибка WiFi: ${e.message}")
        }
    }

    private fun toggleBluetooth(): ToolResult {
        return try {
            val btManager = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
            if (btManager == null) return ToolResult.failure("Bluetooth не поддерживается")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Android 12+ — open Bluetooth settings
                val intent = Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                ToolResult.success("✓ Открыты настройки Bluetooth")
            } else {
                val newState = !btManager.isEnabled
                @Suppress("DEPRECATION")
                if (newState) btManager.enable() else btManager.disable()
                ToolResult.success("✓ Bluetooth ${if (newState) "включён" else "выключен"}")
            }
        } catch (e: SecurityException) {
            ToolResult.failure("Нет разрешения BLUETOOTH_CONNECT")
        } catch (e: Exception) {
            ToolResult.failure("Ошибка Bluetooth: ${e.message}")
        }
    }

    private fun toggleFlashlight(): ToolResult {
        return try {
            // Use camera torch
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as android.hardware.camera2.CameraManager
            val cameraId = cameraManager.cameraIdList[0]
            cameraManager.setTorchMode(cameraId, true) // TODO: track state
            ToolResult.success("✓ Фонарик включён")
        } catch (e: Exception) {
            ToolResult.failure("Ошибка фонарика: ${e.message}")
        }
    }

    private fun setVolume(value: Number?): ToolResult {
        if (value == null) return ToolResult.failure("Не указано значение громкости (0-100)")
        return try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val target = (value.toInt() / 100.0 * max).toInt().coerceIn(0, max)
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
            ToolResult.success("✓ Громкость установлена на ${value.toInt()}%")
        } catch (e: Exception) {
            ToolResult.failure("Ошибка громкости: ${e.message}")
        }
    }

    private fun setBrightness(value: Number?): ToolResult {
        if (value == null) return ToolResult.failure("Не указано значение яркости (0-255)")
        return try {
            val brightness = value.toInt().coerceIn(0, 255)
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                brightness
            )
            ToolResult.success("✓ Яркость установлена на $brightness")
        } catch (e: SecurityException) {
            ToolResult.failure("Нет разрешения WRITE_SETTINGS. Разрешите в настройках.")
        } catch (e: Exception) {
            ToolResult.failure("Ошибка яркости: ${e.message}")
        }
    }
}
