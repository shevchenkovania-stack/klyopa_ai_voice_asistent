package com.aiagent.ai_voice_agent.engine.wakeword

import android.content.Context
import android.util.Log
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipInputStream

class VoskWakeWordDetector(private val context: Context) {
    companion object {
        const val TAG = "VoskWakeWordDetector"
        const val SAMPLE_RATE = 16000f
        const val MODEL_DIR = "vosk-model"
        const val MODEL_ZIP = "vosk-model-small-ru.zip"
        const val MODEL_SUBDIR = "vosk-model-small-ru-0.22"
    }

    var onWakeWordDetected: ((String) -> Unit)? = null
    var onPartialResult: ((String) -> Unit)? = null

    private var model: Model? = null
    private var recognizer: Recognizer? = null
    private val recognizerLock = Any()
    private var wakeWord: String = ""

    @Volatile
    private var wakeWordTriggered = false
    private var lastTriggerTimeMs = 0L

    val isReady: Boolean get() = recognizer != null

    fun load(): Boolean {
        return try {
            val modelPath = extractModelIfNeeded()
            if (modelPath == null) {
                Log.e(TAG, "FAILED to extract model from assets")
                return false
            }
            model = Model(modelPath)
            Log.i(TAG, "Model loaded: $modelPath")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Model load FAILED: ${e.message}", e)
            false
        }
    }

    fun setWakeWord(word: String) {
        wakeWord = word.lowercase().trim()
        Log.i(TAG, "Wake word = '$wakeWord'")
        recreateRecognizer()
    }
    private fun recreateRecognizer() {
        synchronized(recognizerLock) {
            try {
                recognizer?.close()
                recognizer = null
                val m = model ?: return
                recognizer = Recognizer(m, SAMPLE_RATE)
                recognizer?.setPartialWords(true)
                Log.i(TAG, "Standard recognizer created (16kHz)")
            } catch (e: IOException) {
                Log.e(TAG, "Recognizer creation FAILED: ${e.message}", e)
            }
        }
    }

    private var diagFrameCount = 0L
    fun feedAudio(buffer: ShortArray, len: Int) {
        try {
            synchronized(recognizerLock) {
                val r = recognizer ?: return
                diagFrameCount++
                // Диагностика амплитуды каждые 200 фреймов
                if (diagFrameCount % 200 == 1L) {
                    val maxAmp = buffer.take(len).maxOrNull() ?: 0
                    Log.d(TAG, "[DIAG] feedAudio frame#$diagFrameCount: len=$len, maxAmp=$maxAmp")
                }
                if (r.acceptWaveForm(buffer, len)) {
                    val json = r.getResult()
                    handleResult(json)
                }
                val partial = r.getPartialResult()
                if (diagFrameCount % 200 == 1L) {
                    Log.d(TAG, "[DIAG] partial result: '$partial'")
                }
                handlePartial(partial)
            }
        } catch (e: Exception) {
            Log.e(TAG, "feedAudio error: ${e.message}", e)
        }
    }

    fun release() {
        synchronized(recognizerLock) {
            try { recognizer?.close() } catch (_: Exception) {}
            recognizer = null
        }
        try { model?.close() } catch (_: Exception) {}
        model = null
    }

    /**
     * Сбросить debounce после завершения pipeline.
     * Вызывать из EngineManager после onWakeWordDetected обработки.
     */
    fun resetTrigger() {
        wakeWordTriggered = false
        Log.d(TAG, "Wake word trigger reset")
    }

    private fun handleResult(json: String) {
        try {
            val text = JSONObject(json).optString("text", "").lowercase().trim()
            if (text.isEmpty()) return
            Log.d(TAG, "FINAL: '$text'")
            if (wakeWord.isNotEmpty() && checkAndTriggerWakeWord(text, "final")) {
                Log.i(TAG, "*** WAKE WORD DETECTED (final): '$text' ***")
            }
        } catch (e: Exception) {
            Log.e(TAG, "handleResult error: ${e.message}")
        }
    }

    private fun handlePartial(json: String) {
        try {
            val text = JSONObject(json).optString("partial", "").lowercase().trim()
            if (text.isEmpty()) return
            Log.d(TAG, "PARTIAL: '$text'")
            onPartialResult?.invoke(text)
            if (wakeWord.isNotEmpty() && checkAndTriggerWakeWord(text, "partial")) {
                Log.i(TAG, "*** WAKE WORD DETECTED (partial): '$text' ***")
            }
        } catch (e: Exception) {
            Log.e(TAG, "handlePartial error: ${e.message}")
        }
    }

