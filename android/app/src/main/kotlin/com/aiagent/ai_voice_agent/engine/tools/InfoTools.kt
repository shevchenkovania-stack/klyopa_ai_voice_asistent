/**
 * InfoTools — набор инструментов для получения информации из интернета и GPS.
 *
 * Файл содержит 3 инструмента:
 * - `get_weather` — погода через wttr.in (бесплатно, без API ключа)
 * - `get_currency_rate` — курсы валют через exchangerate-api.com (бесплатно)
 * - `get_location` — определение текущего местоположения через GPS/Network
 *
 * Зависимости: OkHttp (для HTTP запросов), Geocoder (для обратного геокодирования).
 * get_location требует ACCESS_FINE_LOCATION или ACCESS_COARSE_LOCATION permission.
 */
package com.aiagent.ai_voice_agent.engine.tools

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.location.Geocoder
import android.location.LocationManager
import android.provider.Settings
import com.aiagent.ai_voice_agent.engine.core.AgentTool
import com.aiagent.ai_voice_agent.engine.core.ToolParam
import com.aiagent.ai_voice_agent.engine.core.ToolResult
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * Инструмент `get_weather` — получение прогноза погоды.
 * Использует бесплатный API wttr.in (не требует ключа).
 * Возвращает текущую температуру, ощущаемую температуру, влажность, ветер, описание.
 * Дополнительно: совет по одежде и прогноз на 1-3 дня.
 */
class GetWeatherTool : AgentTool {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    override val name = "get_weather"
    override val description = """
        Узнать погоду в городе.
        ЧТО ДЕЛАЕТ: Возвращает текущую погоду и прогноз на несколько дней через wttr.in.
        КОГДА ИСПОЛЬЗОВАТЬ: Когда пользователь спрашивает "какая погода", "будет ли дождь", "что надеть",
        "завтра тепло?", "прогноз на неделю". Также возвращает совет по одежде.
        ПАРАМЕТРЫ: city (обязательно, название города), days (0=сейчас, 1-3=прогноз).
        ВОЗВРАЩАЕТ: Температура, описание, влажность, ветер, совет по одежде, прогноз.
        ПРИМЕЧАНИЕ: Не требует API ключа.
    """.trimIndent()
    override val parameters = listOf(
        ToolParam("city", "string", "Название города (например \"Кишинёв\", \"Москва\", \"London\")", required = true),
        ToolParam("days", "number", "Прогноз на сколько дней (0=только сейчас, 1-3=прогноз). По умолчанию 0.", required = false)
    )

    override suspend fun execute(params: Map<String, Any?>): ToolResult {
        val city = params["city"] as? String
        if (city.isNullOrBlank()) return ToolResult.failure("Не указан город")
        val days = (params["days"] as? Number)?.toInt() ?: 0

        return try {
            val url = "https://wttr.in/${java.net.URLEncoder.encode(city, "UTF-8")}?format=j1"
            val request = Request.Builder().url(url)
                .header("User-Agent", "curl/7.68.0")
                .build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return ToolResult.failure("Пустой ответ")

            if (!response.isSuccessful) return ToolResult.failure("Ошибка запроса погоды")

            val json = JSONObject(body)
            val current = json.optJSONArray("current_condition")?.optJSONObject(0)
                ?: return ToolResult.failure("Данные о погоде недоступны")

            val temp = current.optString("temp_C", "?")
            val feelsLike = current.optString("FeelsLikeC", "?")
            val humidity = current.optString("humidity", "?")
            val windSpeed = current.optString("windspeedKmph", "?")
            val description = current.optJSONArray("weatherDesc")?.optJSONObject(0)?.optString("value", "?") ?: "?"

            val result = StringBuilder()
            result.appendLine("Погода в городе $city:")
            result.appendLine("Температура: ${temp}°C (ощущается как ${feelsLike}°C)")
            result.appendLine("Описание: $description")
            result.appendLine("Влажность: ${humidity}%")
            result.appendLine("Ветер: $windSpeed км/ч")
            result.appendLine("")
            result.appendLine("Совет по одежде: ${clothingAdvice(temp.toIntOrNull())}")

            // Forecast if requested
            if (days > 0) {
                val weatherArray = json.optJSONArray("weather")
                if (weatherArray != null) {
                    result.appendLine("")
                    result.appendLine("=== Прогноз ===")
                    for (i in 0 until minOf(days, weatherArray.length())) {
                        val day = weatherArray.getJSONObject(i)
                        val date = day.optString("date", "?")
                        val maxTemp = day.optString("maxtempC", "?")
                        val minTemp = day.optString("mintempC", "?")
                        val dayDesc = day.optJSONArray("hourly")?.optJSONObject(4)
                            ?.optJSONArray("weatherDesc")?.optJSONObject(0)?.optString("value", "?") ?: "?"
                        val dayHumidity = day.optJSONArray("hourly")?.optJSONObject(4)
                            ?.optString("humidity", "?") ?: "?"

                        result.appendLine("$date: $minTemp..$maxTemp°C, $dayDesc, влажность ${dayHumidity}%")
                    }
                }
            }

            ToolResult.success(result.toString().trim())
        } catch (e: Exception) {
            ToolResult.failure("Ошибка получения погоды: ${e.message}")
        }
    }

