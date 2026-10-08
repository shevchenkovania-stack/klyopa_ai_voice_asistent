package com.aiagent.ai_voice_agent.engine.tools

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.MediaScannerConnection
import com.aiagent.ai_voice_agent.engine.core.AgentTool
import com.aiagent.ai_voice_agent.engine.core.ToolParam
import com.aiagent.ai_voice_agent.engine.core.ToolResult
import java.io.File
import java.io.FileOutputStream

/**
 * Draw shapes and composite images, save as PNG.
 * Simple: circle, rectangle, line, text, heart, star
 * Composite: sun, person, house, tree, flower, smiley, snowman
 */
class DrawImageTool(private val context: Context) : AgentTool {
    override val name = "draw_image"
    override val description = """
        Нарисовать изображение и сохранить как PNG.
        ЧТО ДЕЛАЕТ: Рисует фигуры и картинки на цветном фоне, сохраняет в галерею.
        КОГДА ИСПОЛЬЗОВАТЬ: "нарисуй сердечко", "нарисуй солнышко", "нарисуй человечка", "нарисуй дом".
        ПРОСТЫЕ ФИГУРЫ: circle, rectangle, line, text, heart, star
        КАРТИНКИ: sun (солнышко), person (человечек), house (домик), tree (дерево), flower (цветок), smiley (смайлик), snowman (снеговик)
        ПАРАМЕТРЫ:
          - shape (string, обязательно) — что нарисовать
          - color (string, опц.) — цвет (по умолчанию 'yellow' для картинок, 'white' для фигур)
          - bg_color (string, опц.) — цвет фона (по умолчанию 'black', для солнышка 'blue' — небо)
          - shape2 (string, опц.) — вторая фигура
          - color2 (string, опц.) — цвет второй фигуры
          - text (string, опц.) — текст для shape='text'
          - filename (string, опц.) — имя файла
          - save_path (string, опц.) — куда сохранить (по умолчанию /sdcard/Pictures/)
          - size (number, опц.) — размер (по умолчанию 512)
        ЦВЕТА: red, blue, green, yellow, white, black, orange, purple, pink, cyan, brown, gray
        ПРИМЕРЫ:
          - "солнышко" → shape=sun, bg_color=blue
          - "человечек" → shape=person
          - "розовое сердечко на белом" → shape=heart, color=pink, bg_color=white
        ВОЗВРАЩАЕТ: "✓ Изображение сохранено: /Pictures/drawing.png"
    """.trimIndent()
    override val parameters = listOf(
        ToolParam(name = "shape", description = "Фигура или картинка: circle, rectangle, line, text, heart, star, sun, person, house, tree, flower, smiley, snowman", type = "string", required = true),
        ToolParam(name = "color", description = "Цвет (по умолчанию зависит от фигуры)", type = "string", required = false),
        ToolParam(name = "bg_color", description = "Цвет фона (по умолчанию 'black')", type = "string", required = false),
        ToolParam(name = "shape2", description = "Вторая фигура другим цветом", type = "string", required = false),
        ToolParam(name = "color2", description = "Цвет второй фигуры", type = "string", required = false),
        ToolParam(name = "text", description = "Текст для shape='text'", type = "string", required = false),
        ToolParam(name = "filename", description = "Имя файла (по умолчанию drawing.png)", type = "string", required = false),
        ToolParam(name = "save_path", description = "Папка (по умолчанию /sdcard/Pictures/)", type = "string", required = false),
        ToolParam(name = "size", description = "Размер в пикселях (по умолчанию 512)", type = "number", required = false)
    )

