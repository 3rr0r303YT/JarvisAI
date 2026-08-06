package com.jarvis.ai

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Benutzeroberfläche direkt im Code bauen (keine XML nötig!)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 50, 50, 50)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val title = TextView(this).apply {
            text = "Jarvis AI Einstellungen"
            textSize = 22f
            setTextColor(Color.BLACK)
            setPadding(0, 0, 0, 30)
        }
        layout.addView(title)

        val providerLabel = TextView(this).apply {
            text = "Wähle deinen KI-Anbieter:"
            textSize = 16f
            setPadding(0, 10, 0, 10)
        }
        layout.addView(providerLabel)

        val radioGroup = RadioGroup(this)
        val rbGemini = RadioButton(this).apply { text = "Google Gemini"; id = 101 }
        val rbOpenAi = RadioButton(this).apply { text = "OpenAI (ChatGPT)"; id = 102 }
        val rbClaude = RadioButton(this).apply { text = "Anthropic Claude"; id = 103 }

        radioGroup.addView(rbGemini)
        radioGroup.addView(rbOpenAi)
        radioGroup.addView(rbClaude)
        layout.addView(radioGroup)

        val keyLabel = TextView(this).apply {
            text = "API Key eintragen:"
            textSize = 16f
            setPadding(0, 30, 0, 10)
        }
        layout.addView(keyLabel)

        val etApiKey = EditText(this).apply {
            hint = "API Key hier einfügen..."
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        layout.addView(etApiKey)

        val btnSave = Button(this).apply {
            text = "Speichern"
        }
        layout.addView(btnSave)

        setContentView(layout)

        // Gespeicherte Daten auslesen
        val prefs = getSharedPreferences("JarvisSettings", Context.MODE_PRIVATE)
        etApiKey.setText(prefs.getString("API_KEY", ""))

        val savedProvider = prefs.getString("SELECTED_PROVIDER", "GEMINI")
        when (savedProvider) {
            "OPENAI" -> rbOpenAi.isChecked = true
            "CLAUDE" -> rbClaude.isChecked = true
            else -> rbGemini.isChecked = true
        }

        // Speichern-Button Logik
        btnSave.setOnClickListener {
            val key = etApiKey.text.toString().trim()
            val selectedProvider = when (radioGroup.checkedRadioButtonId) {
                102 -> "OPENAI"
                103 -> "CLAUDE"
                else -> "GEMINI"
            }

            prefs.edit().apply {
                putString("API_KEY", key)
                putString("SELECTED_PROVIDER", selectedProvider)
                apply()
            }

            Toast.makeText(this, "Einstellungen gespeichert!", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
