package com.jarvis.ai

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract

object CommandHandler {

    fun handleCustomCommands(context: Context, command: String, onResult: (String?) -> Unit): Boolean {
        val lower = command.lowercase()

        // TikTok Follower auslesen
        if (lower.contains("tiktok") && (lower.contains("follower") || lower.contains("wie viele"))) {
            val pm = context.packageManager
            val launchIntent = pm.getLaunchIntentForPackage("com.zhiliaoapp.musically") 
                ?: pm.getLaunchIntentForPackage("com.ss.android.ugc.trill")

            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)

                // 3 Sekunden warten, bis TikTok geladen ist, dann Bildschirm auslesen
                Handler(Looper.getMainLooper()).postDelayed({
                    val screenText = JarvisAccessibilityService.instance?.readScreenText()
                    if (!screenText.isNullOrEmpty()) {
                        onResult("Ich habe TikTok geöffnet. Hier sind die Informationen auf dem Bildschirm: $screenText")
                    } else {
                        onResult("TikTok wurde geöffnet, aber bitte aktiviere den Barrierefreiheitsdienst für Jarvis in den Android-Einstellungen.")
                    }
                }, 3000)
                return true
            }
        }
        return false
    }

    fun tryMakeCall(context: Context, command: String): Boolean {
        val lower = command.lowercase()
        if (lower.contains("ruf") || lower.contains("anrufen") || lower.contains("wähle")) {
            val contactName = lower
                .replace("rufe", "").replace("ruf", "").replace("anrufen", "")
                .replace("wähle", "").replace("an", "").replace("bitte", "").trim()

            if (contactName.isNotEmpty()) {
                val number = getPhoneNumberByName(context, contactName)
                if (number != null) {
                    val intent = Intent(Intent.ACTION_CALL).apply {
                        data = Uri.parse("tel:$number")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                    return true
                }
            }
        }
        return false
    }

    private fun getPhoneNumberByName(context: Context, name: String): String? {
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER)
        val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("%$name%")

        try {
            context.contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    if (index != -1) return cursor.getString(index)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }
}