    override suspend fun execute(params: Map<String, Any?>): ToolResult {
        val shape = params["shape"] as? String ?: return ToolResult.failure("Фигура обязательна")
        val colorName = params["color"] as? String
        val bgColorName = params["bg_color"] as? String ?: "black"
        val shape2 = params["shape2"] as? String
        val color2Name = params["color2"] as? String
        val text = params["text"] as? String ?: ""
        val filename = params["filename"] as? String ?: "drawing.png"
        val size = (params["size"] as? Number)?.toInt() ?: 512
        val savePath = params["save_path"] as? String

        // Default colors for composite images
        val defaultColor = when (shape.lowercase()) {
            "sun", "солнышко", "солнце" -> "yellow"
            "tree", "дерево" -> "green"
            "flower", "цветок" -> "red"
            "snowman", "снеговик" -> "white"
            "smiley", "смайлик" -> "yellow"
            else -> "white"
        }
        val color = parseColor(colorName ?: defaultColor)
        val bgColor = parseColor(bgColorName)

        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(bgColor)

        val center = size / 2f
        val radius = size / 3f

        // Draw main shape
        drawShape(canvas, shape, color, center, center, radius, size, text)

        // Draw second shape if specified
        if (shape2 != null) {
            val color2 = parseColor(color2Name ?: "white")
            drawShape(canvas, shape2, color2, center + radius * 0.4f, center + radius * 0.4f, radius * 0.6f, size, text)
        }

        val dir = resolveSaveDir(savePath)
        dir.mkdirs()
        val file = File(dir, filename)

        return try {
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            bitmap.recycle()
            scanMedia(file)

            val relativePath = file.absolutePath.replace("/sdcard/", "/")
            ToolResult.success("✓ Изображение сохранено: $relativePath\n$shape на $bgColorName фоне, ${size}x${size}px")
        } catch (e: Exception) {
            ToolResult.failure("Не удалось сохранить: ${e.message}")
        }
    }

    private fun drawShape(canvas: Canvas, shape: String, color: Int, cx: Float, cy: Float, radius: Float, size: Int, text: String) {
        val fillPaint = Paint().apply { this.color = color; isAntiAlias = true; style = Paint.Style.FILL }
        val strokePaint = Paint().apply { this.color = color; isAntiAlias = true; strokeWidth = size / 20f; style = Paint.Style.STROKE }

        when (shape.lowercase()) {
            // === Simple shapes ===
            "circle", "круг" -> canvas.drawCircle(cx, cy, radius, fillPaint)
            "rectangle", "rect", "квадрат", "прямоугольник" -> canvas.drawRect(cx - radius, cy - radius, cx + radius, cy + radius, fillPaint)
            "line", "линия", "линию" -> canvas.drawLine(cx - radius, cy, cx + radius, cy, strokePaint)
            "heart", "сердце", "сердечко" -> drawHeart(canvas, cx, cy, radius, fillPaint)
            "star", "звезда", "звёздочка", "звездочка" -> drawStar(canvas, cx, cy, radius, fillPaint)
            "text", "текст" -> {
                if (text.isNotEmpty()) {
                    val textPaint = Paint().apply { this.color = color; isAntiAlias = true; textSize = size / 10f; textAlign = Paint.Align.CENTER }
                    canvas.drawText(text, cx, cy, textPaint)
                }
            }
            // === Composite images ===
            "sun", "солнышко", "солнце" -> drawSun(canvas, cx, cy, radius, size, color)
            "person", "человечек", "человек", "чел" -> drawPerson(canvas, cx, cy, radius, size, color)
            "house", "дом", "домик" -> drawHouse(canvas, cx, cy, radius, size, color)
            "tree", "дерево" -> drawTree(canvas, cx, cy, radius, size, color)
            "flower", "цветок" -> drawFlower(canvas, cx, cy, radius, size, color)
            "smiley", "смайлик", "рожица", "лицо" -> drawSmiley(canvas, cx, cy, radius, size, color)
            "snowman", "снеговик" -> drawSnowman(canvas, cx, cy, radius, size, color)
        }
    }

    // === COMPOSITE DRAWINGS ===

