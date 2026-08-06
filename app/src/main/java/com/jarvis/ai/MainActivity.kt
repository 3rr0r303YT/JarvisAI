package com.jarvis.ai

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.widget.Button
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.Locale

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var tts: TextToSpeech
    private lateinit var chatText: TextView
    private lateinit var statusText: TextView
    private lateinit var micButton: Button
    private lateinit var apiKeyInput: EditText
    private lateinit var chatScroll: ScrollView
    private lateinit var listenToggleButton: Button
    private var isBackgroundListening = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        chatText = findViewById(R.id.chatText)
        statusText = findViewById(R.id.statusText)
        micButton = findViewById(R.id.micButton)
        apiKeyInput = findViewById(R.id.apiKeyInput)
        chatScroll = findViewById(R.id.chatScroll)
        listenToggleButton = findViewById(R.id.listenToggleButton)

        listenToggleButton.setOnClickListener {
            isBackgroundListening = !isBackgroundListening
            if (isBackgroundListening) {
                val serviceIntent = Intent(this, JarvisListenerService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent)
                } else {
                    startService(serviceIntent)
                }
                listenToggleButton.text = "👂 Dauerzuhören stoppen"
                statusText.text = "Sag \"Jarvis\" gefolgt von deinem Befehl"
            } else {
                stopService(Intent(this, JarvisListenerService::class.java))
                listenToggleButton.text = "👂 Dauerzuhören starten"
                statusText.text = "Bereit"
            }
        }

        tts = TextToSpeech(this, this)

        val neededPermissions = mutableListOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CALL_PHONE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            neededPermissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val missing = neededPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), 1)
        }

        registerReceiver(
            object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    val command = intent?.getStringExtra("command") ?: return
                    appendChat("Du (Hintergrund)", command)
                    sendToAI(command)
                }
            },
            IntentFilter("com.jarvis.ai.VOICE_COMMAND"),
            Context.RECEIVER_NOT_EXPORTED.takeIf { Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU } ?: 0
        )

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                statusText.text = "Ich höre zu..."
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val spokenText = matches?.firstOrNull()
                if (!spokenText.isNullOrBlank()) {
                    appendChat("Du", spokenText)
                    if (CommandHandler.tryOpenApp(this@MainActivity, spokenText)) {
                        appendChat("Jarvis", "App wird geöffnet.")
                    } else if (CommandHandler.tryMakeCall(this@MainActivity, spokenText)) {
                        appendChat("Jarvis", "Rufe an...")
                    } else {
                        sendToAI(spokenText)
                    }
                } else {
                    statusText.text = "Nichts verstanden. Nochmal."
                }
            }

            override fun onError(error: Int) {
                statusText.text = "Fehler bei der Spracherkennung ($error)"
            }

            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { statusText.text = "Verarbeite..." }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        micButton.setOnClickListener {
            startListening()
        }
    }

    private fun startListening() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.GERMAN)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Sprich jetzt mit Jarvis...")
        }
        speechRecognizer.startListening(intent)
    }

    private fun sendToAI(userText: String) {
        val prefs = getSharedPreferences("JarvisSettings", Context.MODE_PRIVATE)
        var apiKey = prefs.getString("API_KEY", "")?.trim() ?: ""

        if (apiKey.isBlank()) {
            apiKey = apiKeyInput.text.toString().trim()
        }

        if (apiKey.isBlank()) {
            Toast.makeText(this, "Bitte gib einen API-Key ein!", Toast.LENGTH_LONG).show()
            statusText.text = "Kein API Key hinterlegt"
            return
        }

        val savedProviderStr = prefs.getString("SELECTED_PROVIDER", "") ?: ""

        // 🔥 AUTO-DETECTION DER KI ANHAND DES API-KEYS 🔥
        val provider = when {
            apiKey.startsWith("sk-ant-") -> AIProvider.CLAUDE
            apiKey.startsWith("sk-") -> AIProvider.OPENAI
            savedProviderStr == "OPENAI" -> AIProvider.OPENAI
            savedProviderStr == "CLAUDE" -> AIProvider.CLAUDE
            else -> AIProvider.GEMINI
        }

        statusText.text = "Jarvis denkt nach ($provider)..."

        val aiService = AIService()
        aiService.sendMessage(provider, apiKey, userText) { result ->
            runOnUiThread {
                result.onSuccess { reply ->
                    statusText.text = "Bereit"
                    appendChat("Jarvis", reply)
                    speak(reply)
                }.onFailure { error ->
                    statusText.text = "Fehler"
                    appendChat("Jarvis", "Fehler: ${error.message}")
                }
            }
        }
    }

    private fun speak(text: String) {
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    private fun appendChat(sender: String, text: String) {
        chatText.append("\n\n$sender: $text")
        chatScroll.post { chatScroll.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale.GERMAN
        }
    }

    override fun onDestroy() {
        speechRecognizer.destroy()
        tts.stop()
        tts.shutdown()
        super.onDestroy()
    }
}
