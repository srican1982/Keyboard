package com.personal.sinhalakeyboard

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText

class SettingsActivity : AppCompatActivity() {

    private val micPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        Toast.makeText(
            this,
            if (granted) "Microphone enabled for voice typing" else "Microphone permission denied",
            Toast.LENGTH_SHORT,
        ).show()
    }

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

        findViewById<SwitchMaterial>(R.id.switchAutoCorrect).isChecked =
            Prefs.isAutoCorrectOnSpace(this)

        findViewById<Button>(R.id.btnMicPermission).setOnClickListener {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }

        findViewById<Button>(R.id.btnSave).setOnClickListener {
            Prefs.setApiKey(this, input.text?.toString().orEmpty())

            val theme = when (findViewById<RadioGroup>(R.id.themeGroup).checkedRadioButtonId) {
                R.id.themeBlack -> KeyboardTheme.BLACK
                else -> KeyboardTheme.WHITE
            }
            Prefs.setTheme(this, theme)
            Prefs.setAutoCorrectOnSpace(
                this,
                findViewById<SwitchMaterial>(R.id.switchAutoCorrect).isChecked,
            )

            Toast.makeText(this, R.string.api_key_saved, Toast.LENGTH_SHORT).show()
        }
    }
}