    private fun clothingAdvice(tempC: Int?): String {
        if (tempC == null) return "Не удалось определить"
        return when {
            tempC <= 0 -> "Очень холодно! Пуховик, шапка, шарф, перчатки."
            tempC <= 10 -> "Холодно. Тёплая куртка, шапка, шарф."
            tempC <= 16 -> "Прохладно. Куртка или свитер."
            tempC <= 22 -> "Комфортно. Лёгкая куртка или кофта."
            tempC <= 28 -> "Тепло. Лёгкая одежда, футболка."
            else -> "Жарко! Минимум одежды, головной убор, вода."
        }
    }
}

/**
 * Инструмент `get_currency_rate` — получение курсов валют.
 * Использует бесплатный API exchangerate-api.com (не требует ключа).
 * Поддерживает конвертацию сумм (amount параметр).
 */
class GetCurrencyRateTool : AgentTool {
    companion object { private const val TAG = "CurrencyRate" }
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    override val name = "get_currency_rate"
    override val description = """
        Узнать курс валюты.
        ЧТО ДЕЛАЕТ: Возвращает текущий курс обмена между двумя валютами.
        КОГДА ИСПОЛЬЗОВАТЬ: Когда пользователь спрашивает "курс доллара", "сколько евро", "курс валют", "доллар к мдл".
        ПАРАМЕТРЫ: from (код валюты, обязательно), to (код валюты, обязательно), amount (сумма, необязательно).
        ВОЗВРАЩАЕТ: "1 USD = 17.85 MDL" или с суммой "100 USD = 1785.00 MDL"
        ПРИМЕРЫ: from="USD", to="MDL" → курс доллара к молдавскому лею.
    """.trimIndent()
    override val parameters = listOf(
        ToolParam("from", "string", "Код исходной валюты (USD, EUR, RUB, MDL, UAH, KZT и т.д.)", required = true),
        ToolParam("to", "string", "Код целевой валюты (USD, EUR, RUB, MDL, UAH, KZT и т.д.)", required = true),
        ToolParam("amount", "number", "Сумма для конвертации (по умолчанию 1)", required = false)
    )

