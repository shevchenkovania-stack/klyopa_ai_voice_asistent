package com.aiagent.ai_voice_agent.engine.tools

import android.content.Context
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import com.aiagent.ai_voice_agent.engine.core.AgentTool
import com.aiagent.ai_voice_agent.engine.core.ToolParam
import com.aiagent.ai_voice_agent.engine.core.ToolResult

// ==================== BATTERY INFO ====================
class BatteryInfoTool(private val context: Context) : AgentTool {
    override val name = "battery_info"
    override val description = """
        Получить информацию о батарее устройства.
        ЧТО ДЕЛАЕТ: Показывает текущий заряд батареи в процентах, статус (CRITICAL/LOW/NORMAL/GOOD/FULL), и заряжается ли устройство.
        КОГДА ИСПОЛЬЗОВАТЬ: Когда пользователь спрашивает о заряде батареи, "сколько заряда", "нужно ли заряжать".
        ПАРАМЕТРЫ: Нет параметров.
        ВОЗВРАЩАЕТ: "Battery: 75%\nStatus: GOOD\nCharging: No"
    """.trimIndent()
    override val parameters = emptyList<ToolParam>()

    override suspend fun execute(params: Map<String, Any?>): ToolResult {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val isCharging = bm.isCharging
        
        val status = when {
            level <= 10 -> "CRITICAL"
            level <= 20 -> "LOW"
            level <= 50 -> "NORMAL"
            level <= 80 -> "GOOD"
            else -> "FULL"
        }
        
        val info = buildString {
            appendLine("Battery: $level%")
            appendLine("Status: $status")
            appendLine("Charging: ${if (isCharging) "Yes" else "No"}")
        }
        return ToolResult.success(info.trim())
    }
}

// ==================== DEVICE INFO ====================
class DeviceInfoTool(private val context: Context) : AgentTool {
    override val name = "device_info"
    override val description = """
        Получить информацию об устройстве.
        ЧТО ДЕЛАЕТ: Показывает модель устройства, версию Android, свободное и общее место в хранилище.
        КОГДА ИСПОЛЬЗОВАТЬ: Когда пользователь спрашивает "какой у меня телефон", "сколько памяти", "какая версия Android".
        ПАРАМЕТРЫ: Нет параметров.
        ВОЗВРАЩАЕТ: "Device: Samsung Galaxy S21\nAndroid: 14\nStorage: 45.2 GB free of 128 GB"
    """.trimIndent()
    override val parameters = emptyList<ToolParam>()

    override suspend fun execute(params: Map<String, Any?>): ToolResult {
        val stat = StatFs(Environment.getDataDirectory().path)
        val available = stat.availableBlocksLong * stat.blockSizeLong
        val total = stat.blockCountLong * stat.blockSizeLong
        val used = total - available
        
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager
        val metrics = android.util.DisplayMetrics()
        wm.defaultDisplay.getMetrics(metrics)
        
        val info = buildString {
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Storage: ${formatBytes(used)} used / ${formatBytes(total)} total (${formatBytes(available)} free)")
            appendLine("Display: ${metrics.widthPixels}x${metrics.heightPixels}")
        }
        return ToolResult.success(info.trim())
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
        else -> "${"%.1f".format(bytes / (1024.0 * 1024 * 1024))} GB"
    }
}

// ==================== STORAGE INFO ====================
class StorageInfoTool(private val context: Context) : AgentTool {
    override val name = "storage_info"
    override val description = """
        Получить детальную информацию о хранилище устройства.
        ЧТО ДЕЛАЕТ: Показывает общий объём памяти, использовано, свободно в процентах и абсолютных значениях.
        КОГДА ИСПОЛЬЗОВАТЬ: Когда пользователь спрашивает "сколько памяти занято", "есть ли место", "очисти память".
        ПАРАМЕТРЫ: Нет параметров.
        ВОЗВРАЩАЕТ: "Total: 128 GB\nUsed: 82.5 GB (64%)\nFree: 45.5 GB"
    """.trimIndent()
    override val parameters = emptyList<ToolParam>()

    override suspend fun execute(params: Map<String, Any?>): ToolResult {
        val stat = StatFs(Environment.getDataDirectory().path)
        val available = stat.availableBlocksLong * stat.blockSizeLong
        val total = stat.blockCountLong * stat.blockSizeLong
        val used = total - available
        val percentUsed = (used * 100 / total).toInt()
        
        val info = buildString {
            appendLine("Total: ${formatBytes(total)}")
            appendLine("Used: ${formatBytes(used)} ($percentUsed%)")
            appendLine("Free: ${formatBytes(available)}")
            if (percentUsed > 90) appendLine("WARNING: Storage almost full!")
            else if (percentUsed > 80) appendLine("Note: Storage getting low")
        }
        return ToolResult.success(info.trim())
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
        else -> "${"%.1f".format(bytes / (1024.0 * 1024 * 1024))} GB"
    }
}
