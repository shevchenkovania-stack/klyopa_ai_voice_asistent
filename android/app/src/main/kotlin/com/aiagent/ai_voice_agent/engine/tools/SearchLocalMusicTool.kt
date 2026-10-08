package com.aiagent.ai_voice_agent.engine.tools

import android.content.Context
import android.provider.MediaStore
import com.aiagent.ai_voice_agent.engine.core.AgentTool
import com.aiagent.ai_voice_agent.engine.core.ToolParam
import com.aiagent.ai_voice_agent.engine.core.ToolResult

/**
 * Search for music files on the device by title, artist, or album.
 * Returns list of matching songs with paths that can be played.
 */
class SearchLocalMusicTool(private val context: Context) : AgentTool {
    override val name = "search_local_music"
    override val description = "Search for music files on device by song name, artist, or album. Returns matching songs with file paths."
    override val parameters = listOf(
        ToolParam(name = "query", description = "Search query (song name, artist, or album)", type = "string", required = true),
        ToolParam(name = "limit", description = "Max results to return (default: 10)", type = "number", required = false)
    )

    override suspend fun execute(params: Map<String, Any?>): ToolResult {
        val query = params["query"] as? String ?: return ToolResult.failure("Query is required")
        val limit = (params["limit"] as? Number)?.toInt() ?: 10

        val songs = searchMusic(query, limit)
        
        if (songs.isEmpty()) {
            return ToolResult.failure("No songs found for '$query'")
        }

        val result = buildString {
            appendLine("Found ${songs.size} song(s) matching '$query':")
            songs.forEachIndexed { index, song ->
                appendLine("${index + 1}. ${song.title}")
                appendLine("   Artist: ${song.artist}")
                appendLine("   Album: ${song.album}")
                appendLine("   Path: ${song.path}")
                if (index < songs.size - 1) appendLine()
            }
        }

        return ToolResult.success(result.trim())
    }

    private fun searchMusic(query: String, limit: Int): List<SongInfo> {
        val songs = mutableListOf<SongInfo>()
        
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DATA
        )

        val selection = "${MediaStore.Audio.Media.TITLE} LIKE ? OR " +
                "${MediaStore.Audio.Media.ARTIST} LIKE ? OR " +
                "${MediaStore.Audio.Media.ALBUM} LIKE ?"
        
        val searchPattern = "%$query%"
        val selectionArgs = arrayOf(searchPattern, searchPattern, searchPattern)

        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC LIMIT $limit"

        try {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)

                while (cursor.moveToNext() && songs.size < limit) {
                    val id = cursor.getLong(idColumn)
                    val title = cursor.getString(titleColumn)
                    val artist = cursor.getString(artistColumn)
                    val album = cursor.getString(albumColumn)
                    val path = cursor.getString(dataColumn)

                    if (path != null && title != null) {
                        songs.add(SongInfo(
                            id = id,
                            title = title,
                            artist = artist ?: "Unknown",
                            album = album ?: "Unknown",
                            path = path
                        ))
                    }
                }
            }
        } catch (e: Exception) {
            // Return empty list on error
        }

        return songs
    }

    data class SongInfo(
        val id: Long,
        val title: String,
        val artist: String,
        val album: String,
        val path: String
    )
}
