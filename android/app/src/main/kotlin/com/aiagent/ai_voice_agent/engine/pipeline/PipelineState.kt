package com.aiagent.ai_voice_agent.engine.pipeline

/**
 * Единая state machine для всего голосового pipeline.
 * Используется EngineManager, PipelineOrchestrator и ContinuousDialogueManager.
 *
 * Глобальный flow:
 * IDLE → WAKE_WORD → LISTENING → STT_RUNNING → AGENT_RUNNING → TOOL_EXECUTING → TTS_SPEAKING → IDLE
 *
 * Continuous dialogue loop (внутри сессии):
 * LISTENING → SPEECH_RECORDING → PROCESSING → TTS_SPEAKING → LISTENING
 *                                    ↑ BARGE_IN (user interrupts TTS)
 *                                    ↑ WAITING_FOR_CONFIRMATION (PermissionManager)
 */
enum class PipelineState {
    IDLE,                    // Engine inactive
    WAKE_WORD,              // Wake word detector listening
    LISTENING,              // Mic active, waiting for speech (continuous mode)
    SPEECH_RECORDING,       // Speech detected, recording in progress
    STT_RUNNING,            // Speech-to-text processing
    PROCESSING,             // Agent running (LLM + tools)
    AGENT_RUNNING,          // Alias-compatible: agent logic executing
    TOOL_EXECUTING,         // Tool execution in progress
    TTS_SPEAKING,           // TTS output playing
    BARGE_IN,               // User interrupted TTS by speaking
    CANCELLING,             // User cancelled pipeline
    WAITING_FOR_CONFIRMATION, // PermissionManager waiting for user approval
    CANCELLED,              // Pipeline cancelled
    ERROR                   // Pipeline error
}
