package com.personal.sinhalakeyboard

import android.inputmethodservice.InputMethodService
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedTextRequest
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class KeyboardService : InputMethodService() {

    enum class Language { SINHALA, ENGLISH }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val grammarFixer = GrammarFixer()

    private var keyboardView: View? = null
    private var previewBar: TextView? = null
    private var btnLang: Button? = null
    private var btnFix: Button? = null
    private var progress: ProgressBar? = null

    private var language = Language.SINHALA
    private var shiftOn = false
    private var sinhalaBuffer = StringBuilder()
    private var fixJob: Job? = null

    override fun onCreateInputView(): View {
        val view = LayoutInflater.from(this).inflate(R.layout.keyboard_view, null)
        keyboardView = view
        previewBar = view.findViewById(R.id.previewBar)
        btnLang = view.findViewById(R.id.btnLang)
        btnFix = view.findViewById(R.id.btnFix)
        progress = view.findViewById(R.id.progress)

        setupKey(view, R.id.keyQ, "q")
        setupKey(view, R.id.keyW, "w")
        setupKey(view, R.id.keyE, "e")
        setupKey(view, R.id.keyR, "r")
        setupKey(view, R.id.keyT, "t")
        setupKey(view, R.id.keyY, "y")
        setupKey(view, R.id.keyU, "u")
        setupKey(view, R.id.keyI, "i")
        setupKey(view, R.id.keyO, "o")
        setupKey(view, R.id.keyP, "p")
        setupKey(view, R.id.keyA, "a")
        setupKey(view, R.id.keyS, "s")
        setupKey(view, R.id.keyD, "d")
        setupKey(view, R.id.keyF, "f")
        setupKey(view, R.id.keyG, "g")
        setupKey(view, R.id.keyH, "h")
        setupKey(view, R.id.keyJ, "j")
        setupKey(view, R.id.keyK, "k")
        setupKey(view, R.id.keyL, "l")
        setupKey(view, R.id.keyZ, "z")
        setupKey(view, R.id.keyX, "x")
        setupKey(view, R.id.keyC, "c")
        setupKey(view, R.id.keyV, "v")
        setupKey(view, R.id.keyB, "b")
        setupKey(view, R.id.keyN, "n")
        setupKey(view, R.id.keyM, "m")

        view.findViewById<Button>(R.id.keyShift).setOnClickListener { toggleShift() }
        view.findViewById<Button>(R.id.keyBackspace).setOnClickListener { onBackspace() }
        view.findViewById<Button>(R.id.keySpace).setOnClickListener { onSpace() }
        view.findViewById<Button>(R.id.keyEnter).setOnClickListener { onEnter() }
        view.findViewById<Button>(R.id.keyComma).setOnClickListener { commitDirect(",") }
        view.findViewById<Button>(R.id.keyPeriod).setOnClickListener { commitDirect(".") }
        view.findViewById<Button>(R.id.keyNumbers).setOnClickListener {
            Toast.makeText(this, "Numbers row coming soon", Toast.LENGTH_SHORT).show()
        }

        btnLang?.setOnClickListener { toggleLanguage() }
        btnFix?.setOnClickListener { fixGrammar() }

        updateLanguageUi()
        return view
    }

    private fun setupKey(view: View, id: Int, letter: String) {
        view.findViewById<Button>(id).setOnClickListener { onLetter(letter) }
    }

    private fun onLetter(letter: String) {
        val ch = if (shiftOn) letter.uppercase() else letter
        if (language == Language.ENGLISH) {
            currentInputConnection?.commitText(ch, 1)
            if (shiftOn) toggleShift()
            return
        }

        sinhalaBuffer.append(ch.lowercase())
        updatePreview()
    }

    private fun onSpace() {
        if (language == Language.SINHALA && sinhalaBuffer.isNotEmpty()) {
            commitSinhalaWord()
        }
        currentInputConnection?.commitText(" ", 1)
    }

    private fun onEnter() {
        if (language == Language.SINHALA && sinhalaBuffer.isNotEmpty()) {
            commitSinhalaWord()
        }
        currentInputConnection?.commitText("\n", 1)
    }

    private fun onBackspace() {
        if (language == Language.SINHALA && sinhalaBuffer.isNotEmpty()) {
            sinhalaBuffer.deleteCharAt(sinhalaBuffer.length - 1)
            updatePreview()
            return
        }
        currentInputConnection?.deleteSurroundingText(1, 0)
    }

    private fun commitSinhalaWord() {
        val word = sinhalaBuffer.toString()
        val sinhala = SinglishEngine.transliterate(word)
        currentInputConnection?.commitText(sinhala, 1)
        sinhalaBuffer.clear()
        updatePreview()
    }

    private fun commitDirect(text: String) {
        if (language == Language.SINHALA && sinhalaBuffer.isNotEmpty()) {
            commitSinhalaWord()
        }
        currentInputConnection?.commitText(text, 1)
    }

    private fun updatePreview() {
        previewBar?.text = if (language == Language.SINHALA && sinhalaBuffer.isNotEmpty()) {
            SinglishEngine.transliterate(sinhalaBuffer.toString())
        } else {
            ""
        }
    }

    private fun toggleShift() {
        shiftOn = !shiftOn
        refreshKeyLabels()
    }

    private fun refreshKeyLabels() {
        val view = keyboardView ?: return
        val letters = listOf(
            R.id.keyQ to "q", R.id.keyW to "w", R.id.keyE to "e", R.id.keyR to "r",
            R.id.keyT to "t", R.id.keyY to "y", R.id.keyU to "u", R.id.keyI to "i",
            R.id.keyO to "o", R.id.keyP to "p", R.id.keyA to "a", R.id.keyS to "s",
            R.id.keyD to "d", R.id.keyF to "f", R.id.keyG to "g", R.id.keyH to "h",
            R.id.keyJ to "j", R.id.keyK to "k", R.id.keyL to "l", R.id.keyZ to "z",
            R.id.keyX to "x", R.id.keyC to "c", R.id.keyV to "v", R.id.keyB to "b",
            R.id.keyN to "n", R.id.keyM to "m",
        )
        for ((id, letter) in letters) {
            view.findViewById<Button>(id).text = if (shiftOn) letter.uppercase() else letter
        }
    }

    private fun toggleLanguage() {
        if (sinhalaBuffer.isNotEmpty()) commitSinhalaWord()
        language = if (language == Language.SINHALA) Language.ENGLISH else Language.SINHALA
        updateLanguageUi()
    }

    private fun updateLanguageUi() {
        when (language) {
            Language.SINHALA -> {
                btnLang?.text = getString(R.string.mode_sinhala)
                btnFix?.visibility = View.GONE
                previewBar?.visibility = View.VISIBLE
            }
            Language.ENGLISH -> {
                btnLang?.text = getString(R.string.mode_english)
                btnFix?.visibility = View.VISIBLE
                previewBar?.visibility = View.GONE
                previewBar?.text = ""
            }
        }
    }

    private fun fixGrammar() {
        val ic = currentInputConnection ?: return
        val apiKey = Prefs.getApiKey(this)
        if (apiKey.isBlank()) {
            Toast.makeText(this, R.string.api_key_missing, Toast.LENGTH_LONG).show()
            return
        }

        fixJob?.cancel()
        fixJob = scope.launch {
            progress?.visibility = View.VISIBLE
            btnFix?.isEnabled = false

            val text = withContext(Dispatchers.Main) { getCurrentSentence(ic) }
            if (text.isBlank()) {
                progress?.visibility = View.GONE
                btnFix?.isEnabled = true
                return@launch
            }

            val result = grammarFixer.fixGrammar(text, apiKey)
            progress?.visibility = View.GONE
            btnFix?.isEnabled = true

            result.onSuccess { corrected ->
                replaceCurrentSentence(ic, text, corrected)
            }.onFailure {
                Toast.makeText(this@KeyboardService, R.string.grammar_error, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun getCurrentSentence(ic: android.view.inputmethod.InputConnection): String {
        val extracted = ic.getExtractedText(ExtractedTextRequest(), 0) ?: return ""
        val full = extracted.text?.toString().orEmpty()
        if (full.isEmpty()) return ""

        val cursor = extracted.selectionStart.coerceIn(0, full.length)
        var start = cursor
        while (start > 0 && !full[start - 1].isSentenceBreak()) start--
        var end = cursor
        while (end < full.length && !full[end].isSentenceBreak()) end++
        return full.substring(start, end).trim()
    }

    private fun replaceCurrentSentence(
        ic: android.view.inputmethod.InputConnection,
        original: String,
        corrected: String,
    ) {
        if (original.isEmpty()) {
            ic.commitText(corrected, 1)
            return
        }
        val before = original.length
        ic.deleteSurroundingText(before, 0)
        ic.commitText(corrected, 1)
    }

    private fun Char.isSentenceBreak(): Boolean = this == '.' || this == '!' || this == '?' || this == '\n'

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        sinhalaBuffer.clear()
        updatePreview()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
