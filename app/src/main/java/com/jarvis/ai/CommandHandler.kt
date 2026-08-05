package com.jarvis.ai

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri

/**
 * Erkennt einfache Gerätebefehle in gesprochenem Text.
 * Gibt true zurück, wenn ein Befehl erkannt und ausgeführt wurde,
 * sonst false (dann soll der Text an Gemini weitergeleitet werden).
 */
object CommandHandler {

    fun tryOpenApp(context: Context, text: String): Boolean {
        val lower = text.lowercase().trim()
        val triggers = listOf("öffne ", "starte ", "open ")
        val appNamePart = triggers.firstOrNull { lower.startsWith(it) }
            ?.let { lower.removePrefix(it).trim() } ?: return false

        if (appNamePart.isBlank()) return false

        val pm = context.packageManager
        val apps: List<ApplicationInfo> = try {
            pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))
        } catch (e: Exception) {
            pm.getInstalledApplications(0)
        }

        for (app in apps) {
            val label = pm.getApplicationLabel(app).toString().lowercase()
            if (label.contains(appNamePart) || appNamePart.contains(label)) {
                val launchIntent = pm.getLaunchIntentForPackage(app.packageName) ?: continue
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                return true
            }
        }
        return false
    }

    fun tryMakeCall(context: Context, text: String): Boolean {
        val lower = text.lowercase().trim()
        val callTriggers = listOf("ruf ", "rufe ", "anrufen", "call ")
        val looksLikeCall = callTriggers.any { lower.contains(it) }
        if (!looksLikeCall) return false

        // Aktuell werden nur Zahlen direkt unterstützt.
        // Kontaktnamen würden zusätzlich READ_CONTACTS + Auflösung benötigen.
        val digits = text.filter { it.isDigit() }
        if (digits.length < 3) return false

        val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$digits"))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return true
    }
}
