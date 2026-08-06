package com.jarvis.ai

import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val etApiKey = findViewById<EditText>(R.id.etApiKey)
        val rgProvider = findViewById<RadioGroup>(R.id.rgProvider)
        val rbGemini = findViewById<RadioButton>(R.id.rbGemini)
        val rbOpenAi = findViewById<RadioButton>(R.id.rbOpenAi)
        val rbClaude = findViewById<RadioButton>(R.id.rbClaude)
        val btnSave = findViewById<Button>(R.id.btnSave)

        val prefs = getSharedPreferences("JarvisSettings", Context.MODE_PRIVATE)

        // Gespeicherte Daten beim Öffnen anzeigen
        etApiKey.setText(prefs.getString("API_KEY", ""))
        val savedProvider = prefs.getString("SELECTED_PROVIDER", "GEMINI")

        when (savedProvider) {
            "OPENAI" -> rbOpenAi.isChecked = true
            "CLAUDE" -> rbClaude.isChecked = true
            else -> rbGemini.isChecked = true
        }

        // Bei Klick auf "Speichern" Daten sichern
        btnSave.setOnClickListener {
            val key = etApiKey.text.toString().trim()
            val selectedProvider = when (rgProvider.checkedRadioButtonId) {
                R.id.rbOpenAi -> "OPENAI"
                R.id.rbClaude -> "CLAUDE"
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
