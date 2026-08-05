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
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
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

    private val client = OkHttpClient()
    private val JSON = "application/json; charset=utf-8".toMediaType()

    // Verlauf für Kontext (einfaches Gedächtnis innerhalb der Session)
    private val conversationHistory = mutableListOf<Pair<String, String>>() // (role, text)

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

        // Empfängt Befehle, die der Hintergrunddienst gehört hat und nicht selbst
        // ausführen konnte (z.B. normale Fragen an Gemini)
        registerReceiver(
            object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    val command = intent?.getStringExtra("command") ?: return
                    appendChat("Du (Hintergrund)", command)
                    sendToGemini(command)
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
                        sendToGemini(spokenText)
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
            if (apiKeyInput.text.isBlank()) {
                Toast.makeText(this, "Bitte zuerst deinen Gemini API-Key eingeben", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            startListening()
        }
    }

    private fun startListening() {
        val intent = android.content.Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.GERMAN)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Sprich jetzt mit Jarvis...")
        }
        speechRecognizer.startListening(intent)
    }

    private fun sendToGemini(userText: String) {
        val apiKey = apiKeyInput.text.toString().trim()
        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=$apiKey"

        conversationHistory.add("user" to userText)

        val contentsArray = JSONArray()
        for ((role, text) in conversationHistory) {
            val partObj = JSONObject().put("text", text)
            val parts = JSONArray().put(partObj)
            val entry = JSONObject()
                .put("role", role)
                .put("parts", parts)
            contentsArray.put(entry)
        }

        val systemInstruction = JSONObject()
            .put("parts", JSONArray().put(
                JSONObject().put("text",
                    "Du bist Jarvis, ein hilfsbereiter, knapper persönlicher KI-Assistent. " +
                    "Antworte auf Deutsch, klar und direkt, wie ein kompetenter Mitarbeiter.")
            ))

        val body = JSONObject()
            .put("contents", contentsArray)
            .put("system_instruction", systemInstruction)
            .toString()

        val request = Request.Builder()
            .url(url)
            .post(body.toRequestBody(JSON))
            .build()

        runOnUiThread { statusText.text = "Jarvis denkt nach..." }

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    statusText.text = "Verbindungsfehler"
                    appendChat("Jarvis", "Ich konnte keine Verbindung herstellen: ${e.message}")
                }
            }

            override fun onResponse(call: Call, response: okhttp3.Response) {
                val bodyStr = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    runOnUiThread {
                        statusText.text = "Fehler von der Gemini-API"
                        appendChat("Jarvis", "Fehler: $bodyStr")
                    }
                    return
                }
                try {
                    val json = JSONObject(bodyStr)
                    val reply = json.getJSONArray("candidates")
                        .getJSONObject(0)
                        .getJSONObject("content")
                        .getJSONArray("parts")
                        .getJSONObject(0)
                        .getString("text")

                    conversationHistory.add("model" to reply)

                    runOnUiThread {
                        statusText.text = "Bereit"
                        appendChat("Jarvis", reply)
                        speak(reply)
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        statusText.text = "Antwort konnte nicht gelesen werden"
                        appendChat("Jarvis", "Fehler beim Verarbeiten der Antwort.")
                    }
                }
            }
        })
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
