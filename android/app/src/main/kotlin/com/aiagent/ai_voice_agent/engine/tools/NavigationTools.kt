package com.aiagent.ai_voice_agent.engine.tools

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.aiagent.ai_voice_agent.engine.core.AgentTool
import com.aiagent.ai_voice_agent.engine.core.ToolParam
import com.aiagent.ai_voice_agent.engine.core.ToolResult
import android.util.Log

/**
 * Инструменты навигации через Google Maps.
 * 
 * Доступные инструменты:
 * - `open_maps` — открыть Google Maps
 * - `build_route` — построить маршрут до адреса
 * - `find_nearby` — найти ближайшие места (заправки, парковки, кафе, аптеки)
 * - `route_home` — быстрый маршрут домой (если адрес сохранён)
 * - `route_work` — быстрый маршрут на работу (если адрес сохранён)
 * 
 * Зависимости: Google Maps app или браузер
 */

/**
 * Инструмент `open_maps` — открыть Google Maps.
 */
class OpenMapsTool(private val context: Context) : AgentTool {
    companion object { private const val TAG = "OpenMaps" }
    
    override val name = "open_maps"
    override val description = """
        Открыть Google Maps.
        ЧТО ДЕЛАЕТ: Открывает приложение Google Maps (или браузер, если Maps не установлен).
        КОГДА ИСПОЛЬЗОВАТЬ: Когда пользователь просит "открой карты", "покажи карту", "открой гугл мэпс".
        ПАРАМЕТРЫ: Нет обязательных параметров.
        ВОЗВРАЩАЕТ: "Открываю Google Maps"
    """.trimIndent()
    override val parameters = emptyList<ToolParam>()
    
    override suspend fun execute(params: Map<String, Any?>): ToolResult {
        return try {
            // Пробуем открыть Google Maps app
            val mapsIntent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("geo:0,0")
                setPackage("com.google.android.apps.maps")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            try {
                context.startActivity(mapsIntent)
                Log.d(TAG, "Открыт Google Maps app")
                ToolResult.success("Открываю Google Maps")
            } catch (e: Exception) {
                // Если Maps app нет — открываем в браузере
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://maps.google.com")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(browserIntent)
                Log.d(TAG, "Открыт Google Maps в браузере")
                ToolResult.success("Открываю Google Maps в браузере")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка открытия Maps: ${e.message}", e)
            ToolResult.failure("Не удалось открыть карты: ${e.message}")
        }
    }
}

/**
 * Инструмент `build_route` — построить маршрут до адреса.
 */
class BuildRouteTool(private val context: Context) : AgentTool {
    companion object { private const val TAG = "BuildRoute" }
    
    override val name = "build_route"
    override val description = """
        Построить маршрут до адреса в Google Maps.
        ЧТО ДЕЛАЕТ: Открывает Google Maps с маршрутом до указанного адреса.
        КОГДА ИСПОЛЬЗОВАТЬ: Когда пользователь просит "построй маршрут", "как доехать до", "маршрут до", "навигатор до".
        ПАРАМЕТРЫ: destination (обязательно) — адрес или название места (например "Кишинёв центр", "дом", "работа").
        ВОЗВРАЩАЕТ: "Строю маршрут до [адрес]"
    """.trimIndent()
    override val parameters = listOf(
        ToolParam("destination", "string", "Адрес или название места (например 'Кишинёв, центр', 'дом', 'работа')", required = true)
    )
    
