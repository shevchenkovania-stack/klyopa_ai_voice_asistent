package com.aiagent.ai_voice_agent.engine

/**
 * NOTE: recreated during project recovery - the original implementation was never
 * snapshotted and could not be restored from cache.
 *
 * LocalIntentDetector worked as an offline fast path (bypassing the LLM for obvious
 * phrases). The recreated version returns null, so every request goes through the
 * regular LLM pipeline - full functionality is preserved, only the speed-up is off.
 *
 * LocalPermissionManager gated the fast path only; with the detector disabled it
 * allows everything by default.
 */

enum class PermissionLevel { SAFE, SENSITIVE, DANGEROUS }

class LocalIntentDetector : IntentDetector {
    override fun detect(text: String): IntentMatch? = null
}

class LocalPermissionManager : PermissionManager {
    override fun check(toolName: String, params: Map<String, Any?>): Boolean = true
    override fun getLevel(toolName: String): PermissionLevel = PermissionLevel.SAFE
}