    private fun drawSun(canvas: Canvas, cx: Float, cy: Float, r: Float, size: Int, color: Int) {
        val fill = Paint().apply { this.color = color; isAntiAlias = true; style = Paint.Style.FILL }
        val stroke = Paint().apply { this.color = color; isAntiAlias = true; strokeWidth = size / 25f; style = Paint.Style.STROKE }
        // Sun body
        canvas.drawCircle(cx, cy, r * 0.5f, fill)
        // Rays
        for (i in 0 until 12) {
            val angle = Math.toRadians(i * 30.0)
            val x1 = cx + (r * 0.6f * Math.cos(angle)).toFloat()
            val y1 = cy + (r * 0.6f * Math.sin(angle)).toFloat()
            val x2 = cx + (r * 0.95f * Math.cos(angle)).toFloat()
            val y2 = cy + (r * 0.95f * Math.sin(angle)).toFloat()
            canvas.drawLine(x1, y1, x2, y2, stroke)
        }
    }

    private fun drawPerson(canvas: Canvas, cx: Float, cy: Float, r: Float, size: Int, color: Int) {
        val stroke = Paint().apply { this.color = color; isAntiAlias = true; strokeWidth = size / 25f; style = Paint.Style.STROKE }
        val fill = Paint().apply { this.color = color; isAntiAlias = true; style = Paint.Style.FILL }
        val headR = r * 0.2f
        // Head
        canvas.drawCircle(cx, cy - r * 0.6f, headR, fill)
        // Body
        canvas.drawLine(cx, cy - r * 0.4f, cx, cy + r * 0.2f, stroke)
        // Arms
        canvas.drawLine(cx - r * 0.4f, cy - r * 0.1f, cx + r * 0.4f, cy - r * 0.1f, stroke)
        // Left leg
        canvas.drawLine(cx, cy + r * 0.2f, cx - r * 0.35f, cy + r * 0.7f, stroke)
        // Right leg
        canvas.drawLine(cx, cy + r * 0.2f, cx + r * 0.35f, cy + r * 0.7f, stroke)
    }

    private fun drawHouse(canvas: Canvas, cx: Float, cy: Float, r: Float, size: Int, color: Int) {
        val fill = Paint().apply { this.color = color; isAntiAlias = true; style = Paint.Style.FILL }
        val stroke = Paint().apply { this.color = color; isAntiAlias = true; strokeWidth = size / 30f; style = Paint.Style.STROKE }
        // Walls
        canvas.drawRect(cx - r * 0.6f, cy - r * 0.1f, cx + r * 0.6f, cy + r * 0.7f, fill)
        // Roof (triangle)
        val roofPath = android.graphics.Path()
        roofPath.moveTo(cx - r * 0.75f, cy - r * 0.1f)
        roofPath.lineTo(cx, cy - r * 0.7f)
        roofPath.lineTo(cx + r * 0.75f, cy - r * 0.1f)
        roofPath.close()
        canvas.drawPath(roofPath, fill)
        // Door
        val doorPaint = Paint().apply { this.color = Color.parseColor("#5D4037"); isAntiAlias = true; style = Paint.Style.FILL }
        canvas.drawRect(cx - r * 0.15f, cy + r * 0.2f, cx + r * 0.15f, cy + r * 0.7f, doorPaint)
        // Window
        val winPaint = Paint().apply { this.color = Color.parseColor("#81D4FA"); isAntiAlias = true; style = Paint.Style.FILL }
        canvas.drawRect(cx + r * 0.25f, cy + r * 0.05f, cx + r * 0.5f, cy + r * 0.3f, winPaint)
    }