    override suspend fun execute(params: Map<String, Any?>): ToolResult {
        val destination = params["destination"] as? String
            ?: return ToolResult.failure("Не указан адрес назначения")
        
        return try {
            // Кодируем адрес для URL
            val encodedDest = Uri.encode(destination)
            val uri = Uri.parse("google.navigation:q=$encodedDest")
            
            val mapsIntent = Intent(Intent.ACTION_VIEW).apply {
                data = uri
                setPackage("com.google.android.apps.maps")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            try {
                context.startActivity(mapsIntent)
                Log.d(TAG, "Маршрут до '$destination' открыт в Google Maps")
                ToolResult.success("Строю маршрут до: $destination")
            } catch (e: Exception) {
                // Если Maps app нет — открываем в браузере
                val browserUri = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$encodedDest")
                val browserIntent = Intent(Intent.ACTION_VIEW, browserUri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(browserIntent)
                Log.d(TAG, "Маршрут до '$destination' открыт в браузере")
                ToolResult.success("Строю маршрут до: $destination (в браузере)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка построения маршрута: ${e.message}", e)
            ToolResult.failure("Не удалось построить маршрут: ${e.message}")
        }
    }
}

/**
 * Инструмент `find_nearby` — найти ближайшие места.
 */
class FindNearbyTool(private val context: Context) : AgentTool {
    companion object { private const val TAG = "FindNearby" }
    
    override val name = "find_nearby"
    override val description = """
        Найти ближайшие места (заправки, парковки, кафе, аптеки и т.д.) в Google Maps.
        ЧТО ДЕЛАЕТ: Открывает Google Maps с поиском ближайших мест указанного типа.
        КОГДА ИСПОЛЬЗОВАТЬ: Когда пользователь просит "найди заправку", "где парковка", "найди кафе рядом", "аптека поблизости".
        ПАРАМЕТРЫ: place_type (обязательно) — тип места (например "заправка", "парковка", "кафе", "аптека", "ресторан", "магазин").
        ВОЗВРАЩАЕТ: "Ищу ближайшие [тип места]"
    """.trimIndent()
    override val parameters = listOf(
        ToolParam("place_type", "string", "Тип места для поиска (например 'заправка', 'парковка', 'кафе', 'аптека', 'ресторан')", required = true)
    )
    
    override suspend fun execute(params: Map<String, Any?>): ToolResult {
        val placeType = params["place_type"] as? String
            ?: return ToolResult.failure("Не указан тип места")
        
        // Маппинг русских типов на английские для Google Maps
        val typeMapping = mapOf(
            "заправк" to "gas+station",
            "бензин" to "gas+station",
            "парковк" to "parking",
            "парковат" to "parking",
            "кафе" to "cafe",
            "ресторан" to "restaurant",
            "аптек" to "pharmacy",
            "магазин" to "supermarket",
            "супермаркет" to "supermarket",
            "банк" to "bank",
            "атм" to "atm",
            "отель" to "hotel",
            "гостиниц" to "hotel"
        )
        
        // Ищем подходящий тип
        val englishType = typeMapping.entries.find { (key, _) -> 
            placeType.lowercase().contains(key)
        }?.value ?: placeType.replace(" ", "+")
        
        return try {
            // geo:0,0?q=заправка (поиск поблизости)
            val uri = Uri.parse("geo:0,0?q=$englishType")
            
            val mapsIntent = Intent(Intent.ACTION_VIEW).apply {
                data = uri
                setPackage("com.google.android.apps.maps")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            try {
                context.startActivity(mapsIntent)
                Log.d(TAG, "Поиск '$placeType' открыт в Google Maps")
                ToolResult.success("Ищу ближайшие: $placeType")
            } catch (e: Exception) {
                // Если Maps app нет — открываем в браузере
                val encodedType = Uri.encode(placeType)
                val browserUri = Uri.parse("https://www.google.com/maps/search/$encodedType")
                val browserIntent = Intent(Intent.ACTION_VIEW, browserUri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(browserIntent)
                Log.d(TAG, "Поиск '$placeType' открыт в браузере")
                ToolResult.success("Ищу ближайшие: $placeType (в браузере)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка поиска: ${e.message}", e)
            ToolResult.failure("Не удалось найти: ${e.message}")
        }
    }
}

/**
 * Инструмент `route_home` — быстрый маршрут домой.
 */
class RouteHomeTool(private val context: Context) : AgentTool {
    companion object { private const val TAG = "RouteHome" }
    
    override val name = "route_home"
    override val description = """
        Быстрый маршрут домой в Google Maps.
        ЧТО ДЕЛАЕТ: Открывает Google Maps с маршрутом до дома (использует сохранённый адрес "дом" в Maps).
        КОГДА ИСПОЛЬЗОВАТЬ: Когда пользователь просит "маршрут домой", "поехали домой", "навигатор домой".
        ПАРАМЕТРЫ: Нет обязательных параметров (используется сохранённый адрес "дом" в Google Maps).
        ВОЗВРАЩАЕТ: "Строю маршрут домой"
        ПРИМЕЧАНИЕ: Работает только если в Google Maps сохранён адрес "дом".
    """.trimIndent()
    override val parameters = emptyList<ToolParam>()
    
    override suspend fun execute(params: Map<String, Any?>): ToolResult {
        return try {
            val uri = Uri.parse("google.navigation:q=home")
            
            val mapsIntent = Intent(Intent.ACTION_VIEW).apply {
                data = uri
                setPackage("com.google.android.apps.maps")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            try {
                context.startActivity(mapsIntent)
                Log.d(TAG, "Маршрут домой открыт в Google Maps")
                ToolResult.success("Строю маршрут домой")
            } catch (e: Exception) {
                val browserUri = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=home")
                val browserIntent = Intent(Intent.ACTION_VIEW, browserUri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(browserIntent)
                Log.d(TAG, "Маршрут домой открыт в браузере")
                ToolResult.success("Строю маршрут домой (в браузере)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка: ${e.message}", e)
            ToolResult.failure("Не удалось построить маршрут домой: ${e.message}")
        }
    }
}

/**
 * Инструмент `route_work` — быстрый маршрут на работу.
 */
class RouteWorkTool(private val context: Context) : AgentTool {
    companion object { private const val TAG = "RouteWork" }
    
    override val name = "route_work"
    override val description = """
        Быстрый маршрут на работу в Google Maps.
        ЧТО ДЕЛАЕТ: Открывает Google Maps с маршрутом до работы (использует сохранённый адрес "работа" в Maps).
        КОГДА ИСПОЛЬЗОВАТЬ: Когда пользователь просит "маршрут на работу", "поехали на работу", "навигатор на работу".
        ПАРАМЕТРЫ: Нет обязательных параметров (используется сохранённый адрес "работа" в Google Maps).
        ВОЗВРАЩАЕТ: "Строю маршрут на работу"
        ПРИМЕЧАНИЕ: Работает только если в Google Maps сохранён адрес "работа".
    """.trimIndent()
    override val parameters = emptyList<ToolParam>()
    
    override suspend fun execute(params: Map<String, Any?>): ToolResult {
        return try {
            val uri = Uri.parse("google.navigation:q=work")
            
            val mapsIntent = Intent(Intent.ACTION_VIEW).apply {
                data = uri
                setPackage("com.google.android.apps.maps")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            try {
                context.startActivity(mapsIntent)
                Log.d(TAG, "Маршрут на работу открыт в Google Maps")
                ToolResult.success("Строю маршрут на работу")
            } catch (e: Exception) {
                val browserUri = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=work")
                val browserIntent = Intent(Intent.ACTION_VIEW, browserUri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(browserIntent)
                Log.d(TAG, "Маршрут на работу открыт в браузере")
                ToolResult.success("Строю маршрут на работу (в браузере)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка: ${e.message}", e)
            ToolResult.failure("Не удалось построить маршрут на работу: ${e.message}")
        }
    }
}