    /**
     * Проверяет текст на wake word с debounce.
     * Возвращает true если wake word обнаружен И не был уже trigger'нут.
     */
    private fun checkAndTriggerWakeWord(text: String, source: String): Boolean {
        if (wakeWordTriggered) return false
        if (!matchWakeWord(text)) return false

        // Дополнительный debounce: 3 секунды между trigger'ами
        val now = System.currentTimeMillis()
        if (now - lastTriggerTimeMs < 3000L) return false

        wakeWordTriggered = true
        lastTriggerTimeMs = now
        onWakeWordDetected?.invoke(text)
        return true
    }

    private fun matchWakeWord(text: String): Boolean {
        if (text.contains(wakeWord)) return true

        val normalizedWake = wakeWord.replace("ё", "е")
        val normalizedText = text.replace("ё", "е")
        if (normalizedText.contains(normalizedWake)) {
            Log.d(TAG, "Fuzzy match (ё→е): '$text' ≈ '$wakeWord'")
            return true
        }

        val words = text.split("\\s+".toRegex()).filter { it.length >= 3 }
        for (word in words) {
            if (levenshtein(word, normalizedWake) <= 2) {
                Log.d(TAG, "Fuzzy match (edit distance): '$word' ≈ '$wakeWord'")
                return true
            }
            val normWord = word.replace("ё", "е")
            if (normWord != word && levenshtein(normWord, normalizedWake) <= 2) {
                Log.d(TAG, "Fuzzy match (edit distance+ё): '$word' ≈ '$wakeWord'")
                return true
            }
        }

        val wlen = normalizedWake.length
        if (text.length >= wlen - 1) {
            for (i in 0..text.length - wlen) {
                val segment = text.substring(i, i + wlen)
                val dist = levenshtein(segment, normalizedWake)
                if (dist <= 2) {
                    Log.d(TAG, "Fuzzy match (segment): '$segment' ≈ '$wakeWord' (dist=$dist)")
                    return true
                }
            }
        }

        return false
    }

    private fun levenshtein(a: String, b: String): Int {
        val m = a.length
        val n = b.length
        val dp = IntArray(n + 1) { it }
        for (i in 1..m) {
            var prev = dp[0]
            dp[0] = i
            for (j in 1..n) {
                val temp = dp[j]
                dp[j] = minOf(
                    dp[j] + 1,
                    dp[j - 1] + 1,
                    prev + (if (a[i - 1] == b[j - 1]) 0 else 1)
                )
                prev = temp
            }
        }
        return dp[n]
    }

    private fun extractModelIfNeeded(): String? {
        val baseDir = File(context.filesDir, MODEL_DIR)
        val modelDir = File(baseDir, MODEL_SUBDIR)

        if (modelDir.exists() && File(modelDir, "am").exists()) {
            Log.i(TAG, "Already extracted at ${modelDir.absolutePath}")
            return modelDir.absolutePath
        }

        Log.i(TAG, "Extracting $MODEL_ZIP (46 MB) from assets...")
        return try {
            baseDir.mkdirs()
            context.assets.open(MODEL_ZIP).use { stream ->
                val zis = ZipInputStream(stream)
                val buf = ByteArray(8192)
                var entry = zis.nextEntry
                var totalBytes = 0L
                while (entry != null) {
                    val target = File(baseDir, entry.name)
                    if (entry.isDirectory) {
                        target.mkdirs()
                    } else {
                        target.parentFile?.mkdirs()
                        FileOutputStream(target).use { fos ->
                            var read: Int
                            while (zis.read(buf).also { read = it } != -1) {
                                fos.write(buf, 0, read)
                                totalBytes += read
                            }
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
                Log.i(TAG, "Extraction complete: $totalBytes bytes")
            }
            if (modelDir.exists() && File(modelDir, "am").exists()) {
                Log.i(TAG, "Model ready at ${modelDir.absolutePath}")
                modelDir.absolutePath
            } else {
                Log.e(TAG, "Extraction done but model dir not found at ${modelDir.absolutePath}")
                baseDir.listFiles()?.forEach { Log.e(TAG, "  entry: ${it.name}") }
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Extraction FAILED: ${e.message}", e)
            null
        }
    }
}