    private fun drawTree(canvas: Canvas, cx: Float, cy: Float, r: Float, size: Int, color: Int) {
        val fill = Paint().apply { this.color = color; isAntiAlias = true; style = Paint.Style.FILL }
        // Trunk
        val trunkPaint = Paint().apply { this.color = Color.parseColor("#5D4037"); isAntiAlias = true; style = Paint.Style.FILL }
        canvas.drawRect(cx - r * 0.1f, cy + r * 0.2f, cx + r * 0.1f, cy + r * 0.8f, trunkPaint)
        // Crown (3 circles)
        canvas.drawCircle(cx, cy - r * 0.2f, r * 0.4f, fill)
        canvas.drawCircle(cx - r * 0.3f, cy + r * 0.05f, r * 0.3f, fill)
        canvas.drawCircle(cx + r * 0.3f, cy + r * 0.05f, r * 0.3f, fill)
    }

    private fun drawFlower(canvas: Canvas, cx: Float, cy: Float, r: Float, size: Int, color: Int) {
        val fill = Paint().apply { this.color = color; isAntiAlias = true; style = Paint.Style.FILL }
        val stemPaint = Paint().apply { this.color = Color.GREEN; isAntiAlias = true; strokeWidth = size / 30f; style = Paint.Style.STROKE }
        // Stem
        canvas.drawLine(cx, cy, cx, cy + r * 0.8f, stemPaint)
        // Petals (5 circles around center)
        val petalR = r * 0.25f
        for (i in 0 until 5) {
            val angle = Math.toRadians(i * 72.0 - 90.0)
            val px = cx + (r * 0.3f * Math.cos(angle)).toFloat()
            val py = cy - r * 0.15f + (r * 0.3f * Math.sin(angle)).toFloat()
            canvas.drawCircle(px, py, petalR, fill)
        }
        // Center
        val centerPaint = Paint().apply { this.color = Color.YELLOW; isAntiAlias = true; style = Paint.Style.FILL }
        canvas.drawCircle(cx, cy - r * 0.15f, r * 0.15f, centerPaint)
    }

    private fun drawSmiley(canvas: Canvas, cx: Float, cy: Float, r: Float, size: Int, color: Int) {
        val fill = Paint().apply { this.color = color; isAntiAlias = true; style = Paint.Style.FILL }
        val stroke = Paint().apply { this.color = Color.BLACK; isAntiAlias = true; strokeWidth = size / 30f; style = Paint.Style.STROKE }
        // Face
        canvas.drawCircle(cx, cy, r * 0.7f, fill)
        // Eyes
        val eyePaint = Paint().apply { this.color = Color.BLACK; isAntiAlias = true; style = Paint.Style.FILL }
        canvas.drawCircle(cx - r * 0.25f, cy - r * 0.15f, r * 0.08f, eyePaint)
        canvas.drawCircle(cx + r * 0.25f, cy - r * 0.15f, r * 0.08f, eyePaint)
        // Smile
        val smilePath = android.graphics.Path()
        smilePath.moveTo(cx - r * 0.35f, cy + r * 0.1f)
        smilePath.quadTo(cx, cy + r * 0.5f, cx + r * 0.35f, cy + r * 0.1f)
        canvas.drawPath(smilePath, stroke)
    }

    private fun drawSnowman(canvas: Canvas, cx: Float, cy: Float, r: Float, size: Int, color: Int) {
        val fill = Paint().apply { this.color = color; isAntiAlias = true; style = Paint.Style.FILL }
        val stroke = Paint().apply { this.color = Color.BLACK; isAntiAlias = true; strokeWidth = size / 40f; style = Paint.Style.STROKE }
        // Bottom ball
        canvas.drawCircle(cx, cy + r * 0.4f, r * 0.45f, fill)
        canvas.drawCircle(cx, cy + r * 0.4f, r * 0.45f, stroke)
        // Middle ball
        canvas.drawCircle(cx, cy - r * 0.15f, r * 0.32f, fill)
        canvas.drawCircle(cx, cy - r * 0.15f, r * 0.32f, stroke)
        // Head
        canvas.drawCircle(cx, cy - r * 0.55f, r * 0.22f, fill)
        canvas.drawCircle(cx, cy - r * 0.55f, r * 0.22f, stroke)
        // Eyes
        val eyePaint = Paint().apply { this.color = Color.BLACK; isAntiAlias = true; style = Paint.Style.FILL }
        canvas.drawCircle(cx - r * 0.08f, cy - r * 0.6f, r * 0.03f, eyePaint)
        canvas.drawCircle(cx + r * 0.08f, cy - r * 0.6f, r * 0.03f, eyePaint)
        // Nose (carrot)
        val nosePaint = Paint().apply { this.color = Color.parseColor("#FF9800"); isAntiAlias = true; style = Paint.Style.FILL }
        val nosePath = android.graphics.Path()
        nosePath.moveTo(cx, cy - r * 0.55f)
        nosePath.lineTo(cx + r * 0.2f, cy - r * 0.52f)
        nosePath.lineTo(cx, cy - r * 0.48f)
        nosePath.close()
        canvas.drawPath(nosePath, nosePaint)
    }

