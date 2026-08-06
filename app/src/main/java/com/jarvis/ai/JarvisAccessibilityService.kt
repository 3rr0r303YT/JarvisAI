package com.jarvis.ai

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class JarvisAccessibilityService : AccessibilityService() {

    companion object {
        var instance: JarvisAccessibilityService? = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    // Liest den gesamten Text aus, der aktuell auf dem Bildschirm zu sehen ist
    fun readScreenText(): String {
        val rootNode = rootInActiveWindow ?: return ""
        val textBuilder = StringBuilder()
        collectText(rootNode, textBuilder)
        return textBuilder.toString()
    }

    private fun collectText(node: AccessibilityNodeInfo?, sb: StringBuilder) {
        if (node == null) return
        if (!node.text.isNullOrEmpty()) {
            sb.append(node.text).append(" ")
        }
        for (i in 0 until node.childCount) {
            collectText(node.getChild(i), sb)
        }
    }
}
