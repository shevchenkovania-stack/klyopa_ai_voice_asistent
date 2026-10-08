package com.aiagent.ai_voice_agent.engine.tools

import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import com.aiagent.ai_voice_agent.engine.core.AgentTool
import com.aiagent.ai_voice_agent.engine.core.ToolParam
import com.aiagent.ai_voice_agent.engine.core.ToolResult

// ==================== LIST APPS ====================
class ListAppsTool(private val context: Context) : AgentTool {
    override val name = "list_apps"
    override val description = "List installed applications"
    override val parameters = listOf(
        ToolParam(name = "filter", description = "Filter by name (optional)", type = "string", required = false),
        ToolParam(name = "limit", description = "Max apps to return (default: 20)", type = "number", required = false)
    )

    override suspend fun execute(params: Map<String, Any?>): ToolResult {
        val filter = (params["filter"] as? String ?: "").lowercase()
        val limit = (params["limit"] as? Number)?.toInt() ?: 20
        
        val pm = context.packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
            .filter { filter.isEmpty() || it.loadLabel(pm).toString().lowercase().contains(filter) }
            .sortedBy { it.loadLabel(pm).toString().lowercase() }
            .take(limit)
        
        val list = apps.joinToString("\n") { "${it.loadLabel(pm)} (${it.packageName})" }
        val header = if (filter.isNotEmpty()) "Apps matching '$filter' (${apps.size}):"
        else "Installed apps (${apps.size}):"
        
        return ToolResult.success("$header\n$list")
    }
}

// ==================== APP INFO ====================
class AppInfoTool(private val context: Context) : AgentTool {
    override val name = "app_info"
    override val description = "Get information about an installed app"
    override val parameters = listOf(
        ToolParam(name = "app_name", description = "App name or package name", type = "string", required = true)
    )

    override suspend fun execute(params: Map<String, Any?>): ToolResult {
        val query = params["app_name"] as? String ?: return ToolResult.failure("App name is required")
        val pm = context.packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        
        val app = apps.find { 
            it.packageName.equals(query, ignoreCase = true) ||
            it.loadLabel(pm).toString().equals(query, ignoreCase = true)
        } ?: apps.find { it.loadLabel(pm).toString().lowercase().contains(query.lowercase()) }
        
        if (app == null) return ToolResult.failure("App not found: $query")
        
        val packageInfo = try { pm.getPackageInfo(app.packageName, 0) } catch (e: Exception) { null }
        val isSystem = app.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM != 0
        
        val info = buildString {
            appendLine("Name: ${app.loadLabel(pm)}")
            appendLine("Package: ${app.packageName}")
            appendLine("Version: ${packageInfo?.versionName ?: "Unknown"}")
            appendLine("Target SDK: ${app.targetSdkVersion}")
            appendLine("System app: $isSystem")
        }
        return ToolResult.success(info.trim())
    }
}

// ==================== NETWORK INFO ====================
class NetworkInfoTool(private val context: Context) : AgentTool {
    override val name = "network_info"
    override val description = "Get network connection status (WiFi/mobile)"
    override val parameters = emptyList<ToolParam>()

    override suspend fun execute(params: Map<String, Any?>): ToolResult {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = cm.activeNetwork ?: return ToolResult.success("No network connection")
            val caps = cm.getNetworkCapabilities(network)
            
            val type = when {
                caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "WiFi"
                caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "Mobile"
                caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> "Ethernet"
                caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true -> "VPN"
                else -> "Unknown"
            }
            val hasInternet = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
            val isMetered = cm.isActiveNetworkMetered
            
            val info = buildString {
                appendLine("Connected: Yes")
                appendLine("Type: $type")
                appendLine("Internet: ${if (hasInternet) "Available" else "No internet"}")
                appendLine("Metered: ${if (isMetered) "Yes" else "No (unlimited)"}")
            }
            return ToolResult.success(info.trim())
        } else {
            @Suppress("DEPRECATION")
            val ni = cm.activeNetworkInfo
            return if (ni?.isConnected == true) {
                @Suppress("DEPRECATION")
                ToolResult.success("Connected: ${ni?.typeName ?: "Unknown"}")
            } else {
                ToolResult.success("No network connection")
            }
        }
    }
}