    override suspend fun execute(params: Map<String, Any?>): ToolResult {
        val from = (params["from"] as? String)?.uppercase()?.trim()
        val to = (params["to"] as? String)?.uppercase()?.trim()
        
        if (from.isNullOrBlank()) return ToolResult.failure("Не указана исходная валюта (from)")
        if (to.isNullOrBlank()) return ToolResult.failure("Не указана целевая валюта (to)")
        
        val amount = (params["amount"] as? Number)?.toDouble() ?: 1.0

        android.util.Log.d(TAG, "Запрос курса: $amount $from → $to")

        return try {
            // Основной API
            val url = "https://api.exchangerate-api.com/v4/latest/$from"
            android.util.Log.d(TAG, "URL: $url")
            
            val request = Request.Builder().url(url)
                .header("Accept", "application/json")
                .build()
            val response = client.newCall(request).execute()
            val body = response.body?.string()
            
            android.util.Log.d(TAG, "Response code: ${response.code}, body length: ${body?.length ?: 0}")
            
            if (body.isNullOrBlank()) {
                return ToolResult.failure("Пустой ответ от API. Попробуйте позже.")
            }

            if (!response.isSuccessful) {
                android.util.Log.e(TAG, "API error: ${response.code} - $body")
                return ToolResult.failure("Ошибка API (${response.code}). Проверьте коды валют.")
            }

            val json = JSONObject(body)
            
            // Проверяем результат
            val result = json.optString("result", "")
            if (result == "error") {
                val errorType = json.optString("error-type", "unknown")
                android.util.Log.e(TAG, "API returned error: $errorType")
                return ToolResult.failure("Ошибка API: $errorType. Проверьте код валюты '$from'.")
            }
            
            val rates = json.optJSONObject("rates")
            if (rates == null) {
                android.util.Log.e(TAG, "No rates in response: $body")
                return ToolResult.failure("Не удалось получить курсы валют. Попробуйте позже.")
            }

            if (!rates.has(to)) {
                android.util.Log.e(TAG, "Currency $to not found in rates")
                return ToolResult.failure("Валюта '$to' не найдена. Проверьте код (USD, EUR, RUB, MDL и т.д.)")
            }

            val rate = rates.getDouble(to)
            val converted = amount * rate
            
            val rateStr = "1 $from = ${"%.4f".format(rate)} $to"
            val amountStr = if (amount != 1.0) "\n$amount $from = ${"%.2f".format(converted)} $to" else ""

            android.util.Log.d(TAG, "Успех: $rateStr")
            ToolResult.success("$rateStr$amountStr")
        } catch (e: java.net.UnknownHostException) {
            android.util.Log.e(TAG, "Network error: ${e.message}")
            ToolResult.failure("Нет интернета или DNS ошибка")
        } catch (e: java.net.SocketTimeoutException) {
            android.util.Log.e(TAG, "Timeout: ${e.message}")
            ToolResult.failure("Таймаут. Попробуйте позже.")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error: ${e.message}", e)
            ToolResult.failure("Ошибка: ${e.message}")
        }
    }
}

/**
 * Инструмент `get_location` — определение текущего местоположения.
 * Использует GPS и Network провайдеры для получения координат.
 * Обратное геокодирование через Geocoder для получения названия города.
 * Требует ACCESS_FINE_LOCATION или ACCESS_COARSE_LOCATION permission.
 *
 * Совет: использовать ПЕРЕД get_weather если пользователь не указал город.
 */
class GetLocationTool(private val context: Context) : AgentTool {
    override val name = "get_location"
    override val description = """
        Определить текущее местоположение пользователя (город).
        ЧТО ДЕЛАЕТ: Получает GPS/Network координаты и определяет город через Geocoder.
        КОГДА ИСПОЛЬЗОВАТЬ: ПЕРЕД get_weather если пользователь не указал город.
        Также когда пользователь спрашивает "где я", "какой я город".
        ПАРАМЕТРЫ: Нет параметров.
        ВОЗВРАЩАЕТ: Город, страна, полный адрес, координаты.
        ОГРАНИЧЕНИЯ: Требует включённую геолокацию и разрешение ACCESS_FINE_LOCATION.
    """.trimIndent()
    override val parameters = emptyList<ToolParam>()

