/**
 * DalleGenerator — генерация изображений через gpt-image-1-mini API.
 *
 * Модель: gpt-image-1-mini
 * Стоимость: $0.005 за изображение (0.5 цента, 1024x1024 Low).
 * Возвращает: base64 (b64_json).
 *
 * Зависимости: OkHttp, org.json
 */
package com.aiagent.ai_voice_agent.engine.tools

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class DalleGenerator(private val apiKey: String) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)  // base64 большой — дольше ждём
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Генерирует изображение через gpt-image-1-mini.
     * @param prompt Описание изображения
     * @param size Размер: "1024x1024", "1024x1536", "1536x1024"
     * @return Bitmap или null при ошибке
     */
    suspend fun generateImage(
        prompt: String,
        size: String = "1024x1024"
    ): Bitmap? {
        return try {
            val requestBody = JSONObject().apply {
                put("model", "gpt-image-1-mini")
                put("prompt", prompt)
                put("n", 1)
                put("size", size)
            }.toString()

            val request = okhttp3.Request.Builder()
                .url("https://api.openai.com/v1/images/generations")
                .header("Authorization", "Bearer $apiKey")
                .header("Content-Type", "application/json")
                .post(requestBody.toRequestBody())
                .build()

            android.util.Log.d("DalleGenerator", "Отправляем запрос: model=gpt-image-1-mini, prompt=$prompt")
            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "No body"
                android.util.Log.e("DalleGenerator", "Ошибка API ${response.code}: $errorBody")
                return null
            }

            val responseBody = response.body?.string() ?: return null
            val jsonResponse = JSONObject(responseBody)
            
            // gpt-image-1 возвращает b64_json (base64)
            val b64Json = jsonResponse.getJSONArray("data")
                .getJSONObject(0)
                .optString("b64_json", "")

            if (b64Json.isEmpty()) {
                // Fallback: может вернуть url (старый формат)
                val imageUrl = jsonResponse.getJSONArray("data")
                    .getJSONObject(0)
                    .optString("url", "")
                if (imageUrl.isNotEmpty()) {
                    android.util.Log.d("DalleGenerator", "Получен URL, скачиваем: $imageUrl")
                    return downloadBitmap(imageUrl)
                }
                android.util.Log.e("DalleGenerator", "Нет ни b64_json, ни url в ответе")
                return null
            }

            android.util.Log.d("DalleGenerator", "Получен base64 (${b64Json.length} символов), декодируем...")
            decodeBase64Image(b64Json)

        } catch (e: Exception) {
            android.util.Log.e("DalleGenerator", "Ошибка: ${e.message}", e)
            null
        }
    }

    /**
     * Декодирует base64 в Bitmap.
     */
    private fun decodeBase64Image(base64: String): Bitmap? {
        return try {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Exception) {
            android.util.Log.e("DalleGenerator", "Ошибка декодирования base64: ${e.message}", e)
            null
        }
    }

    /**
     * Скачивает Bitmap по URL (fallback для старого формата).
     */
    private fun downloadBitmap(imageUrl: String): Bitmap? {
        return try {
            val url = java.net.URL(imageUrl)
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.doInput = true
            connection.connectTimeout = 30000
            connection.connect()
            val input = connection.inputStream
            BitmapFactory.decodeStream(input)
        } catch (e: Exception) {
            android.util.Log.e("DalleGenerator", "Ошибка скачивания: ${e.message}", e)
            null
        }
    }
}
