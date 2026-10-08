package com.aiagent.ai_voice_agent.engine.session

import android.util.Log
import com.aiagent.ai_voice_agent.engine.audio.AudioSessionManager
import com.aiagent.ai_voice_agent.services.WakeWordService

/**
 * WakeWordAudioConsumer — адаптер между AudioSessionManager и WakeWordService.
 *
 * Единственная задача: передать PCM-данные в "тупой" WakeWordService.
 * WakeWordService больше НЕ управляет микрофоном — он просто процессор.
 */
class WakeWordAudioConsumer : AudioSessionManager.AudioConsumer {

    companion object {
        private const val TAG = "WakeWordConsumer"
    }

    override val id = "wakeword"

    override fun onActivated() {
        // Vosk уже загружен — просто сбрасываем trigger
        WakeWordService.resetTrigger()
        Log.d(TAG, "Activated — wake word listening")
    }

    override fun onDeactivated() {
        // Ничего не останавливаем — просто перестанем получать PCM
        Log.d(TAG, "Deactivated")
    }

    override fun onAudioData(buffer: ShortArray, read: Int) {
        // PCM → Vosk → (если "Клёпа") → onWakeWordDetected
        WakeWordService.feedAudioBuffer(buffer, read)
    }
}
