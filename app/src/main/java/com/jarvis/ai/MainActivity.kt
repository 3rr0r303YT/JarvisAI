package com.jarvis.ai

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.Locale

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var tvStatus: TextView
    private lateinit var tvLog: TextView
    private lateinit var etApiKey: EditText
    private lateinit var btnSpeak: Button

    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private val aiService = AIService()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvStatus)
        tvLog = findViewById(R.id.tvLog)
        etApiKey = findViewById(R.id.etApiKey)
        btnSpeak = findViewById(R.id.btnSpeak)

        tts = TextToSpeech(this, this)

        checkAndRequestPermissions()

        btnSpeak.setOnClickListener {
            startListening()
        }
    }

    private fun checkAndRequestPermissions() {
        val neededPermissions = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_CONTACTS
        )

        val missing = neededPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), 101)
        }
    }

    private fun startListening() {
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.GERMAN.toString())
            }

            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    tvStatus.text = "Zuhören..."
                }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    tvStatus.text = "Verarbeite..."
                }
                override fun onError(error: Int) {
                    tvStatus.text = "Fehler bei der Spracheingabe"
                }
                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        val userText = matches[0]
                        tvLog.append("\nDu: $userText")
                        processUserPrompt(userText)
                    }
                }
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            speechRecognizer?.startListening(intent)
        } else {
            Toast.makeText(this, "Spracherkennung nicht verfügbar", Toast.LENGTH_SHORT).show()
        }
    }

    private fun processUserPrompt(prompt: String) {
        // 1. TikTok / Bildschirm-Aktionen
        val handledCustom = CommandHandler.handleCustomCommands(this, prompt) { aiContextPrompt ->
            if (aiContextPrompt != null) {
                sendToAI(aiContextPrompt)
            }
        }
        if (handledCustom) return

        // 2. Anrufen
        if (CommandHandler.tryMakeCall(this, prompt)) {
            speak("Wähle Nummer...")
            return
        }

        // 3. App öffnen
        if (CommandHandler.tryOpenApp(this, prompt)) {
            speak("Öffne App...")
            return
        }

        // 4. KI befragen
        sendToAI(prompt)
    }

    private fun sendToAI(prompt: String) {
        val apiKey = etApiKey.text.toString().trim()
        if (apiKey.isEmpty()) {
            Toast.makeText(this, "Bitte API-Key eingeben", Toast.LENGTH_SHORT).show()
            return
        }

        val provider = when {
            apiKey.startsWith("gsk_") -> AIProvider.GROQ
            apiKey.startsWith("sk-ant-") -> AIProvider.CLAUDE
            apiKey.startsWith("sk-") -> AIProvider.OPENAI
            else -> AIProvider.GEMINI
        }

        aiService.sendMessage(provider, apiKey, prompt) { result ->
            runOnUiThread {
                result.onSuccess { response ->
                    tvLog.append("\nJarvis: $response")
                    speak(response)
                }.onFailure { error ->
                    tvLog.append("\nJarvis Fehler: ${error.message}")
                }
            }
        }
    }

    private fun speak(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.GERMAN
        }
    }

    override fun onDestroy() {
        speechRecognizer?.destroy()
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}
