package com.aiagent.ai_voice_agent.helpers

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/**
 * Handles app launching by name with fuzzy matching and verification.
 */
class AppLauncher(private val context: Context) {
    
    private val pm = context.packageManager
    
    fun openAppByName(name: String): Map<String, Any?> {
        val query = name.lowercase().trim()
        
        // 1. Try exact package name
        val exactResult = launchApp(name)
        if (exactResult["success"] == true) {
            return exactResult
        }
        
        // 2. Try common package name patterns
        for ((pkg, keywords) in commonPackages) {
            for (keyword in keywords) {
                if (query.contains(keyword) || keyword.contains(query)) {
                    val result = launchApp(pkg)
                    if (result["success"] == true) {
                        return result
                    }
                }
            }
        }

        // 3. Search through all installed apps
        val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        val similarApps = mutableListOf<Map<String, String>>()
        
        // First pass: exact label match
        for (app in packages) {
            val label = pm.getApplicationLabel(app).toString().lowercase().trim()
            if (label == query) {
                val result = launchApp(app.packageName)
                if (result["success"] == true) {
                    return result
                }
            }
        }
        
        // Second pass: contains match
        for (app in packages) {
            val label = pm.getApplicationLabel(app).toString().lowercase().trim()
            if (label.contains(query) || query.contains(label)) {
                val result = launchApp(app.packageName)
                if (result["success"] == true) {
                    return result
                }
                similarApps.add(mapOf("label" to label, "package" to app.packageName))
            }
        }
        
        // Third pass: fuzzy match
        val normalizedQuery = query.replace(Regex("[^a-zа-я0-9]"), "")
        for (app in packages) {
            val label = pm.getApplicationLabel(app).toString().lowercase()
            val normalizedLabel = label.replace(Regex("[^a-zа-я0-9]"), "")
            if (normalizedLabel.contains(normalizedQuery) || normalizedQuery.contains(normalizedLabel)) {
                val result = launchApp(app.packageName)
                if (result["success"] == true) {
                    return result
                }
                if (!similarApps.any { it["package"] == app.packageName }) {
                    similarApps.add(mapOf("label" to label, "package" to app.packageName))
                }
            }
        }
        
        // Fourth pass: package name contains query
        for (app in packages) {
            if (app.packageName.lowercase().contains(query)) {
                val result = launchApp(app.packageName)
                if (result["success"] == true) {
                    return result
                }
                if (!similarApps.any { it["package"] == app.packageName }) {
                    similarApps.add(mapOf("label" to getAppLabel(app.packageName), "package" to app.packageName))
                }
            }
        }
        
        // Fifth pass: semantic matching
        val queryKeywords = semanticMatches.entries
            .filter { query.contains(it.key) }
            .flatMap { it.value }
        
        for (app in packages) {
            val label = pm.getApplicationLabel(app).toString().lowercase()
            val pkg = app.packageName.lowercase()
            for (keyword in queryKeywords) {
                if (label.contains(keyword) || pkg.contains(keyword)) {
                    val result = launchApp(app.packageName)
                    if (result["success"] == true) {
                        return result
                    }
                    if (!similarApps.any { it["package"] == app.packageName }) {
                        similarApps.add(mapOf("label" to label, "package" to app.packageName))
                    }
                }
            }
        }
        
        android.util.Log.w("OpenApp", "App not found: $query. Similar apps: ${similarApps.size}")
        
        return mapOf(
            "success" to false,
            "similarApps" to similarApps.take(10)
        )
    }
    
    private fun launchApp(packageName: String): Map<String, Any?> {
        val intent = pm.getLaunchIntentForPackage(packageName)
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(intent)
                
                // Verification: check if app is running
                Thread.sleep(500)
                
                val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                val runningApps = activityManager.runningAppProcesses ?: emptyList()
                val isRunning = runningApps.any { process ->
                    process.pkgList.any { it == packageName } && 
                    process.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
                }
                
                val appLabel = getAppLabel(packageName)
                
                return if (isRunning) {
                    mapOf("success" to true, "appName" to appLabel, "verified" to true)
                } else {
                    mapOf("success" to true, "appName" to appLabel, "verified" to false, 
                          "message" to "Команда отправлена, но приложение ещё загружается")
                }
            } catch (e: Exception) {
                android.util.Log.e("OpenApp", "Failed to launch $packageName: ${e.message}")
            }
        }
        return mapOf("success" to false)
    }
    
    private fun getAppLabel(packageName: String): String {
        return try {
            val appInfo = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            packageName
        }
    }
    
    companion object {
        val commonPackages = listOf(
            "com.roblox.client" to listOf("roblox", "роблокс"),
            "com.google.android.youtube" to listOf("youtube", "ютуб", "ютюб"),
            "com.netflix.mediaclient" to listOf("netflix", "нетфликс"),
            "com.spotify.music" to listOf("spotify", "спотифай"),
            "org.telegram.messenger" to listOf("telegram", "телеграм", "телега"),
            "com.whatsapp" to listOf("whatsapp", "ватсап", "вацап"),
            "com.viber.voip" to listOf("viber", "вайбер"),
            "com.skype.raider" to listOf("skype", "скайп"),
            "com.discord" to listOf("discord", "дискорд"),
            "com.tencent.mm" to listOf("wechat", "вичат"),
            "com.instagram.android" to listOf("instagram", "инстаграм"),
            "com.facebook.katana" to listOf("facebook", "фейсбук"),
            "com.google.android.gm" to listOf("gmail", "гмаил", "почта"),
            "com.google.android.apps.maps" to listOf("maps", "карты", "гугл карты"),
            "com.google.android.keep" to listOf("keep", "заметки", "notes"),
            "com.google.android.calendar" to listOf("calendar", "календарь"),
            "com.google.android.photos" to listOf("photos", "фото", "фотографии"),
            "com.google.android.apps.docs" to listOf("docs", "документы"),
            "com.samsung.android.app.notes" to listOf("samsung notes", "заметки samsung"),
            "com.sec.android.app.myfiles" to listOf("my files", "файлы", "мои файлы"),
            "com.android.settings" to listOf("settings", "настройки"),
            "com.android.camera" to listOf("camera", "камера"),
            "com.android.chrome" to listOf("chrome", "хром", "браузер"),
        )
        
        val semanticMatches = mapOf(
            "заметк" to listOf("note", "keep", "memo", "notepad"),
            "note" to listOf("заметк", "keep", "memo"),
            "календар" to listOf("calendar", "dates"),
            "calendar" to listOf("календар"),
            "почт" to listOf("mail", "email", "gmail"),
            "mail" to listOf("почт", "email"),
            "файл" to listOf("file", "folder", "manager"),
            "file" to listOf("файл", "folder"),
            "фото" to listOf("photo", "gallery", "camera"),
            "photo" to listOf("фото", "gallery"),
            "музык" to listOf("music", "audio", "player"),
            "music" to listOf("музык", "audio"),
            "видео" to listOf("video", "player", "movie"),
            "video" to listOf("видео", "movie"),
        )
    }
}
