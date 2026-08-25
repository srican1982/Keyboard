package com.personal.sinhalakeyboard

import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val input = findViewById<TextInputEditText>(R.id.apiKeyInput)
        input.setText(Prefs.getApiKey(this))

        findViewById<Button>(R.id.btnSave).setOnClickListener {
            Prefs.setApiKey(this, input.text?.toString().orEmpty())
            Toast.makeText(this, R.string.api_key_saved, Toast.LENGTH_SHORT).show()
        }
    }
}
