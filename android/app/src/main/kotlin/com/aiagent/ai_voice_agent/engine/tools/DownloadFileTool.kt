package com.aiagent.ai_voice_agent.engine.tools

import android.content.Context
import android.media.MediaScannerConnection
import com.aiagent.ai_voice_agent.engine.core.AgentTool
import com.aiagent.ai_voice_agent.engine.core.ToolParam
import com.aiagent.ai_voice_agent.engine.core.ToolResult
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.net.URLDecoder

/**
 * Download a file from URL and save to device.
 * Can download MP3, images, documents, etc.
 */
class DownloadFileTool(private val context: Context) : AgentTool {
    override val name = "download_file"
    override val description = "Download a file from URL. Saves to Downloads folder. Can download MP3, images, PDF, etc."
    override val parameters = listOf(
        ToolParam(name = "url", description = "Direct URL to the file to download", type = "string", required = true),
        ToolParam(name = "filename", description = "Filename to save as (optional, auto-detected from URL if not provided)", type = "string", required = false),
        ToolParam(name = "folder", description = "Folder: 'Downloads' (default), 'Music', 'Documents', or path", type = "string", required = false)
    )

    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    override suspend fun execute(params: Map<String, Any?>): ToolResult {
        val url = params["url"] as? String ?: return ToolResult.failure("URL is required")
        val folderName = params["folder"] as? String ?: "Downloads"
        var filename = params["filename"] as? String

        // Auto-detect filename from URL if not provided
        if (filename.isNullOrBlank()) {
            filename = extractFilenameFromUrl(url) ?: "downloaded_file"
        }

        val folder = resolveFolder(folderName)
        folder.mkdirs()
        val file = File(folder, filename)

        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36")
                .build()

            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) {
                return ToolResult.failure("Download failed: HTTP ${response.code}")
            }

            val body = response.body ?: return ToolResult.failure("Empty response body")
            
            FileOutputStream(file).use { out ->
                body.byteStream().copyTo(out)
            }

            if (!file.exists() || file.length() == 0L) {
                return ToolResult.failure("Download failed: file is empty")
            }

            // Scan media so it appears in music/file apps
            scanMedia(file)

            val sizeMb = "%.1f".format(file.length() / (1024.0 * 1024))
            val relativePath = file.absolutePath.replace("/sdcard/", "/")
            
            ToolResult.success("✓ Downloaded: $relativePath ($sizeMb MB)\nFile ready to play/open")
        } catch (e: Exception) {
            ToolResult.failure("Download error: ${e.message}")
        }
    }

    private fun extractFilenameFromUrl(url: String): String? {
        return try {
            val path = url.split("?").first()
            val name = path.split("/").last()
            URLDecoder.decode(name, "UTF-8").takeIf { it.contains(".") }
        } catch (e: Exception) {
            null
        }
    }

    private fun resolveFolder(folder: String): File {
        return when {
            folder.startsWith("/") -> File(folder)
            folder.contains("download", true) || folder.contains("загрузк", true) -> File("/sdcard/Download")
            folder.contains("music", true) || folder.contains("музык", true) -> File("/sdcard/Music")
            folder.contains("document", true) || folder.contains("документ", true) -> File("/sdcard/Documents")
            folder.contains("picture", true) || folder.contains("фото", true) -> File("/sdcard/Pictures")
            else -> File("/sdcard/Download")
        }
    }

    private fun scanMedia(file: File) {
        try {
            MediaScannerConnection.scanFile(
                context,
                arrayOf(file.absolutePath),
                null
            ) { _, _ -> }
        } catch (e: Exception) {
            // Ignore scan errors
        }
    }
}
