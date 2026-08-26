package com.personal.sinhalakeyboard

import android.Manifest
import android.content.pm.PackageManager
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class KeyboardService : InputMethodService() {

    enum class Language { SINHALA, ENGLISH }

    enum class KeyLayout { LETTERS, NUMBERS, SYMBOLS, EMOJI }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val grammarFixer = GrammarFixer()
    private val cloudSuggestionService = CloudSuggestionService()
    private val repeatHandler = Handler(Looper.getMainLooper())
    private var repeatRunnable: Runnable? = null

    private lateinit var singlishEngine: SinglishEngine
    private lateinit var englishSuggestions: EnglishSuggestions
    private lateinit var nextWordPredictor: NextWordPredictor
    private lateinit var typingMemory: TypingMemory

    private var keyboardView: View? = null
    private var suggestionRow: LinearLayout? = null
    private var suggestionScroll: View? = null
    private var toolbarRow: View? = null
    private var btnToolbarExpand: ImageView? = null
    private var btnLang: TextView? = null
    private var btnMic: ImageView? = null
    private var btnFix: TextView? = null
    private var btnTonePro: TextView? = null
    private var btnToneFriendly: TextView? = null
    private var progress: ProgressBar? = null
    private var keyboardKeysPanel: LinearLayout? = null
    private var emojiPanelRoot: View? = null
    private var emojiPanel: EmojiPanel? = null

    private var voiceInputHelper: VoiceInputHelper? = null
    private var englishTone = EnglishTone.PROFESSIONAL
    private var lastCommittedWord: String? = null
    private var toolbarExpanded = false

    private var language = Language.SINHALA
    private var keyLayout = KeyLayout.LETTERS
    private var shiftOn = false
    private var sinhalaBuffer = StringBuilder()
    private var fixJob: Job? = null
    private var sinhalaSuggestionJob: Job? = null
    private var nextWordJob: Job? = null
    private var englishCloudJob: Job? = null

    private var activeTheme = KeyboardTheme.WHITE
    private var keyTextColor = 0xFF212121.toInt()
    private var keyMutedColor = 0xFF616161.toInt()
    private var sinhalaSuggestionColor = 0xFF1B5E20.toInt()
    private var romanSuggestionColor = 0xFF616161.toInt()
    private var suggestionChipBg = R.drawable.suggestion_chip_light

    private val letterKeyIds = listOf(
        R.id.keyQ, R.id.keyW, R.id.keyE, R.id.keyR, R.id.keyT, R.id.keyY, R.id.keyU,
        R.id.keyI, R.id.keyO, R.id.keyP, R.id.keyA, R.id.keyS, R.id.keyD, R.id.keyF,
        R.id.keyG, R.id.keyH, R.id.keyJ, R.id.keyK, R.id.keyL, R.id.keyZ, R.id.keyX,
        R.id.keyC, R.id.keyV, R.id.keyB, R.id.keyN, R.id.keyM,
    )

    private val lettersLower = listOf(
        "q", "w", "e", "r", "t", "y", "u", "i", "o", "p",
        "a", "s", "d", "f", "g", "h", "j", "k", "l",
        "z", "x", "c", "v", "b", "n", "m",
    )

    private val numbersRow1 = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
    private val numbersRow2 = listOf("-", "/", ":", ";", "(", ")", "$", "&", "@", "\"")
    private val numbersRow3Keys = listOf(".", ",", "?", "!", "'", "•", "@")

    private val symbolsRow1 = listOf("[", "]", "{", "}", "#", "%", "^", "*", "+", "=")
    private val symbolsRow2 = listOf("_", "\\", "|", "~", "<", ">", "€", "£", "¥", "•")
    private val symbolsRow3Keys = listOf(".", ",", "?", "!", "'", "•", "@")

    private val themedKeyIds = letterKeyIds + listOf(
        R.id.keyShift, R.id.keyBackspace, R.id.keyNumbers, R.id.keyComma,
        R.id.keySpace, R.id.keyPeriod,
    )

    override fun onCreate() {
        super.onCreate()
        typingMemory = TypingMemory(this)
        singlishEngine = SinglishEngine(this, typingMemory)
        englishSuggestions = EnglishSuggestions(this, typingMemory)
        nextWordPredictor = NextWordPredictor(this, typingMemory)
        voiceInputHelper = VoiceInputHelper(
            context = this,
            onFinal = { text -> insertVoiceText(text) },
            onError = { message -> Toast.makeText(this, message, Toast.LENGTH_SHORT).show() },
            onListeningChanged = { listening -> updateMicButton(listening) },
        ).also { it.prepare() }
    }

    override fun onCreateInputView(): View {
        val view = LayoutInflater.from(this).inflate(R.layout.keyboard_view, null)
        keyboardView = view
        suggestionRow = view.findViewById(R.id.suggestionRow)
        suggestionScroll = view.findViewById(R.id.suggestionScroll)
        toolbarRow = view.findViewById(R.id.toolbarRow)
        btnToolbarExpand = view.findViewById(R.id.btnToolbarExpand)
        btnLang = view.findViewById(R.id.btnLang)
        btnMic = view.findViewById(R.id.btnMic)
        btnFix = view.findViewById(R.id.btnFix)
        btnTonePro = view.findViewById(R.id.btnTonePro)
        btnToneFriendly = view.findViewById(R.id.btnToneFriendly)
        progress = view.findViewById(R.id.progress)
        keyboardKeysPanel = view.findViewById(R.id.keyboardKeysPanel)
        emojiPanelRoot = view.findViewById(R.id.emojiPanel)
        englishTone = Prefs.getEnglishTone(this)

        emojiPanel = EmojiPanel(this, emojiPanelRoot!!) { emoji ->
            insertEmoji(emoji)
        }.also { panel ->
            panel.bind()
            panel.setOnBackListener { showLettersLayout() }
        }

        btnTonePro?.setOnClickListener { setEnglishTone(EnglishTone.PROFESSIONAL) }
        btnToneFriendly?.setOnClickListener { setEnglishTone(EnglishTone.FRIENDLY) }

        setupRepeatKey(view.findViewById(R.id.keyBackspace)) { onBackspace() }
        view.findViewById<TextView>(R.id.keySpace).setOnClickListener { onSpace() }
        view.findViewById<ImageView>(R.id.keyEnter).setOnClickListener { onEnter() }

        btnLang?.setOnClickListener { toggleLanguage() }
        btnMic?.setOnClickListener { toggleVoiceInput() }
        btnFix?.setOnClickListener { fixGrammar() }
        btnToolbarExpand?.setOnClickListener {
            hapticKey()
            toolbarExpanded = true
            updateTopBarMode(hasSuggestions = (suggestionRow?.childCount ?: 0) > 0)
        }

        applyKeyLayout()
        applyTheme()
        updateLanguageUi()
        updateTopBarMode(hasSuggestions = false)
        return view
    }

    private fun applyTheme() {
        activeTheme = Prefs.getTheme(this)
        val view = keyboardView ?: return

        val keyBg: Int
        val btnLangBg: Int
        val btnMicBg: Int
        val btnFixBg: Int
        when (activeTheme) {
            KeyboardTheme.WHITE -> {
                view.findViewById<View>(R.id.keyboardRoot).setBackgroundColor(0xFFECEFF1.toInt())
                keyBg = R.drawable.key_bg_light
                keyTextColor = 0xFF212121.toInt()
                keyMutedColor = 0xFF616161.toInt()
                btnLangBg = R.drawable.toolbar_btn_lang
                btnMicBg = R.drawable.toolbar_btn_mic
                btnFixBg = R.drawable.toolbar_btn_fix
                suggestionChipBg = R.drawable.suggestion_chip_light
                sinhalaSuggestionColor = 0xFF1B5E20.toInt()
                romanSuggestionColor = 0xFF616161.toInt()
            }
            KeyboardTheme.BLACK -> {
                view.findViewById<View>(R.id.keyboardRoot).setBackgroundColor(0xFF121212.toInt())
                keyBg = R.drawable.key_bg_dark
                keyTextColor = 0xFFFFFFFF.toInt()
                keyMutedColor = 0xFFB0BEC5.toInt()
                btnLangBg = R.drawable.toolbar_btn_lang
                btnMicBg = R.drawable.toolbar_btn_mic
                btnFixBg = R.drawable.toolbar_btn_fix
                suggestionChipBg = R.drawable.suggestion_chip_dark
                sinhalaSuggestionColor = 0xFF81C784.toInt()
                romanSuggestionColor = 0xFFB0BEC5.toInt()
            }
        }

        for (id in themedKeyIds) {
            view.findViewById<TextView>(id).apply {
                setBackgroundResource(keyBg)
                setTextColor(if (id == R.id.keySpace) keyMutedColor else keyTextColor)
            }
        }

        btnLang?.apply {
            setBackgroundResource(btnLangBg)
            setTextColor(0xFFFFFFFF.toInt())
        }
        btnMic?.apply {
            setBackgroundResource(btnMicBg)
            setColorFilter(0xFFFFFFFF.toInt())
            alpha = 1f
        }
        btnToolbarExpand?.apply {
            setBackgroundResource(R.drawable.toolbar_btn_expand)
            setColorFilter(0xFFFFFFFF.toInt())
        }
        btnFix?.apply {
            setBackgroundResource(btnFixBg)
            setTextColor(0xFFFFFFFF.toInt())
        }
        updateToneUi()
    }

    private fun applyKeyLayout() {
        val view = keyboardView ?: return
        shiftOn = false

        when (keyLayout) {
            KeyLayout.LETTERS -> bindLettersLayout(view)
            KeyLayout.NUMBERS -> bindNumbersLayout(view)
            KeyLayout.SYMBOLS -> bindSymbolsLayout(view)
            KeyLayout.EMOJI -> bindEmojiLayout(view)
        }
        updateEmojiVisibility()
    }

    private fun updateEmojiVisibility() {
        val showEmoji = keyLayout == KeyLayout.EMOJI
        emojiPanelRoot?.visibility = if (showEmoji) View.VISIBLE else View.GONE
        val panel = keyboardKeysPanel ?: return
        for (i in 0 until panel.childCount - 1) {
            panel.getChildAt(i).visibility = if (showEmoji) View.GONE else View.VISIBLE
        }
    }

    private fun bindEmojiLayout(view: View) {
        view.findViewById<TextView>(R.id.keyComma).apply {
            text = getString(R.string.key_abc)
            setOnClickListener { showLettersLayout() }
        }
        view.findViewById<TextView>(R.id.keyNumbers).apply {
            text = "123"
            setOnClickListener { showNumbersLayout() }
        }
    }

    private fun bindLettersLayout(view: View) {
        letterKeyIds.forEachIndexed { index, id ->
            val letter = lettersLower[index]
            view.findViewById<TextView>(id).apply {
                text = letter
                setOnClickListener { onLetter(letter) }
            }
        }
        view.findViewById<TextView>(R.id.keyShift).apply {
            text = "⇧"
            setOnClickListener { toggleShift() }
        }
        view.findViewById<TextView>(R.id.keyNumbers).apply {
            text = "123"
            setOnClickListener { showNumbersLayout() }
        }
        view.findViewById<TextView>(R.id.keyComma).apply {
            text = "😊"
            setOnClickListener { showEmojiLayout() }
        }
        view.findViewById<TextView>(R.id.keyPeriod).apply {
            text = "."
            setOnClickListener { commitDirect(".") }
        }
    }

    private fun bindNumbersLayout(view: View) {
        bindSymbolRows(view, numbersRow1, numbersRow2, numbersRow3Keys)
        view.findViewById<TextView>(R.id.keyShift).apply {
            text = "#+="
            setOnClickListener { showSymbolsLayout() }
        }
        view.findViewById<TextView>(R.id.keyNumbers).apply {
            text = "ABC"
            setOnClickListener { showLettersLayout() }
        }
        view.findViewById<TextView>(R.id.keyComma).apply {
            text = ","
            setOnClickListener { commitDirect(",") }
        }
        view.findViewById<TextView>(R.id.keyPeriod).apply {
            text = "."
            setOnClickListener { commitDirect(".") }
        }
    }

    private fun bindSymbolsLayout(view: View) {
        bindSymbolRows(view, symbolsRow1, symbolsRow2, symbolsRow3Keys)
        view.findViewById<TextView>(R.id.keyShift).apply {
            text = "123"
            setOnClickListener { showNumbersLayout() }
        }
        view.findViewById<TextView>(R.id.keyNumbers).apply {
            text = "ABC"
            setOnClickListener { showLettersLayout() }
        }
        view.findViewById<TextView>(R.id.keyComma).apply {
            text = ","
            setOnClickListener { commitDirect(",") }
        }
        view.findViewById<TextView>(R.id.keyPeriod).apply {
            text = "."
            setOnClickListener { commitDirect(".") }
        }
    }

    private fun bindSymbolRows(
        view: View,
        row1: List<String>,
        row2: List<String>,
        row3: List<String>,
    ) {
        letterKeyIds.forEachIndexed { index, id ->
            val label = when {
                index < row1.size -> row1[index]
                index < row1.size + row2.size -> row2[index - row1.size]
                else -> row3.getOrElse(index - row1.size - row2.size) { "" }
            }
            view.findViewById<TextView>(id).apply {
                text = label
                if (label.isEmpty()) {
                    setOnClickListener(null)
                    isClickable = false
                } else {
                    isClickable = true
                    setOnClickListener { commitDirect(label) }
                }
            }
        }
    }

    private fun showEmojiLayout() {
        hapticKey()
        if (sinhalaBuffer.isNotEmpty()) commitSinhalaWord()
        keyLayout = KeyLayout.EMOJI
        applyKeyLayout()
    }

    private fun showLettersLayout() {
        hapticKey()
        if (sinhalaBuffer.isNotEmpty()) commitSinhalaWord()
        keyLayout = KeyLayout.LETTERS
        applyKeyLayout()
    }

    private fun showNumbersLayout() {
        hapticKey()
        if (sinhalaBuffer.isNotEmpty()) commitSinhalaWord()
        keyLayout = KeyLayout.NUMBERS
        applyKeyLayout()
    }

    private fun showSymbolsLayout() {
        hapticKey()
        if (sinhalaBuffer.isNotEmpty()) commitSinhalaWord()
        keyLayout = KeyLayout.SYMBOLS
        applyKeyLayout()
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

    private fun hapticKey() {
        HapticHelper.keyTap(this, keyboardView)
    }

    private fun onLetter(letter: String) {
        hapticKey()
        val ch = if (shiftOn) letter.uppercase() else letter
        if (language == Language.ENGLISH) {
            currentInputConnection?.commitText(ch, 1)
            if (shiftOn) toggleShift()
            updateEnglishSuggestions()
            return
        }

        sinhalaBuffer.append(ch)
        if (shiftOn) toggleShift()
        updateComposingText()
        updateSinhalaSuggestions()
    }

    private fun onSpace() {
        hapticKey()
        val ic = currentInputConnection ?: return
        if (language == Language.SINHALA && sinhalaBuffer.isNotEmpty()) {
            commitSinhalaWord()
        } else if (language == Language.ENGLISH) {
            val word = getCurrentWord(ic)
            if (word.isNotEmpty()) rememberWordCommitted(word)
        }
        ic.commitText(" ", 1)
        updateNextWordSuggestions()
    }

    private fun onEnter() {
        hapticKey()
        if (language == Language.SINHALA && sinhalaBuffer.isNotEmpty()) {
            commitSinhalaWord()
        }
        currentInputConnection?.commitText("\n", 1)
        clearSuggestions()
    }

    private fun onBackspace() {
        hapticKey()
        if (language == Language.SINHALA && sinhalaBuffer.isNotEmpty()) {
            sinhalaBuffer.deleteCharAt(sinhalaBuffer.length - 1)
            updateComposingText()
            updateSinhalaSuggestions()
            return
        }
        currentInputConnection?.deleteSurroundingText(1, 0)
        val ic = currentInputConnection ?: return
        if (language == Language.ENGLISH) {
            if (getCurrentWord(ic).isEmpty()) updateNextWordSuggestions()
            else updateEnglishSuggestions()
        } else if (sinhalaBuffer.isEmpty() && getCurrentWord(ic).isEmpty()) {
            updateNextWordSuggestions()
        }
    }

    private fun commitSinhalaWord(sinhala: String? = null) {
        val roman = sinhalaBuffer.toString()
        val word = sinhala ?: singlishEngine.transliterate(roman)
        if (word.isEmpty()) return
        currentInputConnection?.commitText(word, 1)
        rememberWordCommitted(word, roman)
        sinhalaBuffer.clear()
        clearComposingText()
    }

    private fun rememberWordCommitted(word: String, roman: String? = null) {
        val cleaned = word.trim()
        if (cleaned.isEmpty()) return
        val sinhala = language == Language.SINHALA
        if (sinhala) {
            val key = roman?.trim().orEmpty()
            if (key.isNotEmpty()) {
                typingMemory.rememberSinhala(key, cleaned)
            }
        } else {
            typingMemory.rememberEnglish(cleaned)
        }
        lastCommittedWord?.let { prev ->
            typingMemory.rememberBigram(prev, cleaned, sinhala)
        }
        lastCommittedWord = cleaned
    }

    private fun rememberVoiceWords(text: String) {
        val sinhala = language == Language.SINHALA
        val tokens = text.split(Regex("\\s+")).filter { it.isNotBlank() }
        for (token in tokens) {
            if (!sinhala) {
                typingMemory.rememberEnglish(token)
            }
            lastCommittedWord?.let { prev ->
                typingMemory.rememberBigram(prev, token, sinhala)
            }
            lastCommittedWord = token
        }
    }

    private fun commitRomanWord(roman: String) {
        currentInputConnection?.commitText(roman, 1)
        sinhalaBuffer.clear()
        clearComposingText()
        clearSuggestions()
    }

    private fun commitDirect(text: String) {
        hapticKey()
        if (language == Language.SINHALA && sinhalaBuffer.isNotEmpty()) {
            commitSinhalaWord()
        }
        currentInputConnection?.commitText(text, 1)
    }

    private fun updateComposingText() {
        val ic = currentInputConnection ?: return
        ic.beginBatchEdit()
        if (sinhalaBuffer.isEmpty()) {
            ic.setComposingText("", 0)
        } else {
            // Show exact Singlish typed; Sinhala appears only in suggestion row until picked or space.
            val typed = sinhalaBuffer.toString()
            ic.setComposingText(typed, typed.length)
        }
        ic.endBatchEdit()
    }

    private fun clearComposingText() {
        currentInputConnection?.finishComposingText()
    }

    private fun updateSinhalaSuggestions() {
        if (sinhalaBuffer.isEmpty()) {
            sinhalaSuggestionJob?.cancel()
            clearSuggestions()
            return
        }
        val typed = sinhalaBuffer.toString()
        sinhalaSuggestionJob?.cancel()
        sinhalaSuggestionJob = scope.launch {
            val items = withContext(Dispatchers.Default) {
                singlishEngine.suggestions(typed)
            }
            if (sinhalaBuffer.toString() != typed) return@launch
            renderSuggestions(items) { candidate ->
                if (candidate.isRoman) {
                    commitRomanWord(candidate.commitText)
                } else {
                    commitSinhalaWord(candidate.commitText)
                }
            }
        }
    }

    private fun updateEnglishSuggestions() {
        val ic = currentInputConnection ?: return
        val word = getCurrentWord(ic)
        if (word.isEmpty()) {
            englishCloudJob?.cancel()
            updateNextWordSuggestions()
            return
        }
        englishCloudJob?.cancel()
        englishSuggestions.suggest(word) { items ->
            val liveIc = currentInputConnection ?: return@suggest
            if (getCurrentWord(liveIc) != word) return@suggest
            renderSuggestions(items) { candidate ->
                replaceCurrentWord(liveIc, word, candidate.commitText)
            }
            fetchEnglishCloudWordCompletions(word, items)
        }
    }

    private fun fetchEnglishCloudWordCompletions(
        partialWord: String,
        localItems: List<SuggestionCandidate>,
    ) {
        if (language != Language.ENGLISH || !Prefs.isCloudSuggestionsEnabled(this)) return
        val apiKey = Prefs.getApiKey(this)
        if (apiKey.isBlank() || partialWord.length < 2) return

        englishCloudJob = scope.launch {
            delay(400)
            val ic = currentInputConnection ?: return@launch
            if (getCurrentWord(ic) != partialWord) return@launch
            val cloudResult = cloudSuggestionService.predictWordCompletions(
                contextText = getContextBeforeCursor(ic),
                partialWord = partialWord,
                apiKey = apiKey,
                tone = englishTone,
            )
            val cloudWords = cloudResult.getOrNull().orEmpty()
            if (cloudWords.isEmpty()) return@launch
            if (getCurrentWord(currentInputConnection ?: return@launch) != partialWord) return@launch
            val merged = mergeCloudSuggestions(localItems, cloudWords, partialWord, isNextWord = false)
            renderSuggestions(merged) { candidate ->
                replaceCurrentWord(currentInputConnection ?: return@renderSuggestions, partialWord, candidate.commitText)
            }
        }
    }

    private fun updateNextWordSuggestions() {
        val ic = currentInputConnection ?: return
        if (sinhalaBuffer.isNotEmpty()) return
        val lastWord = getLastWord(ic)
        if (lastWord.isEmpty()) {
            clearSuggestions()
            return
        }
        val sinhala = language == Language.SINHALA
        val tone = if (sinhala) EnglishTone.PROFESSIONAL else englishTone
        val local = nextWordPredictor.predict(lastWord, sinhala, tone)
        if (local.isEmpty() && !Prefs.isCloudSuggestionsEnabled(this)) {
            clearSuggestions()
            return
        }
        renderSuggestions(local) { candidate ->
            commitNextWord(candidate)
        }
        if (!Prefs.isCloudSuggestionsEnabled(this)) return
        val apiKey = Prefs.getApiKey(this)
        if (apiKey.isBlank()) return

        nextWordJob?.cancel()
        val contextSnapshot = getContextBeforeCursor(ic)
        nextWordJob = scope.launch {
            delay(350)
            val liveIc = currentInputConnection ?: return@launch
            if (getLastWord(liveIc) != lastWord || sinhalaBuffer.isNotEmpty()) return@launch
            val cloudResult = if (sinhala) {
                cloudSuggestionService.predictNextWords(
                    contextText = contextSnapshot,
                    sinhala = true,
                    apiKey = apiKey,
                    tone = tone,
                )
            } else {
                cloudSuggestionService.predictNextCompletions(
                    contextText = contextSnapshot,
                    apiKey = apiKey,
                    tone = tone,
                )
            }
            val cloudWords = cloudResult.getOrNull().orEmpty()
            if (cloudWords.isEmpty()) return@launch
            if (getLastWord(currentInputConnection ?: return@launch) != lastWord) return@launch
            val merged = mergeCloudSuggestions(local, cloudWords, prefixHint = null, isNextWord = true)
            renderSuggestions(merged) { candidate ->
                commitNextWord(candidate)
            }
        }
    }

    private fun mergeCloudSuggestions(
        local: List<SuggestionCandidate>,
        cloudItems: List<String>,
        prefixHint: String?,
        isNextWord: Boolean,
    ): List<SuggestionCandidate> {
        val seen = local.map { it.commitText.lowercase() }.toMutableSet()
        val merged = local.toMutableList()
        for (raw in cloudItems) {
            val text = raw.trim()
            if (text.isBlank()) continue
            val commit = formatCloudSuggestion(text, prefixHint)
            if (!seen.add(commit.lowercase())) continue
            merged.add(
                SuggestionCandidate(
                    display = truncateSuggestionDisplay(commit),
                    commitText = commit,
                    isNextWord = isNextWord,
                    isCloud = true,
                ),
            )
            if (merged.size >= 8) break
        }
        return merged
    }

    private fun formatCloudSuggestion(text: String, prefixHint: String?): String {
        if (prefixHint.isNullOrEmpty()) return text
        return text.replaceFirstChar { c ->
            if (prefixHint.firstOrNull()?.isUpperCase() == true) c.uppercaseChar() else c
        }
    }

    private fun truncateSuggestionDisplay(text: String, maxLen: Int = 48): String {
        if (text.length <= maxLen) return text
        return text.take(maxLen - 1).trimEnd() + "…"
    }

    private fun commitNextWord(candidate: SuggestionCandidate) {
        val ic = currentInputConnection ?: return
        ic.commitText("${candidate.commitText} ", 1)
        rememberWordCommitted(candidate.commitText)
        updateNextWordSuggestions()
    }

    private fun renderSuggestions(
        items: List<SuggestionCandidate>,
        onPick: (SuggestionCandidate) -> Unit,
    ) {
        if (items.isNotEmpty()) {
            toolbarExpanded = false
        }
        val row = suggestionRow ?: return
        row.removeAllViews()
        items.forEach { candidate ->
            val chip = TextView(this).apply {
                text = candidate.display
                textSize = 15f
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                setTextColor(
                    when {
                        candidate.isPersonal -> if (activeTheme == KeyboardTheme.BLACK) {
                            0xFFFFB74D.toInt()
                        } else {
                            0xFFE65100.toInt()
                        }
                        candidate.isCloud -> if (activeTheme == KeyboardTheme.BLACK) {
                            0xFF90CAF9.toInt()
                        } else {
                            0xFF1565C0.toInt()
                        }
                        candidate.isRoman -> romanSuggestionColor
                        else -> sinhalaSuggestionColor
                    },
                )
                setPadding(20, 8, 20, 8)
                setBackgroundResource(suggestionChipBg)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { marginEnd = 6 }
                setOnClickListener {
                    hapticKey()
                    onPick(candidate)
                }
            }
            row.addView(chip)
        }
        updateTopBarMode(hasSuggestions = items.isNotEmpty())
    }

    private fun updateTopBarMode(hasSuggestions: Boolean) {
        val showSuggestions = hasSuggestions && !toolbarExpanded
        btnToolbarExpand?.visibility = if (showSuggestions) View.VISIBLE else View.GONE
        toolbarRow?.visibility = if (showSuggestions) View.GONE else View.VISIBLE
        suggestionScroll?.visibility = if (showSuggestions) View.VISIBLE else View.GONE
    }

    private fun clearSuggestions() {
        nextWordJob?.cancel()
        englishCloudJob?.cancel()
        suggestionRow?.removeAllViews()
        toolbarExpanded = false
        updateTopBarMode(hasSuggestions = false)
    }

    private fun toggleVoiceInput() {
        val helper = voiceInputHelper ?: return
        if (helper.isListening()) {
            helper.stop()
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(this, R.string.mic_permission_needed, Toast.LENGTH_LONG).show()
            return
        }
        if (language == Language.SINHALA && sinhalaBuffer.isNotEmpty()) {
            commitSinhalaWord()
        }
        val locale = if (language == Language.SINHALA) "si-LK" else "en-US"
        val continuous = Prefs.isContinuousVoice(this)
        updateMicButton(listening = true)
        helper.start(locale, continuousMode = continuous)
    }

    private fun updateMicButton(listening: Boolean) {
        val helper = voiceInputHelper
        btnMic?.apply {
            when {
                listening && helper?.isContinuous() == true -> {
                    setColorFilter(0xFFFF5252.toInt())
                    alpha = 1f
                }
                listening -> {
                    setColorFilter(0xFFFFFFFF.toInt())
                    alpha = 0.55f
                }
                else -> {
                    setColorFilter(0xFFFFFFFF.toInt())
                    alpha = 1f
                }
            }
        }
    }

    private fun insertEmoji(emoji: String) {
        if (emoji.isEmpty()) return
        hapticKey()
        if (language == Language.SINHALA && sinhalaBuffer.isNotEmpty()) {
            commitSinhalaWord()
        }
        currentInputConnection?.commitText(emoji, 1)
        clearSuggestions()
    }

    private fun setEnglishTone(tone: EnglishTone) {
        englishTone = tone
        Prefs.setEnglishTone(this, tone)
        updateToneUi()
        if (language == Language.ENGLISH) {
            val ic = currentInputConnection
            if (ic != null && getCurrentWord(ic).isEmpty()) {
                updateNextWordSuggestions()
            }
        }
    }

    private fun updateToneUi() {
        val pro = btnTonePro ?: return
        val friendly = btnToneFriendly ?: return
        val proActive = englishTone == EnglishTone.PROFESSIONAL
        pro.setBackgroundResource(
            if (proActive) R.drawable.toolbar_btn_tone_on else R.drawable.toolbar_btn_tone_off,
        )
        friendly.setBackgroundResource(
            if (!proActive) R.drawable.toolbar_btn_tone_on else R.drawable.toolbar_btn_tone_off,
        )
        pro.setTextColor(if (proActive) 0xFFFFFFFF.toInt() else 0xFFCFD8DC.toInt())
        friendly.setTextColor(if (!proActive) 0xFFFFFFFF.toInt() else 0xFFCFD8DC.toInt())
    }

    private fun insertVoiceText(text: String) {
        val ic = currentInputConnection ?: return
        if (language == Language.SINHALA) {
            sinhalaBuffer.clear()
            clearComposingText()
        }
        ic.commitText("$text ", 1)
        rememberVoiceWords(text.trim())
        updateNextWordSuggestions()
    }

    private fun getLastWord(ic: InputConnection): String {
        val before = ic.getTextBeforeCursor(1000, 0)?.toString().orEmpty().trimEnd()
        if (before.isEmpty()) return ""
        val lastToken = before.substringAfterLast('\n').substringAfterLast(' ')
        return lastToken.trimEnd { !it.isLetter() && it != '\'' }
    }

    private fun getContextBeforeCursor(ic: InputConnection): String {
        return ic.getTextBeforeCursor(500, 0)?.toString().orEmpty()
    }

    private fun getCurrentWord(ic: InputConnection): String {
        val before = ic.getTextBeforeCursor(1000, 0)?.toString().orEmpty()
        val raw = before.takeLastWhile { !it.isWhitespace() && it != '\n' }
        return raw.trimEnd { !it.isLetter() && it != '\'' }
    }

    private fun replaceCurrentWord(ic: InputConnection, oldWord: String, newWord: String) {
        if (oldWord.isEmpty()) {
            ic.commitText(newWord, 1)
            return
        }
        val before = ic.getTextBeforeCursor(oldWord.length + 5, 0)?.toString().orEmpty()
        val toDelete = before.takeLastWhile { !it.isWhitespace() && it != '\n' }
        ic.deleteSurroundingText(toDelete.length, 0)
        ic.commitText(newWord, 1)
        rememberWordCommitted(newWord)
        updateEnglishSuggestions()
    }

    private fun toggleShift() {
        hapticKey()
        if (keyLayout != KeyLayout.LETTERS) return
        shiftOn = !shiftOn
        refreshKeyLabels()
    }

    private fun refreshKeyLabels() {
        val view = keyboardView ?: return
        if (keyLayout != KeyLayout.LETTERS) return
        letterKeyIds.forEachIndexed { index, id ->
            val letter = lettersLower[index]
            view.findViewById<TextView>(id).text =
                if (shiftOn) letter.uppercase() else letter
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
        val showTone = language == Language.ENGLISH
        btnTonePro?.visibility = if (showTone) View.VISIBLE else View.GONE
        btnToneFriendly?.visibility = if (showTone) View.VISIBLE else View.GONE
        if (showTone) updateToneUi()
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

            Toast.makeText(this@KeyboardService, R.string.fixing_grammar, Toast.LENGTH_SHORT).show()

            val result = grammarFixer.fixGrammar(text, apiKey, englishTone)
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
        val extracted = ic.getExtractedText(ExtractedTextRequest(), 0)
        if (extracted?.text != null) {
            val full = extracted.text.toString().trim()
            if (full.isNotEmpty()) return full
        }
        val before = ic.getTextBeforeCursor(10000, 0)?.toString().orEmpty()
        val after = ic.getTextAfterCursor(10000, 0)?.toString().orEmpty()
        return (before + after).trim()
    }

    private fun replaceFieldText(ic: InputConnection, newText: String) {
        val extracted = ic.getExtractedText(ExtractedTextRequest(), 0)
        if (extracted?.text != null) {
            val fullLen = extracted.text.length
            ic.beginBatchEdit()
            ic.setSelection(0, fullLen)
            ic.commitText(newText, 1)
            ic.endBatchEdit()
            return
        }
        val before = ic.getTextBeforeCursor(10000, 0)?.length ?: 0
        val after = ic.getTextAfterCursor(10000, 0)?.length ?: 0
        ic.beginBatchEdit()
        ic.deleteSurroundingText(before, after)
        ic.commitText(newText, 1)
        ic.endBatchEdit()
    }

    override fun onUpdateSelection(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        candidatesStart: Int,
        candidatesEnd: Int,
    ) {
        super.onUpdateSelection(
            oldSelStart, oldSelEnd, newSelStart, newSelEnd,
            candidatesStart, candidatesEnd,
        )
        if (language == Language.SINHALA && sinhalaBuffer.isNotEmpty()) {
            if (candidatesStart < 0 || candidatesEnd < 0) {
                sinhalaBuffer.clear()
                clearSuggestions()
            }
        }
        if (language == Language.ENGLISH && newSelStart == newSelEnd) {
            updateEnglishSuggestions()
        }
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        sinhalaBuffer.clear()
        clearComposingText()
        clearSuggestions()
        lastCommittedWord = null
        keyLayout = KeyLayout.LETTERS
        englishTone = Prefs.getEnglishTone(this)
        voiceInputHelper?.prepare()
        applyKeyLayout()
        applyTheme()
    }

    override fun onFinishInput() {
        sinhalaBuffer.clear()
        clearComposingText()
        clearSuggestions()
        super.onFinishInput()
    }

    override fun onDestroy() {
        stopRepeat()
        voiceInputHelper?.destroy()
        voiceInputHelper = null
        englishSuggestions.close()
        scope.cancel()
        super.onDestroy()
    }
}
