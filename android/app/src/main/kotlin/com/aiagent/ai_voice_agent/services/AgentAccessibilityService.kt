package com.aiagent.ai_voice_agent.services

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * AccessibilityService — agent's "eyes and hands" on the screen.
 * Enables: reading screen content, clicking elements, typing text, scrolling, navigation.
 * This is what transforms the project from "voice assistant" to "AI OS".
 */
class AgentAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "AccessibilityService"

        // Singleton reference for tools to access
        var instance: AgentAccessibilityService? = null
            private set

        var lastWindowContent: String? = null
            private set

        var lastPackageName: String? = null
            private set

        fun isServiceRunning(): Boolean = instance != null

        /**
         * Read the current screen content as structured text.
         */
        fun readScreen(): String {
            val svc = instance ?: return "AccessibilityService не запущен. Включи его в настройках специальных возможностей."
            val root = svc.rootInActiveWindow ?: return "Нет активного окна"
            return try {
                val sb = StringBuilder()
                val pkg = svc.rootInActiveWindow?.packageName?.toString() ?: "unknown"
                sb.appendLine("Приложение: $pkg")
                sb.appendLine("---")
                traverseNode(root, sb, 0)
                sb.toString().take(3000) // Limit output size
            } catch (e: Exception) {
                "Ошибка чтения экрана: ${e.message}"
            } finally {
                root.recycle()
            }
        }

        /**
         * Find a node by text (partial match, case-insensitive).
         */
        fun findNodeByText(text: String): AccessibilityNodeInfo? {
            val svc = instance ?: return null
            val root = svc.rootInActiveWindow ?: return null
            return try {
                searchNode(root, text.lowercase())
            } catch (e: Exception) {
                Log.e(TAG, "findNodeByText error: ${e.message}")
                null
            } finally {
                // Don't recycle root here — caller may use the returned node
            }
        }

        /**
         * Click on a node found by text.
         */
        fun clickByText(text: String): Boolean {
            val node = findNodeByText(text) ?: return false
            return try {
                if (node.isClickable) {
                    node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                } else {
                    // Try parent
                    var parent = node.parent
                    while (parent != null && !parent.isClickable) {
                        parent = parent.parent
                    }
                    parent?.performAction(AccessibilityNodeInfo.ACTION_CLICK) ?: false
                }
            } catch (e: Exception) {
                Log.e(TAG, "clickByText error: ${e.message}")
                false
            }
        }

        /**
         * Type text into the focused field (or find field by label and focus it first).
         */
        fun typeText(text: String, fieldLabel: String? = null): Boolean {
            val svc = instance ?: return false
            return try {
                val target = if (fieldLabel != null) {
                    val node = findNodeByText(fieldLabel) ?: return false
                    // Find editable field near the label
                    val editable = findEditableNear(node) ?: node
                    editable.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                    editable
                } else {
                    svc.rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
                } ?: return false

                // Use clipboard to paste text (works for all fields)
                val clipboard = svc.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("agent", text))
                target.performAction(AccessibilityNodeInfo.ACTION_PASTE)
            } catch (e: Exception) {
                Log.e(TAG, "typeText error: ${e.message}")
                false
            }
        }

        /**
         * Perform global action: back, home, recents, notifications, quick_settings.
         */
        fun globalAction(action: String): Boolean {
            val svc = instance ?: return false
            val ga = when (action.lowercase()) {
                "back" -> AccessibilityService.GLOBAL_ACTION_BACK
                "home" -> AccessibilityService.GLOBAL_ACTION_HOME
                "recents" -> AccessibilityService.GLOBAL_ACTION_RECENTS
                "notifications" -> AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS
                "quick_settings" -> AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS
                "power_dialog" -> AccessibilityService.GLOBAL_ACTION_POWER_DIALOG
                else -> return false
            }
            return svc.performGlobalAction(ga)
        }

        /**
         * Scroll in the current window or in a scrollable container.
         */
        fun scroll(direction: String = "down"): Boolean {
            val svc = instance ?: return false
            val root = svc.rootInActiveWindow ?: return false
            return try {
                val scrollable = findScrollable(root) ?: return false
                val action = when (direction.lowercase()) {
                    "down", "next" -> AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                    "up", "previous" -> AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
                    else -> AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                }
                scrollable.performAction(action)
            } catch (e: Exception) {
                Log.e(TAG, "scroll error: ${e.message}")
                false
            }
        }

        /**
         * List all clickable elements on screen with their text.
         */
        fun listClickableElements(): String {
            val root = instance?.rootInActiveWindow ?: return "AccessibilityService не запущен"
            return try {
                val elements = mutableListOf<String>()
                collectClickable(root, elements)
                if (elements.isEmpty()) {
                    "Нет кликабельных элементов"
                } else {
                    elements.take(30).joinToString("\n") { "- $it" }
                }
            } finally {
                root.recycle()
            }
        }

        // ==================== PRIVATE HELPERS ====================

        private fun traverseNode(node: AccessibilityNodeInfo, sb: StringBuilder, depth: Int) {
            if (depth > 10) return // Prevent infinite recursion

            val indent = "  ".repeat(depth)
            val className = node.className?.toString()?.substringAfterLast('.') ?: ""
            val text = node.text?.toString() ?: ""
            val contentDesc = node.contentDescription?.toString() ?: ""
            val hint = node.hintText?.toString() ?: ""

            val displayText = text.ifEmpty { contentDesc }.ifEmpty { hint }

            if (displayText.isNotEmpty()) {
                val clickable = if (node.isClickable) " [btn]" else ""
                val editable = if (node.isEditable) " [input]" else ""
                val scrollable = if (node.isScrollable) " [scroll]" else ""
                sb.appendLine("$indent$className: $displayText$clickable$editable$scrollable")
            }

            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                traverseNode(child, sb, depth + 1)
                child.recycle()
            }
        }

        private fun searchNode(node: AccessibilityNodeInfo, query: String): AccessibilityNodeInfo? {
            val text = node.text?.toString()?.lowercase() ?: ""
            val contentDesc = node.contentDescription?.toString()?.lowercase() ?: ""

            if (text.contains(query) || contentDesc.contains(query)) {
                return node
            }

            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                val result = searchNode(child, query)
                if (result != null) return result
                // Don't recycle child if we didn't find — it was recycled in recursion
            }
            return null
        }

        private fun findEditableNear(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
            if (node.isEditable) return node
            // Check children
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                if (child.isEditable) return child
                val found = findEditableNear(child)
                if (found != null) return found
            }
            // Check parent
            return node.parent?.let { if (it.isEditable) it else null }
        }

        private fun findScrollable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
            if (node.isScrollable) return node
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                val found = findScrollable(child)
                if (found != null) return found
            }
            return null
        }

        private fun collectClickable(node: AccessibilityNodeInfo, list: MutableList<String>) {
            val text = node.text?.toString() ?: node.contentDescription?.toString() ?: ""
            if (node.isClickable && text.isNotEmpty()) {
                val bounds = Rect()
                node.getBoundsInScreen(bounds)
                list.add("$text (${bounds.left},${bounds.top})")
            }
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                collectClickable(child, list)
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "AccessibilityService connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                lastPackageName = event.packageName?.toString()
                Log.d(TAG, "Window changed: ${event.packageName}")
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                // Update cached content periodically
                val root = rootInActiveWindow
                if (root != null) {
                    val sb = StringBuilder()
                    traverseNode(root, sb, 0)
                    lastWindowContent = sb.toString().take(3000)
                    root.recycle()
                }
            }
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "AccessibilityService interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        Log.d(TAG, "AccessibilityService destroyed")
    }
}
