package com.jarvis.ai

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.app.NotificationCompat
import java.util.Locale

/**
 * Läuft als Vordergrunddienst (Pflicht ab Android 8+, damit das Mikrofon
 * im Hintergrund benutzt werden darf) und lauscht wiederholt auf das
 * Weckwort "Jarvis". Alles danach wird als Befehl behandelt.
 */
class JarvisListenerService : Service() {

    private var speechRecognizer: SpeechRecognizer? = null
    private var isRunning = false

    override fun onCreate() {
        super.onCreate()
        startForeground(1, buildNotification("Jarvis hört zu..."))
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        isRunning = true
        listenLoop()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    private fun listenLoop() {
        if (!isRunning) return
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.GERMAN)
        }
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull()?.lowercase() ?: ""
                if (text.contains("jarvis")) {
                    val command = text.substringAfter("jarvis").trim()
                    if (command.isNotBlank()) handleWakeCommand(command)
                }
                listenLoop()
            }
            override fun onError(error: Int) { listenLoop() }
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        speechRecognizer?.startListening(intent)
    }

    private fun handleWakeCommand(command: String) {
        if (CommandHandler.tryOpenApp(this, command)) return
        if (CommandHandler.tryMakeCall(this, command)) return
        // Kein Geräte-Befehl erkannt -> an MainActivity weiterreichen für Gemini
        val broadcast = Intent("com.jarvis.ai.VOICE_COMMAND")
        broadcast.putExtra("command", command)
        sendBroadcast(broadcast)
    }

    private fun buildNotification(text: String): Notification {
        val channelId = "jarvis_channel"
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            channelId, "Jarvis Hintergrunddienst", NotificationManager.IMPORTANCE_LOW
        )
        manager.createNotificationChannel(channel)
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Jarvis AI")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        isRunning = false
        speechRecognizer?.destroy()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