    // === SIMPLE SHAPES ===

    private fun drawHeart(canvas: Canvas, cx: Float, cy: Float, radius: Float, paint: Paint) {
        val path = android.graphics.Path()
        val topY = cy - radius * 0.5f
        path.moveTo(cx, cy + radius * 0.8f)
        path.cubicTo(cx - radius * 1.5f, cy - radius * 0.2f, cx - radius * 0.8f, topY - radius * 0.5f, cx, topY + radius * 0.3f)
        path.cubicTo(cx + radius * 0.8f, topY - radius * 0.5f, cx + radius * 1.5f, cy - radius * 0.2f, cx, cy + radius * 0.8f)
        path.close()
        canvas.drawPath(path, paint)
    }

    private fun drawStar(canvas: Canvas, cx: Float, cy: Float, radius: Float, paint: Paint) {
        val path = android.graphics.Path()
        val innerRadius = radius * 0.4f
        for (i in 0 until 10) {
            val angle = Math.toRadians(i * 36.0 - 90.0)
            val r = if (i % 2 == 0) radius else innerRadius
            val x = cx + (r * Math.cos(angle)).toFloat()
            val y = cy + (r * Math.sin(angle)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        canvas.drawPath(path, paint)
    }

    // === UTILS ===

    private fun resolveSaveDir(savePath: String?): File {
        if (savePath == null) return File("/sdcard/Pictures")
        return when {
            savePath.startsWith("/") -> File(savePath)
            savePath.contains("document", true) || savePath.contains("документ", true) -> File("/sdcard/Documents")
            savePath.contains("download", true) || savePath.contains("загрузк", true) -> File("/sdcard/Download")
            savePath.contains("picture", true) || savePath.contains("фото", true) || savePath.contains("галере", true) -> File("/sdcard/Pictures")
            else -> File("/sdcard/Pictures/$savePath")
        }
    }

    private fun scanMedia(file: File) {
        try {
            MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf("image/png")) { _, uri ->
                android.util.Log.d("DrawImage", "Media scanned: $uri")
            }
        } catch (e: Exception) {
            android.util.Log.w("DrawImage", "Media scan failed: ${e.message}")
        }
    }

    private fun parseColor(name: String): Int = when (name.lowercase()) {
        "red", "красный", "красн" -> Color.RED
        "blue", "синий", "син" -> Color.BLUE
        "green", "зелёный", "зеленый" -> Color.GREEN
        "yellow", "жёлтый", "желтый" -> Color.YELLOW
        "white", "белый" -> Color.WHITE
        "black", "чёрный", "черный" -> Color.BLACK
        "orange", "оранжевый" -> Color.parseColor("#FF9800")
        "purple", "фиолетовый", "пурпурный" -> Color.parseColor("#9C27B0")
        "pink", "розовый" -> Color.parseColor("#E91E63")
        "cyan", "голубой", "бирюзовый" -> Color.CYAN
        "brown", "коричневый" -> Color.parseColor("#795548")
        "gray", "grey", "серый" -> Color.GRAY
        else -> Color.WHITE
    }
}
