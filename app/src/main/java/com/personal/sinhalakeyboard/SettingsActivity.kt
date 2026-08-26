package com.personal.sinhalakeyboard

import android.os.Bundle
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val input = findViewById<TextInputEditText>(R.id.apiKeyInput)
        input.setText(Prefs.getApiKey(this))

        val themeWhite = findViewById<RadioButton>(R.id.themeWhite)
        val themeBlack = findViewById<RadioButton>(R.id.themeBlack)
        when (Prefs.getTheme(this)) {
            KeyboardTheme.WHITE -> themeWhite.isChecked = true
            KeyboardTheme.BLACK -> themeBlack.isChecked = true
        }

        findViewById<Button>(R.id.btnSave).setOnClickListener {
            Prefs.setApiKey(this, input.text?.toString().orEmpty())

            val theme = when (findViewById<RadioGroup>(R.id.themeGroup).checkedRadioButtonId) {
                R.id.themeBlack -> KeyboardTheme.BLACK
                else -> KeyboardTheme.WHITE
            }
            Prefs.setTheme(this, theme)

            Toast.makeText(this, R.string.api_key_saved, Toast.LENGTH_SHORT).show()
        }
    }
}