    @SuppressLint("MissingPermission")
    override suspend fun execute(params: Map<String, Any?>): ToolResult {
        return try {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

            // Проверяем, включена ли геолокация
            val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
            val isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
            
            if (!isGpsEnabled && !isNetworkEnabled) {
                // Геолокация выключена — открываем настройки
                val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                
                return ToolResult.failure(
                    "Геолокация выключена. Открыл настройки — включите определение местоположения."
                )
            }

            // Try GPS first, then Network
            val providers = listOf(
                LocationManager.GPS_PROVIDER,
                LocationManager.NETWORK_PROVIDER
            )

            var location: android.location.Location? = null
            for (provider in providers) {
                if (locationManager.isProviderEnabled(provider)) {
                    location = locationManager.getLastKnownLocation(provider)
                    if (location != null) break
                }
            }

            if (location == null) {
                return ToolResult.failure(
                    "Не удалось получить координаты. Подождите немного или включите GPS."
                )
            }

            // Reverse geocode to get city name
            val geocoder = Geocoder(context, Locale.getDefault())
            val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)

            if (addresses.isNullOrEmpty()) {
                return ToolResult.failure(
                    "Местоположение получено, но не удалось определить город."
                )
            }

            val address = addresses[0]
            val city = address.locality ?: address.subAdminArea ?: address.adminArea ?: "Неизвестно"
            val country = address.countryName ?: ""
            val fullAddress = address.getAddressLine(0) ?: "$city, $country"

            val result = "Текущее местоположение: $city${if (country.isNotEmpty()) ", $country" else ""}\n" +
                "Полный адрес: $fullAddress\n" +
                "Координаты: ${"%.4f".format(location.latitude)}, ${"%.4f".format(location.longitude)}"

            ToolResult.success(result)
        } catch (e: SecurityException) {
            // Нет разрешения — открываем настройки приложения
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = android.net.Uri.fromParts("package", context.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ToolResult.failure("Нет разрешения на геолокацию. Открыл настройки — разрешите доступ к местоположению.")
        } catch (e: Exception) {
            ToolResult.failure("Ошибка определения местоположения: ${e.message}")
        }
    }
}

/**
 * Инструмент `share_location` — отправить текущие координаты через любое приложение.
 * Получает GPS/Network координаты и открывает диалог шаринга.
 */
class ShareLocationTool(private val context: Context) : AgentTool {
    override val name = "share_location"
    override val description = """
        Отправить текущие координаты через SMS, WhatsApp, Telegram или другое приложение.
        ЧТО ДЕЛАЕТ: Получает GPS/Network координаты и открывает диалог шаринга.
        КОГДА ИСПОЛЬЗОВАТЬ: Когда пользователь просит "отправь мои координаты", "где я нахожусь", "отправь локацию жене".
        ПАРАМЕТРЫ: app (необязательно) — имя приложения для шаринга (sms, whatsapp, telegram). Если не указано — откроется общий диалог.
        ВОЗВРАЩАЕТ: "Открываю диалог шаринга координат: 50.4501, 30.5234"
        ОГРАНИЧЕНИЯ: Требует включённую геолокацию и разрешение ACCESS_FINE_LOCATION.
    """.trimIndent()
    override val parameters = listOf(
        ToolParam("app", "string", "Имя приложения для шаринга (sms, whatsapp, telegram). Если не указано — откроется общий диалог", required = false)
    )

    @SuppressLint("MissingPermission")
    override suspend fun execute(params: Map<String, Any?>): ToolResult {
        return try {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

            // Get location
            val providers = listOf(
                LocationManager.GPS_PROVIDER,
                LocationManager.NETWORK_PROVIDER
            )

            var location: android.location.Location? = null
            for (provider in providers) {
                if (locationManager.isProviderEnabled(provider)) {
                    location = locationManager.getLastKnownLocation(provider)
                    if (location != null) break
                }
            }

            if (location == null) {
                return ToolResult.failure("Не удалось получить координаты. Включите GPS.")
            }

            val lat = location.latitude
            val lon = location.longitude
            val locationText = "Мои координаты: $lat, $lon\nhttps://maps.google.com/maps?q=$lat,$lon"

            val appName = params["app"] as? String

            // Share via specific app or general dialog
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, locationText)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            if (appName != null) {
                // Try to find specific app
                val packageName = when (appName.lowercase()) {
                    "sms" -> "com.android.mms"
                    "whatsapp" -> "com.whatsapp"
                    "telegram" -> "org.telegram.messenger"
                    else -> null
                }

                if (packageName != null) {
                    intent.setPackage(packageName)
                }
            }

            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(Intent.createChooser(intent, "Отправить координаты через").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
                ToolResult.success("Открываю диалог шаринга координат: $lat, $lon")
            } else {
                ToolResult.failure("Не найдено приложение для шаринга")
            }
        } catch (e: Exception) {
            ToolResult.failure("Ошибка шаринга координат: ${e.message}")
        }
    }
}
