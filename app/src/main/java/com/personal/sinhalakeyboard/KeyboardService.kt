package com.personal.sinhalakeyboard

import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.LinearLayout
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
    private val repeatHandler = Handler(Looper.getMainLooper())
    private var repeatRunnable: Runnable? = null

    private lateinit var singlishEngine: SinglishEngine

    private var keyboardView: View? = null
    private var suggestionRow: LinearLayout? = null
    private var btnLang: TextView? = null
    private var btnFix: TextView? = null
    private var progress: ProgressBar? = null

    private var language = Language.SINHALA
    private var shiftOn = false
    private var sinhalaBuffer = StringBuilder()
    private var fixJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        singlishEngine = SinglishEngine(this)
    }

    override fun onCreateInputView(): View {
        val view = LayoutInflater.from(this).inflate(R.layout.keyboard_view, null)
        keyboardView = view
        suggestionRow = view.findViewById(R.id.suggestionRow)
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

        view.findViewById<TextView>(R.id.keyShift).setOnClickListener { toggleShift() }
        setupRepeatKey(view.findViewById(R.id.keyBackspace)) { onBackspace() }
        view.findViewById<TextView>(R.id.keySpace).setOnClickListener { onSpace() }
        view.findViewById<TextView>(R.id.keyEnter).setOnClickListener { onEnter() }
        view.findViewById<TextView>(R.id.keyComma).setOnClickListener { commitDirect(",") }
        view.findViewById<TextView>(R.id.keyPeriod).setOnClickListener { commitDirect(".") }
        view.findViewById<TextView>(R.id.keyNumbers).setOnClickListener {
            Toast.makeText(this, "Numbers row coming soon", Toast.LENGTH_SHORT).show()
        }

        btnLang?.setOnClickListener { toggleLanguage() }
        btnFix?.setOnClickListener { fixGrammar() }

        updateLanguageUi()
        return view
    }

    private fun setupKey(view: View, id: Int, letter: String) {
        view.findViewById<TextView>(id).setOnClickListener { onLetter(letter) }
    }

    private fun setupRepeatKey(view: View, action: () -> Unit) {
        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    action()
                    startRepeat(action)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    stopRepeat()
                    true
                }
                else -> false
            }
        }
    }

    private fun startRepeat(action: () -> Unit) {
        stopRepeat()
        var delay = 400L
        repeatRunnable = object : Runnable {
            override fun run() {
                action()
                delay = (delay * 0.85).toLong().coerceAtLeast(40L)
                repeatHandler.postDelayed(this, delay)
            }
        }
        repeatHandler.postDelayed(repeatRunnable!!, 400L)
    }

    private fun stopRepeat() {
        repeatRunnable?.let { repeatHandler.removeCallbacks(it) }
        repeatRunnable = null
    }

    private fun onLetter(letter: String) {
        val ch = if (shiftOn) letter.uppercase() else letter
        if (language == Language.ENGLISH) {
            currentInputConnection?.commitText(ch, 1)
            if (shiftOn) toggleShift()
            updateEnglishSuggestions()
            return
        }

        sinhalaBuffer.append(ch.lowercase())
        updateComposingText()
        updateSinhalaSuggestions()
    }

    private fun onSpace() {
        if (language == Language.SINHALA && sinhalaBuffer.isNotEmpty()) {
            commitSinhalaWord()
        }
        currentInputConnection?.commitText(" ", 1)
        clearSuggestions()
    }

    private fun onEnter() {
        if (language == Language.SINHALA && sinhalaBuffer.isNotEmpty()) {
            commitSinhalaWord()
        }
        currentInputConnection?.commitText("\n", 1)
        clearSuggestions()
    }

    private fun onBackspace() {
        if (language == Language.SINHALA && sinhalaBuffer.isNotEmpty()) {
            sinhalaBuffer.deleteCharAt(sinhalaBuffer.length - 1)
            updateComposingText()
            updateSinhalaSuggestions()
            return
        }
        currentInputConnection?.deleteSurroundingText(1, 0)
        if (language == Language.ENGLISH) updateEnglishSuggestions()
    }

    private fun commitSinhalaWord(sinhala: String? = null) {
        val word = sinhala ?: singlishEngine.transliterate(sinhalaBuffer.toString())
        if (word.isEmpty()) return
        currentInputConnection?.commitText(word, 1)
        sinhalaBuffer.clear()
        clearComposingText()
        clearSuggestions()
    }

    private fun commitRomanWord(roman: String) {
        currentInputConnection?.commitText(roman, 1)
        sinhalaBuffer.clear()
        clearComposingText()
        clearSuggestions()
    }

    private fun commitDirect(text: String) {
        if (language == Language.SINHALA && sinhalaBuffer.isNotEmpty()) {
            commitSinhalaWord()
        }
        currentInputConnection?.commitText(text, 1)
    }

    private fun updateComposingText() {
        val ic = currentInputConnection ?: return
        if (sinhalaBuffer.isEmpty()) {
            ic.setComposingText("", 0)
            return
        }
        val sinhala = singlishEngine.transliterateLive(sinhalaBuffer.toString())
        ic.setComposingText(sinhala, 1)
    }

    private fun clearComposingText() {
        currentInputConnection?.finishComposingText()
    }

    private fun updateSinhalaSuggestions() {
        if (sinhalaBuffer.isEmpty()) {
            clearSuggestions()
            return
        }
        val items = singlishEngine.suggestions(sinhalaBuffer.toString())
        renderSuggestions(items) { candidate ->
            if (candidate.isRoman) {
                commitRomanWord(candidate.commitText)
            } else {
                commitSinhalaWord(candidate.commitText)
            }
        }
    }

    private fun updateEnglishSuggestions() {
        val ic = currentInputConnection ?: return
        val word = getCurrentWord(ic)
        if (word.isEmpty()) {
            clearSuggestions()
            return
        }
        val items = EnglishSuggestions.suggest(word)
        renderSuggestions(items) { candidate ->
            replaceCurrentWord(ic, word, candidate.commitText)
        }
    }

    private fun renderSuggestions(
        items: List<SuggestionCandidate>,
        onPick: (SuggestionCandidate) -> Unit,
    ) {
        val row = suggestionRow ?: return
        row.removeAllViews()
        for (candidate in items) {
            val chip = TextView(this).apply {
                text = candidate.display
                textSize = 16f
                setTextColor(if (candidate.isRoman) 0xFF616161.toInt() else 0xFF1B5E20.toInt())
                setPadding(24, 12, 24, 12)
                setBackgroundResource(R.drawable.suggestion_chip_bg)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { marginEnd = 8 }
                setOnClickListener { onPick(candidate) }
            }
            row.addView(chip)
        }
    }

    private fun clearSuggestions() {
        suggestionRow?.removeAllViews()
    }

    private fun getCurrentWord(ic: InputConnection): String {
        val before = ic.getTextBeforeCursor(100, 0)?.toString().orEmpty()
        return before.takeLastWhile { !it.isWhitespace() }
    }

    private fun replaceCurrentWord(ic: InputConnection, oldWord: String, newWord: String) {
        if (oldWord.isEmpty()) {
            ic.commitText(newWord, 1)
            return
        }
        ic.deleteSurroundingText(oldWord.length, 0)
        ic.commitText(newWord, 1)
        updateEnglishSuggestions()
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
            view.findViewById<TextView>(id).text = if (shiftOn) letter.uppercase() else letter
        }
    }

    private fun toggleLanguage() {
        if (sinhalaBuffer.isNotEmpty()) commitSinhalaWord()
        language = if (language == Language.SINHALA) Language.ENGLISH else Language.SINHALA
        clearSuggestions()
        updateLanguageUi()
    }

    private fun updateLanguageUi() {
        btnLang?.text = if (language == Language.SINHALA) {
            getString(R.string.mode_sinhala)
        } else {
            getString(R.string.mode_english)
        }
        btnFix?.visibility = if (language == Language.ENGLISH) View.VISIBLE else View.GONE
        suggestionRow?.visibility = View.VISIBLE
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

            val text = withContext(Dispatchers.Main) { getFieldText(ic) }
            if (text.isBlank()) {
                progress?.visibility = View.GONE
                btnFix?.isEnabled = true
                return@launch
            }

            val result = grammarFixer.fixGrammar(text, apiKey)
            progress?.visibility = View.GONE
            btnFix?.isEnabled = true

            result.onSuccess { corrected ->
                replaceFieldText(ic, corrected)
            }.onFailure {
                Toast.makeText(this@KeyboardService, R.string.grammar_error, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun getFieldText(ic: InputConnection): String {
        val before = ic.getTextBeforeCursor(5000, 0)?.toString().orEmpty()
        val after = ic.getTextAfterCursor(5000, 0)?.toString().orEmpty()
        return (before + after).trim()
    }

    private fun replaceFieldText(ic: InputConnection, newText: String) {
        val before = ic.getTextBeforeCursor(5000, 0)?.length ?: 0
        val after = ic.getTextAfterCursor(5000, 0)?.length ?: 0
        ic.beginBatchEdit()
        ic.deleteSurroundingText(before, after)
        ic.commitText(newText, 1)
        ic.endBatchEdit()
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        sinhalaBuffer.clear()
        clearComposingText()
        clearSuggestions()
    }

    override fun onDestroy() {
        stopRepeat()
        scope.cancel()
        super.onDestroy()
    }
}
