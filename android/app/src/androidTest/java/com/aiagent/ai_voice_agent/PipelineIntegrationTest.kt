package com.aiagent.ai_voice_agent

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aiagent.ai_voice_agent.engine.EngineManager
import com.aiagent.ai_voice_agent.engine.session.AssistantSessionManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

/**
 * Pipeline Integration Test — без голоса.
 *
 * Тестирует полный пайплайн: WAV → STT → Agent → TTS callback
 * Проверяет переходы state machine: IDLE → LISTENING → THINKING → SPEAKING → CONVERSING
 */
@RunWith(AndroidJUnit4::class)
class PipelineIntegrationTest {

    private lateinit var context: Context
    private lateinit var wavFile: File
    private val stateLog = mutableListOf<String>()

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        
        // Копируем WAV из assets в cache
        wavFile = File(context.cacheDir, "test_hello.wav")
        context.assets.open("test_hello.wav").use { input ->
            FileOutputStream(wavFile).use { output ->
                input.copyTo(output)
            }
        }
        assertTrue("WAV file must exist", wavFile.exists())
        assertTrue("WAV file must not be empty", wavFile.length() > 0)

        // Инициализируем движок
        EngineManager.initialize(context)
        
        // Сбрасываем state machine в IDLE
        AssistantSessionManager.startInIdle()
        assertEquals("Initial state must be IDLE", AssistantSessionManager.State.IDLE, AssistantSessionManager.currentState)
    }

    @Test
    fun testFullPipelineWithoutVoice() = runBlocking {
        // Arrange — логируем переходы состояний
        stateLog.clear()
        AssistantSessionManager.onStateChanged = { old, new ->
            stateLog.add(new.name)
            println("[TEST] State: $old → $new")
        }

        // Перехватываем TTS — не озвучиваем, просто ловим факт
        var ttsTriggered = false
        val originalOnTtsStarted = EngineManager.pipeline.onTtsStarted
        EngineManager.pipeline.onTtsStarted = {
            ttsTriggered = true
            println("[TEST] TTS started")
        }

        // Act — запускаем pipeline с WAV-файлом
        println("[TEST] Starting pipeline with WAV: ${wavFile.absolutePath} (${wavFile.length()} bytes)")
        
        // Симулируем wake word detected → LISTENING
        AssistantSessionManager.handleEvent(AssistantSessionManager.Event.WAKE_WORD_DETECTED)
        assertEquals("State must be LISTENING", AssistantSessionManager.State.LISTENING, AssistantSessionManager.currentState)

        // Запускаем pipeline
        EngineManager.pipeline.processContinuousSpeech(wavFile, onResumeWakeWord = {})

        // Assert — проверяем переходы
        println("[TEST] State log: $stateLog")
        println("[TEST] Final state: ${AssistantSessionManager.currentState}")
        println("[TEST] TTS triggered: $ttsTriggered")

        // State machine должна пройти через THINKING
        assertTrue("Must pass through THINKING", stateLog.contains("THINKING"))
        
        // TTS должен быть вызван (если API работает)
        // Примечание: если API quota exceeded, TTS может не вызваться
        if (ttsTriggered) {
            assertTrue("Must pass through SPEAKING", stateLog.contains("SPEAKING"))
        }

        // Cleanup
        AssistantSessionManager.onStateChanged = null
        EngineManager.pipeline.onTtsStarted = originalOnTtsStarted
        wavFile.delete()
    }

    @Test
    fun testStateTransitions() {
        // Arrange
        stateLog.clear()
        AssistantSessionManager.onStateChanged = { _, new ->
            stateLog.add(new.name)
        }

        // Act — симулируем полный цикл
        AssistantSessionManager.startInIdle()
        AssistantSessionManager.handleEvent(AssistantSessionManager.Event.WAKE_WORD_DETECTED)
        AssistantSessionManager.handleEvent(AssistantSessionManager.Event.SPEECH_END)
        AssistantSessionManager.handleEvent(AssistantSessionManager.Event.TTS_STARTED)
        AssistantSessionManager.handleEvent(AssistantSessionManager.Event.TTS_FINISHED)

        // Assert
        println("[TEST] State transitions: $stateLog")
        assertEquals("Expected 4 transitions", 4, stateLog.size)
        assertEquals("1st: LISTENING", "LISTENING", stateLog[0])
        assertEquals("2nd: THINKING", "THINKING", stateLog[1])
        assertEquals("3rd: SPEAKING", "SPEAKING", stateLog[2])
        assertEquals("4th: CONVERSING", "CONVERSING", stateLog[3])

        // Cleanup
        AssistantSessionManager.onStateChanged = null
    }
}
