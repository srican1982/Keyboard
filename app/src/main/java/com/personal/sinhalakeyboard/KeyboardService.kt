package com.personal.sinhalakeyboard

import android.content.Intent
import android.Manifest
import android.content.pm.PackageManager
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.InputType
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
import java.text.BreakIterator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class KeyboardService : InputMethodService() {

    enum class Language { SINHALA, ENGLISH }

    enum class KeyLayout { LETTERS, NUMBERS, SYMBOLS, EMOJI }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val grammarFixer = GrammarFixer()
    private val promptEnhancer = PromptEnhancer()
    private val singlishTranslator = SinglishTranslator()
    private val cloudSuggestionService = CloudSuggestionService()
    private val repeatHandler = Handler(Looper.getMainLooper())
    private var repeatRunnable: Runnable? = null
    private var backspaceRepeating = false

    private lateinit var singlishEngine: SinglishEngine
    private lateinit var englishSuggestions: EnglishSuggestions
    private lateinit var nextWordPredictor: NextWordPredictor
    private lateinit var typingMemory: TypingMemory
    private lateinit var personalHistory: PersonalHistoryDatabase

    private var keyboardView: View? = null
    private var suggestionRow: LinearLayout? = null
    private var suggestionScroll: View? = null
    private var recentEmojiRow: LinearLayout? = null
    private var recentEmojiScroll: View? = null
    private var toolbarRow: View? = null
    private var btnToolbarExpand: ImageView? = null
    private var keyLangBottom: TextView? = null
    private var btnMic: ImageView? = null
    private var btnFix: TextView? = null
    private var btnPrompt: TextView? = null
    private var btnToEnglish: TextView? = null
    private var btnTonePro: TextView? = null
    private var btnToneFriendly: TextView? = null
    private var keyEnter: ImageView? = null
    private var progress: ProgressBar? = null
    private var keyboardKeysPanel: LinearLayout? = null
    private var emojiPanelRoot: View? = null
    private var emojiPanel: EmojiPanel? = null

    private var voiceInputHelper: VoiceInputHelper? = null
    private var englishTone = EnglishTone.PROFESSIONAL
    private var lastCommittedWord: String? = null
    private var toolbarExpanded = false
    private var toolbarCompact = false

    private var language = Language.SINHALA
    private var keyLayout = KeyLayout.LETTERS
    private var shiftOn = false
    private var shiftLocked = false
    private var sinhalaBuffer = StringBuilder()
    private var enterIsSearch = false
    private var currentEditorInfo: EditorInfo? = null
    private var fixJob: Job? = null
    private var promptJob: Job? = null
    private var translateJob: Job? = null
    private var nextWordJob: Job? = null
    private var englishCloudJob: Job? = null
    private var sinhalaCloudJob: Job? = null
    private var sinhalaLocalSuggestJob: Job? = null
    private var englishLocalSuggestJob: Job? = null
    private val emojisSinceSend = LinkedHashSet<String>()

    private var activeTheme = KeyboardTheme.WHITE
    private var keyTextColor = 0xFF212121.toInt()
    private var keyMutedColor = 0xFF616161.toInt()
    private var sinhalaSuggestionColor = 0xFF1B5E20.toInt()
    private var romanSuggestionColor = 0xFF616161.toInt()
    private var suggestionChipBg = R.drawable.suggestion_chip_light

    private val sinhalaSuggestRunnable = Runnable { updateSinhalaSuggestions() }
    private val englishSuggestRunnable = Runnable { updateEnglishSuggestions() }

    private companion object {
        const val SUGGEST_DEBOUNCE_MS = 60L

        // Cloud AI is sent only after the user pauses typing.
        const val SINHALA_AI_PAUSE_MS = 320L
        const val ENGLISH_AI_PAUSE_MS = 450L
    }

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
        R.id.keyShift, R.id.keyBackspace, R.id.keyNumbers,
        R.id.keySpace, R.id.keyBackspaceBottom,
    )

    override fun onCreate() {
        super.onCreate()
        typingMemory = TypingMemory(this)
        personalHistory = PersonalHistoryDatabase(this)
        singlishEngine = SinglishEngine(this, personalHistory, typingMemory)
        englishSuggestions = EnglishSuggestions(this, personalHistory)
        nextWordPredictor = NextWordPredictor(this, typingMemory, personalHistory)
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
        recentEmojiRow = view.findViewById(R.id.recentEmojiRow)
        recentEmojiScroll = view.findViewById(R.id.recentEmojiScroll)
        toolbarRow = view.findViewById(R.id.toolbarRow)
        btnToolbarExpand = view.findViewById(R.id.btnToolbarExpand)
        keyLangBottom = view.findViewById(R.id.keyLangBottom)
        btnMic = view.findViewById(R.id.btnMic)
        btnFix = view.findViewById(R.id.btnFix)
        btnPrompt = view.findViewById(R.id.btnPrompt)
        btnToEnglish = view.findViewById(R.id.btnToEnglish)
        btnTonePro = view.findViewById(R.id.btnTonePro)
        btnToneFriendly = view.findViewById(R.id.btnToneFriendly)
        keyEnter = view.findViewById(R.id.keyEnter)
        progress = view.findViewById(R.id.progress)
        keyboardKeysPanel = view.findViewById(R.id.keyboardKeysPanel)
        emojiPanelRoot = view.findViewById(R.id.emojiPanel)
        englishTone = Prefs.getEnglishTone(this)

        emojiPanel = EmojiPanel(this, emojiPanelRoot!!) { emoji ->
            insertEmoji(emoji)
        }.also { it.bind() }

        btnTonePro?.let { setupInstantKey(it) { setEnglishTone(EnglishTone.PROFESSIONAL) } }
        btnToneFriendly?.let { setupInstantKey(it) { setEnglishTone(EnglishTone.FRIENDLY) } }

        val backspaceHandler = object {
            fun onInitial() = onBackspaceTap()
            fun onRepeat() {
                backspaceRepeating = true
                deleteOneCharacter()
            }
            fun onRelease() {
                if (backspaceRepeating) {
                    backspaceRepeating = false
                    refreshAfterBackspace()
                }
            }
        }
        setupRepeatKey(
            view.findViewById(R.id.keyBackspace),
            onInitial = { backspaceHandler.onInitial() },
            onRepeat = { backspaceHandler.onRepeat() },
            onRelease = { backspaceHandler.onRelease() },
        )
        setupRepeatKey(
            view.findViewById(R.id.keyBackspaceBottom),
            onInitial = { backspaceHandler.onInitial() },
            onRepeat = { backspaceHandler.onRepeat() },
            onRelease = { backspaceHandler.onRelease() },
        )
        setupInstantKey(view.findViewById(R.id.keySpace)) { onSpace() }
        keyEnter?.let { setupInstantKey(it) { onEnter() } }

        keyLangBottom?.let { setupInstantKey(it) { toggleLanguage() } }
        btnMic?.let { setupInstantKey(it) { toggleVoiceInput() } }
        btnFix?.let { setupInstantKey(it) { fixGrammar() } }
        btnPrompt?.let { setupInstantKey(it) { enhancePrompt() } }
        btnToEnglish?.let { setupInstantKey(it) { translateSinglishToEnglish() } }
        btnToolbarExpand?.let {
            setupInstantKey(it) {
                hapticKey()
                toolbarExpanded = true
                updateTopBarMode()
            }
        }

        applyKeyLayout()
        applyTheme()
        updateLanguageUi()
        updateRecentEmojiRow()
        updateTopBarMode()
        return view
    }

    private fun applyTheme() {
        activeTheme = Prefs.getTheme(this)
        val view = keyboardView ?: return

        val keyBg: Int
        val btnMicBg: Int
        val btnFixBg: Int
        when (activeTheme) {
            KeyboardTheme.WHITE -> {
                view.findViewById<View>(R.id.keyboardRoot).setBackgroundColor(0xFFECEFF1.toInt())
                keyBg = R.drawable.key_bg_light
                keyTextColor = 0xFF212121.toInt()
                keyMutedColor = 0xFF616161.toInt()
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

        btnMic?.apply {
            setBackgroundResource(btnMicBg)
            setColorFilter(0xFFFFFFFF.toInt())
            alpha = 1f
        }
        btnToolbarExpand?.apply {
            setBackgroundResource(R.drawable.toolbar_btn_expand)
            setColorFilter(0xFFFFFFFF.toInt())
        }
        applyCommaKeyTheme(view, keyBg)
        applyPeriodKeyTheme(view, keyBg)
        btnFix?.apply {
            setBackgroundResource(btnFixBg)
            setTextColor(0xFFFFFFFF.toInt())
        }
        btnPrompt?.apply {
            setBackgroundResource(R.drawable.toolbar_btn_prompt)
            setTextColor(0xFFFFFFFF.toInt())
        }
        btnToEnglish?.apply {
            setBackgroundResource(R.drawable.toolbar_btn_translate)
            setTextColor(0xFFFFFFFF.toInt())
        }
        updateToneUi()
        updateLanguageUi()
        if (keyLayout == KeyLayout.LETTERS) {
            updateShiftKeyAppearance()
        }
    }

    private fun applyCommaKeyTheme(view: View, keyBg: Int) {
        view.findViewById<View>(R.id.keyComma).setBackgroundResource(keyBg)
        view.findViewById<TextView>(R.id.keyCommaEmoji).setTextColor(keyTextColor)
        view.findViewById<TextView>(R.id.keyCommaSymbol).setTextColor(keyTextColor)
    }

    private fun applyPeriodKeyTheme(view: View, keyBg: Int) {
        view.findViewById<View>(R.id.keyPeriod).setBackgroundResource(keyBg)
        view.findViewById<TextView>(R.id.keyPeriodAt).setTextColor(keyMutedColor)
        view.findViewById<TextView>(R.id.keyPeriodSymbol).setTextColor(keyTextColor)
    }

    private fun applyKeyLayout() {
        val view = keyboardView ?: return
        if (keyLayout == KeyLayout.LETTERS) {
            shiftOn = shiftLocked
        } else {
            shiftOn = false
        }

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
        val view = keyboardView ?: return
        view.findViewById<View>(R.id.keyLangBottom).visibility = if (showEmoji) View.GONE else View.VISIBLE
        view.findViewById<View>(R.id.keySpace).visibility = if (showEmoji) View.GONE else View.VISIBLE
        view.findViewById<View>(R.id.keyPeriod).visibility = if (showEmoji) View.GONE else View.VISIBLE
        view.findViewById<View>(R.id.keyEnter).visibility = if (showEmoji) View.GONE else View.VISIBLE
        view.findViewById<View>(R.id.keyBackspaceBottom).visibility = if (showEmoji) View.VISIBLE else View.GONE
        updateBottomRowSpacing(showEmoji)
        updateRecentEmojiBarVisibility()
        updateLanguageUi()
    }

    private fun updateBottomRowSpacing(showEmoji: Boolean) {
        val view = keyboardView ?: return
        val marginPx = (if (showEmoji) 8 else 1) * resources.displayMetrics.density
        val margin = marginPx.toInt()
        if (showEmoji) {
            listOf(R.id.keyNumbers, R.id.keyComma, R.id.keyBackspaceBottom).forEach { id ->
                val v = view.findViewById<View>(id)
                val lp = v.layoutParams as LinearLayout.LayoutParams
                lp.weight = 1f
                lp.width = 0
                lp.setMargins(margin, lp.topMargin, margin, lp.bottomMargin)
                v.layoutParams = lp
            }
        } else {
            val weights = mapOf(
                R.id.keyNumbers to 1.4f,
                R.id.keyComma to 1f,
                R.id.keyLangBottom to 1f,
                R.id.keySpace to 4f,
                R.id.keyPeriod to 1f,
                R.id.keyEnter to 1.4f,
            )
            weights.forEach { (id, weight) ->
                val v = view.findViewById<View>(id) ?: return@forEach
                val lp = v.layoutParams as LinearLayout.LayoutParams
                lp.weight = weight
                lp.width = 0
                lp.setMargins(margin, lp.topMargin, margin, lp.bottomMargin)
                v.layoutParams = lp
            }
        }
    }

    private fun bindEmojiLayout(view: View) {
        view.findViewById<View>(R.id.keyCommaEmoji).visibility = View.GONE
        view.findViewById<TextView>(R.id.keyCommaSymbol).apply {
            visibility = View.VISIBLE
            text = getString(R.string.key_abc)
            textSize = 16f
        }
        clearKeyTouchListener(view.findViewById(R.id.keyComma))
        setupInstantKey(view.findViewById(R.id.keyComma)) { showLettersLayout() }
        view.findViewById<TextView>(R.id.keyNumbers).apply {
            text = "123"
            setupInstantKey(this) { showNumbersLayout() }
        }
    }

    private fun bindLettersLayout(view: View) {
        letterKeyIds.forEachIndexed { index, id ->
            val letter = lettersLower[index]
            view.findViewById<TextView>(id).apply {
                text = letter
                setupInstantKey(this) { onLetter(letter) }
            }
        }
        view.findViewById<TextView>(R.id.keyShift).apply {
            setupShiftKey(this)
        }
        view.findViewById<TextView>(R.id.keyNumbers).apply {
            text = "123"
            setupInstantKey(this) { showNumbersLayout() }
        }
        view.findViewById<View>(R.id.keyCommaEmoji).visibility = View.VISIBLE
        view.findViewById<TextView>(R.id.keyCommaSymbol).apply {
            visibility = View.VISIBLE
            text = ","
            textSize = 16f
        }
        setupEmojiCommaKey(view.findViewById(R.id.keyComma))
        view.findViewById<View>(R.id.keyPeriodAt).visibility = View.VISIBLE
        view.findViewById<TextView>(R.id.keyPeriodSymbol).apply {
            visibility = View.VISIBLE
            text = "."
            textSize = 18f
        }
        setupAtPeriodKey(view.findViewById(R.id.keyPeriod))
        view.findViewById<View>(R.id.keyA).apply {
            val lp = layoutParams as LinearLayout.LayoutParams
            lp.weight = 1.28f
            layoutParams = lp
        }
    }

    private fun bindNumbersLayout(view: View) {
        bindSymbolRows(view, numbersRow1, numbersRow2, numbersRow3Keys)
        view.findViewById<TextView>(R.id.keyShift).apply {
            text = "#+="
            clearKeyTouchListener(this)
            setupInstantKey(this) { showSymbolsLayout() }
        }
        view.findViewById<TextView>(R.id.keyNumbers).apply {
            text = "ABC"
            setupInstantKey(this) { showLettersLayout() }
        }
        view.findViewById<View>(R.id.keyCommaEmoji).visibility = View.GONE
        view.findViewById<TextView>(R.id.keyCommaSymbol).apply {
            visibility = View.VISIBLE
            text = ","
            textSize = 18f
        }
        clearKeyTouchListener(view.findViewById(R.id.keyComma))
        setupInstantKey(view.findViewById(R.id.keyComma)) { commitDirect(",") }
        bindPeriodAsDotOnly(view)
    }

    private fun bindSymbolsLayout(view: View) {
        bindSymbolRows(view, symbolsRow1, symbolsRow2, symbolsRow3Keys)
        view.findViewById<TextView>(R.id.keyShift).apply {
            text = "123"
            clearKeyTouchListener(this)
            setupInstantKey(this) { showNumbersLayout() }
        }
        view.findViewById<TextView>(R.id.keyNumbers).apply {
            text = "ABC"
            setupInstantKey(this) { showLettersLayout() }
        }
        view.findViewById<View>(R.id.keyCommaEmoji).visibility = View.GONE
        view.findViewById<TextView>(R.id.keyCommaSymbol).apply {
            visibility = View.VISIBLE
            text = ","
            textSize = 18f
        }
        clearKeyTouchListener(view.findViewById(R.id.keyComma))
        setupInstantKey(view.findViewById(R.id.keyComma)) { commitDirect(",") }
        bindPeriodAsDotOnly(view)
    }

    private fun bindPeriodAsDotOnly(view: View) {
        view.findViewById<View>(R.id.keyPeriodAt).visibility = View.GONE
        view.findViewById<TextView>(R.id.keyPeriodSymbol).apply {
            visibility = View.VISIBLE
            text = "."
            textSize = 18f
        }
        clearKeyTouchListener(view.findViewById(R.id.keyPeriod))
        setupInstantKey(view.findViewById(R.id.keyPeriod)) { commitDirect(".") }
        view.findViewById<View>(R.id.keyA).apply {
            val lp = layoutParams as LinearLayout.LayoutParams
            lp.weight = 1f
            layoutParams = lp
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
                    setOnTouchListener(null)
                    isClickable = false
                } else {
                    isClickable = true
                    setupInstantKey(this) { commitDirect(label) }
                }
            }
        }
    }

    private fun showEmojiLayout() {
        hapticKey()
        keepTypedSinglishWithoutConverting()
        keyLayout = KeyLayout.EMOJI
        applyKeyLayout()
    }

    private fun showLettersLayout() {
        hapticKey()
        keepTypedSinglishWithoutConverting()
        keyLayout = KeyLayout.LETTERS
        applyKeyLayout()
    }

    private fun showNumbersLayout() {
        hapticKey()
        keepTypedSinglishWithoutConverting()
        keyLayout = KeyLayout.NUMBERS
        applyKeyLayout()
    }

    private fun showSymbolsLayout() {
        hapticKey()
        keepTypedSinglishWithoutConverting()
        keyLayout = KeyLayout.SYMBOLS
        applyKeyLayout()
    }

    /** Fire on finger down so light taps register immediately (not on finger lift). */
    private fun setupInstantKey(view: View, onPress: () -> Unit) {
        view.setOnClickListener(null)
        view.isClickable = true
        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.isPressed = true
                    onPress()
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.isPressed = false
                    true
                }
                else -> true
            }
        }
    }

    private fun setupRepeatKey(
        view: View,
        onInitial: () -> Unit,
        onRepeat: () -> Unit = onInitial,
        onRelease: () -> Unit = {},
    ) {
        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    onInitial()
                    startRepeat(onRepeat)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    stopRepeat()
                    onRelease()
                    true
                }
                else -> false
            }
        }
    }

    private fun clearKeyTouchListener(view: View) {
        view.setOnTouchListener(null)
        view.isClickable = true
    }

    private fun setupAtPeriodKey(view: View) {
        view.setOnClickListener(null)
        var longPressTriggered = false
        val longPressRunnable = Runnable {
            longPressTriggered = true
            hapticKey()
            commitDirect("@")
        }
        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    longPressTriggered = false
                    v.postDelayed(longPressRunnable, 400L)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    v.removeCallbacks(longPressRunnable)
                    if (!longPressTriggered) {
                        commitDirect(".")
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    v.removeCallbacks(longPressRunnable)
                    true
                }
                else -> false
            }
        }
    }

    private fun setupEmojiCommaKey(view: View) {
        view.setOnClickListener(null)
        var longPressTriggered = false
        val longPressRunnable = Runnable {
            longPressTriggered = true
            hapticKey()
            showEmojiLayout()
        }
        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    longPressTriggered = false
                    v.postDelayed(longPressRunnable, 400L)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    v.removeCallbacks(longPressRunnable)
                    if (!longPressTriggered) {
                        commitDirect(",")
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    v.removeCallbacks(longPressRunnable)
                    true
                }
                else -> false
            }
        }
    }

    private fun startRepeat(action: () -> Unit) {
        stopRepeat()
        var delay = 80L
        repeatRunnable = object : Runnable {
            override fun run() {
                action()
                delay = (delay * 0.78).toLong().coerceAtLeast(36L)
                repeatHandler.postDelayed(this, delay)
            }
        }
        repeatHandler.postDelayed(repeatRunnable!!, 260L)
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
        beginTypingCompactMode()
        val ch = if (shiftOn) letter.uppercase() else letter
        if (language == Language.ENGLISH) {
            currentInputConnection?.commitText(ch, 1)
            if (shiftOn && !shiftLocked) releaseOneShotShift()
            scheduleEnglishSuggestions()
        } else {
            sinhalaBuffer.append(ch)
            if (shiftOn && !shiftLocked) releaseOneShotShift()
            updateComposingText()
            scheduleSinhalaSuggestions()
        }
    }

    private fun scheduleSinhalaSuggestions() {
        keyboardView?.removeCallbacks(sinhalaSuggestRunnable)
        keyboardView?.postDelayed(sinhalaSuggestRunnable, SUGGEST_DEBOUNCE_MS)
    }

    private fun scheduleEnglishSuggestions() {
        keyboardView?.removeCallbacks(englishSuggestRunnable)
        keyboardView?.postDelayed(englishSuggestRunnable, SUGGEST_DEBOUNCE_MS)
    }

    private fun onSpace() {
        hapticKey()
        beginTypingCompactMode()
        val ic = currentInputConnection ?: return
        if (language == Language.SINHALA) {
            adoptCurrentRomanWordIntoBuffer()
            if (sinhalaBuffer.isNotEmpty()) {
                commitSinhalaWord()
            }
        } else if (language == Language.ENGLISH) {
            val word = getCurrentWord(ic)
            if (word.isNotEmpty()) rememberWordCommitted(word)
        }
        ic.commitText(" ", 1)
        updateNextWordSuggestions()
    }

    private fun onEnter() {
        hapticKey()
        val ic = currentInputConnection ?: return
        if (language == Language.SINHALA) {
            adoptCurrentRomanWordIntoBuffer()
            if (sinhalaBuffer.isNotEmpty()) {
                commitSinhalaWord()
            }
        }
        flushEmojiUsageToRecent()
        performEnterAction(ic)
        clearShiftLock()
        clearSuggestions(expandToolbar = true)
    }

    /** Move quick-bar order only after send, so repeated taps stay in place. */
    private fun flushEmojiUsageToRecent() {
        if (emojisSinceSend.isEmpty()) return
        emojisSinceSend.reversed().forEach { Prefs.addRecentEmoji(this, it) }
        emojisSinceSend.clear()
        updateRecentEmojiRow()
    }

    private fun performEnterAction(ic: InputConnection) {
        if (enterIsSearch) {
            ic.performEditorAction(EditorInfo.IME_ACTION_SEARCH)
            return
        }
        // New line keeps keyboard open; SEND/DONE actions often dismiss the IME.
        ic.commitText("\n", 1)
    }

    /** Shrink toolbar while typing; arrow temporarily expands until the next key press. */
    private fun beginTypingCompactMode() {
        if (keyLayout == KeyLayout.EMOJI) return
        toolbarCompact = true
        toolbarExpanded = false
        updateTopBarMode()
    }

    private fun updateEnterKey(info: EditorInfo?) {
        currentEditorInfo = info
        val action = info?.imeOptions?.and(EditorInfo.IME_MASK_ACTION)
            ?: EditorInfo.IME_ACTION_UNSPECIFIED
        val variation = info?.inputType?.and(InputType.TYPE_MASK_VARIATION) ?: 0
        enterIsSearch = action == EditorInfo.IME_ACTION_SEARCH ||
            variation == 0x00000010 || // TYPE_TEXT_VARIATION_WEB_SEARCH
            variation == InputType.TYPE_TEXT_VARIATION_FILTER
        keyEnter?.setImageResource(
            if (enterIsSearch) R.drawable.ic_search else R.drawable.ic_send_arrow,
        )
        keyEnter?.contentDescription = getString(
            if (enterIsSearch) R.string.key_search else R.string.key_send,
        )
    }

    private fun onBackspaceTap() {
        hapticKey()
        deleteOneCharacter()
        if (!backspaceRepeating) {
            refreshAfterBackspace()
        }
    }

    private fun deleteOneCharacter() {
        if (deleteSelectionIfAny()) return
        if (language == Language.SINHALA && sinhalaBuffer.isNotEmpty()) {
            sinhalaBuffer.deleteCharAt(sinhalaBuffer.length - 1)
            updateComposingText()
            return
        }
        deleteTextBeforeCursor()
    }

    /** Delete highlighted text so backspace works on a selection, not only one character. */
    private fun deleteSelectionIfAny(): Boolean {
        val ic = currentInputConnection ?: return false
        val selected = ic.getSelectedText(0)
        val hasSelectedText = !selected.isNullOrEmpty()
        val extracted = if (!hasSelectedText) ic.getExtractedText(ExtractedTextRequest(), 0) else null
        val hasExtractedSelection = extracted != null && extracted.selectionStart != extracted.selectionEnd
        if (!hasSelectedText && !hasExtractedSelection) return false

        sinhalaBuffer.clear()
        ic.commitText("", 1)
        clearComposingText()
        return true
    }

    /** Delete the full grapheme before the cursor (emoji, Sinhala, combining marks). */
    private fun deleteTextBeforeCursor(graphemeCount: Int = 1) {
        val ic = currentInputConnection ?: return
        val lookBehind = (graphemeCount * 8).coerceAtLeast(8)
        val before = ic.getTextBeforeCursor(lookBehind, 0) ?: return
        if (before.isEmpty()) return

        val text = before.toString()
        val breakIterator = BreakIterator.getCharacterInstance()
        breakIterator.setText(text)
        var end = text.length
        var graphemes = 0
        while (graphemes < graphemeCount && end > 0) {
            val start = breakIterator.preceding(end)
            if (start == BreakIterator.DONE) break
            end = start
            graphemes++
        }
        val deleteChars = text.length - end
        if (deleteChars > 0) {
            ic.deleteSurroundingText(deleteChars, 0)
        }
    }

    private fun refreshAfterBackspace() {
        if (language == Language.SINHALA) {
            if (sinhalaBuffer.isNotEmpty()) {
                scheduleSinhalaSuggestions()
            } else {
                val ic = currentInputConnection ?: return
                if (getCurrentWord(ic).isEmpty()) updateNextWordSuggestions()
            }
            return
        }
        val ic = currentInputConnection ?: return
        if (getCurrentWord(ic).isEmpty()) updateNextWordSuggestions()
        else scheduleEnglishSuggestions()
    }

    private fun commitSinhalaWord(sinhala: String? = null, trailingSpace: Boolean = false) {
        val roman = sinhalaBuffer.toString()
        val word = sinhala ?: singlishEngine.transliterate(roman)
        if (word.isEmpty()) return
        val out = if (trailingSpace) "$word " else word
        currentInputConnection?.commitText(out, 1)
        rememberWordCommitted(word, roman)
        sinhalaBuffer.clear()
        clearComposingText()
        if (trailingSpace) updateNextWordSuggestions()
    }

    /** Leave uncommitted Singlish as typed Roman. Convert only on space or a chip tap. */
    private fun keepTypedSinglishWithoutConverting() {
        if (sinhalaBuffer.isEmpty()) return
        val typed = sinhalaBuffer.toString()
        val ic = currentInputConnection
        if (ic != null) {
            ic.beginBatchEdit()
            ic.setComposingText(typed, typed.length)
            ic.finishComposingText()
            ic.endBatchEdit()
        }
        sinhalaBuffer.clear()
    }

    /**
     * If the current token started in English (e.g. "koho") and Sinhala mode
     * continued it ("mada"), treat the whole token as Singlish before convert.
     */
    private fun adoptCurrentRomanWordIntoBuffer() {
        val ic = currentInputConnection ?: return
        val before = ic.getTextBeforeCursor(200, 0)?.toString().orEmpty()
        val fieldWord = before.takeLastWhile { !it.isWhitespace() && it != '\n' }
        if (!isRomanTypingToken(fieldWord)) return

        val buffered = sinhalaBuffer.toString()
        if (fieldWord.equals(buffered, ignoreCase = true)) return

        ic.beginBatchEdit()
        ic.deleteSurroundingText(fieldWord.length, 0)
        sinhalaBuffer.clear()
        sinhalaBuffer.append(fieldWord)
        ic.setComposingText(fieldWord, fieldWord.length)
        ic.endBatchEdit()
    }

    private fun isRomanTypingToken(word: String): Boolean {
        if (word.isBlank()) return false
        if (word.any { it.code in 0x0D80..0x0DFF }) return false
        return word.any { it.isLetter() }
    }

    private fun rememberWordCommitted(word: String, roman: String? = null) {
        val cleaned = word.trim()
        if (cleaned.isEmpty()) return
        val sinhala = language == Language.SINHALA
        if (sinhala) {
            personalHistory.increment(cleaned, PersonalHistoryDatabase.MODE_SINHALA)
            val key = roman?.trim().orEmpty()
            if (key.isNotEmpty()) {
                typingMemory.rememberSinhala(key, cleaned)
            }
        } else {
            personalHistory.increment(cleaned, PersonalHistoryDatabase.MODE_ENGLISH)
            typingMemory.rememberEnglish(cleaned)
        }
        lastCommittedWord?.let { prev ->
            typingMemory.rememberBigram(prev, cleaned, sinhala)
        }
        lastCommittedWord = cleaned
    }

    private fun rememberVoiceWords(text: String) {
        val tokens = text.split(Regex("\\s+")).filter { it.isNotBlank() }
        for (token in tokens) {
            rememberWordCommitted(token)
        }
    }

    private fun commitDirect(text: String) {
        hapticKey()
        beginTypingCompactMode()
        keepTypedSinglishWithoutConverting()
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
        if (language != Language.SINHALA) return
        if (sinhalaBuffer.isEmpty()) {
            sinhalaLocalSuggestJob?.cancel()
            sinhalaCloudJob?.cancel()
            clearSuggestions(expandToolbar = true)
            return
        }
        val typed = sinhalaBuffer.toString()
        sinhalaLocalSuggestJob?.cancel()
        sinhalaLocalSuggestJob = scope.launch {
            val engineItems = withContext(Dispatchers.Default) {
                singlishEngine.sinhalaSuggestions(typed)
            }

            if (sinhalaBuffer.toString() != typed) return@launch

            /*
             * If the user previously selected a Sinhala word for this exact
             * Singlish spelling (including a cloud/AI suggestion), force that
             * learned mapping to the front of the LOCAL list.
             *
             * Example:
             *   sankayaawa -> සංක්‍යාව
             *
             * After the user chooses it once, the next time it should appear
             * locally without waiting for Gemini.
             */
            val learnedExact = typingMemory.exactSinhalaMapping(typed)
            val sinhalaItems = if (learnedExact == null) {
                engineItems
            } else {
                buildList {
                    add(learnedExact)
                    addAll(
                        engineItems.filter {
                            it.commitText != learnedExact.commitText
                        }
                    )
                }.take(7)
            }
            val items = withTypedSinglishChip(typed, sinhalaItems)

            renderSuggestions(items) { pickSinhalaSuggestion(it) }
            fetchSinhalaCloudWordCompletions(typed, items)
        }
    }

    private fun fetchSinhalaCloudWordCompletions(
        partialSinglish: String,
        localItems: List<SuggestionCandidate>,
    ) {
        if (language != Language.SINHALA || !Prefs.isCloudSuggestionsEnabled(this)) return
        val apiKey = Prefs.getApiKey(this)
        if (apiKey.isBlank() || partialSinglish.length < 2) return

        sinhalaCloudJob?.cancel()
        sinhalaCloudJob = scope.launch {
            delay(SINHALA_AI_PAUSE_MS)
            if (sinhalaBuffer.toString() != partialSinglish) return@launch
            val ic = currentInputConnection ?: return@launch
            val context = buildSinhalaContextForCloud(ic, partialSinglish)
            val cloudResult = cloudSuggestionService.predictSinhalaWordCompletions(
                contextText = context,
                partialSinglish = partialSinglish,
                apiKey = apiKey,
            )
            if (sinhalaBuffer.toString() != partialSinglish) return@launch
            cloudResult.onFailure { e ->
                toastOpenRouterFailure(e)
                return@launch
            }
            val cloudWords = cloudResult.getOrNull().orEmpty()
                .filter { containsSinhalaScript(it) }
            if (cloudWords.isEmpty()) return@launch
            if (sinhalaBuffer.toString() != partialSinglish) return@launch
            val merged = mergeCloudSuggestions(
                local = localItems,
                cloudItems = cloudWords,
                prefixHint = null,
                isNextWord = false,
                sinhalaScript = true,
            )
            renderSuggestions(merged) { pickSinhalaSuggestion(it) }
        }
    }

    /** Field text + current Singlish buffer for AI context. */
    private fun buildSinhalaContextForCloud(ic: InputConnection, partialSinglish: String): String {
        val before = ic.getTextBeforeCursor(500, 0)?.toString().orEmpty()
        return if (before.isBlank()) partialSinglish else "$before $partialSinglish"
    }

    private fun pickSinhalaSuggestion(candidate: SuggestionCandidate) {
        if (candidate.isSinglishRoman) {
            commitLiteralSinglish(candidate.commitText)
            clearSuggestions()
            return
        }
        adoptCurrentRomanWordIntoBuffer()
        commitSinhalaWord(candidate.commitText, trailingSpace = true)
        clearSuggestions()
    }

    private fun commitLiteralSinglish(typed: String) {
        val ic = currentInputConnection ?: return
        val text = typed.ifBlank { sinhalaBuffer.toString() }
        if (text.isEmpty()) return
        ic.beginBatchEdit()
        ic.setComposingText(text, text.length)
        ic.finishComposingText()
        ic.commitText(" ", 1)
        ic.endBatchEdit()
        sinhalaBuffer.clear()
    }

    private fun withTypedSinglishChip(
        typed: String,
        items: List<SuggestionCandidate>,
    ): List<SuggestionCandidate> {
        if (typed.isBlank()) return items
        val exact = SuggestionCandidate(
            display = typed,
            commitText = typed,
            isRoman = true,
            isSinglishRoman = true,
        )
        return buildList {
            val sinhalaItems = items.filter { !it.commitText.equals(typed, ignoreCase = true) }
            val firstSinhala = sinhalaItems.firstOrNull()
            if (firstSinhala != null) add(firstSinhala)
            add(exact)
            addAll(sinhalaItems.drop(if (firstSinhala == null) 0 else 1))
        }.take(8)
    }

    private fun updateEnglishSuggestions() {
        if (language != Language.ENGLISH) return
        val ic = currentInputConnection ?: return
        val word = getCurrentWord(ic)
        if (word.isEmpty()) {
            englishLocalSuggestJob?.cancel()
            englishCloudJob?.cancel()
            updateNextWordSuggestions()
            return
        }
        val wordSnapshot = word
        englishLocalSuggestJob?.cancel()
        englishLocalSuggestJob = scope.launch {
            englishSuggestions.suggest(wordSnapshot) { englishItems ->
                scope.launch englishSuggestCallback@{
                    if (language != Language.ENGLISH) return@englishSuggestCallback
                    val callbackIc = currentInputConnection ?: return@englishSuggestCallback
                    if (getCurrentWord(callbackIc) != wordSnapshot) return@englishSuggestCallback
                    renderEnglishSuggestions(wordSnapshot, englishItems)
                    fetchEnglishCloudWordCompletions(wordSnapshot, englishItems)
                }
            }
        }
    }

    private fun renderEnglishSuggestions(partialWord: String, items: List<SuggestionCandidate>) {
        renderSuggestions(items) { pickEnglishSuggestion(it, partialWord) }
    }

    private fun pickEnglishSuggestion(candidate: SuggestionCandidate, partialWord: String) {
        val ic = currentInputConnection ?: return
        replaceCurrentWord(ic, partialWord, candidate.commitText, trailingSpace = true)
        clearSuggestions()
    }

    private fun fetchEnglishCloudWordCompletions(
        partialWord: String,
        localItems: List<SuggestionCandidate>,
    ) {
        if (language != Language.ENGLISH || !Prefs.isCloudSuggestionsEnabled(this)) return

        val apiKey = Prefs.getApiKey(this)
        if (apiKey.isBlank() || partialWord.length < 2) return

        /*
         * Every new keystroke reaches this method again. Cancel the previous
         * pending cloud job BEFORE creating another one. This is the main cost
         * protection: while the user is continuously typing, the 700 ms timer
         * keeps restarting and no HTTP request is sent.
         */
        englishCloudJob?.cancel()
        englishCloudJob = scope.launch {
            delay(ENGLISH_AI_PAUSE_MS)

            if (language != Language.ENGLISH) return@launch

            val ic = currentInputConnection ?: return@launch
            if (getCurrentWord(ic) != partialWord) return@launch

            val contextSnapshot = getContextBeforeCursor(ic)
            val sentenceMode = shouldUseEnglishSentenceCompletion(
                contextText = contextSnapshot,
                partialWord = partialWord,
            )

            val cloudResult = coroutineScope {
                val wordDeferred = async {
                    cloudSuggestionService.predictWordCompletions(
                        contextText = contextSnapshot,
                        partialWord = partialWord,
                        apiKey = apiKey,
                        tone = englishTone,
                    )
                }
                val phraseDeferred = if (sentenceMode) {
                    async {
                        cloudSuggestionService.predictEnglishSentenceCompletions(
                            contextText = contextSnapshot,
                            partialWord = partialWord,
                            apiKey = apiKey,
                            tone = englishTone,
                        )
                    }
                } else {
                    null
                }

                val wordResult = wordDeferred.await()
                val phraseResult = phraseDeferred?.await()
                wordResult to phraseResult
            }

            val liveIc = currentInputConnection ?: return@launch
            if (getCurrentWord(liveIc) != partialWord) return@launch

            val (wordCloudResult, phraseCloudResult) = cloudResult
            wordCloudResult.onFailure { e ->
                toastOpenRouterFailure(e)
            }
            phraseCloudResult?.onFailure { e ->
                toastOpenRouterFailure(e)
            }

            val wordCloudWords = wordCloudResult
                .getOrNull()
                .orEmpty()
                .filter { EnglishSuggestionRanker.isEnglishOnly(it) }

            val phraseCloudWords = phraseCloudResult
                ?.getOrNull()
                .orEmpty()
                .filter { EnglishSuggestionRanker.isEnglishCloudSuggestion(it) }

            if (wordCloudWords.isEmpty() && phraseCloudWords.isEmpty()) return@launch

            val merged = if (sentenceMode) {
                mergeEnglishPausedSuggestions(
                    local = localItems,
                    wordCloudItems = wordCloudWords,
                    phraseCloudItems = phraseCloudWords,
                    partialWord = partialWord,
                )
            } else {
                mergeCloudSuggestions(
                    local = localItems,
                    cloudItems = wordCloudWords,
                    prefixHint = partialWord,
                    isNextWord = false,
                )
            }

            renderSuggestions(merged) { candidate ->
                replaceCurrentWord(
                    currentInputConnection ?: return@renderSuggestions,
                    partialWord,
                    candidate.commitText,
                    trailingSpace = true,
                )
                clearSuggestions()
            }
        }
    }

    /**
     * Use sentence/phrase AI only when there is useful context before the
     * current unfinished word. Short inputs such as "hel" still use normal
     * AI word completion after the same pause.
     */
    private fun shouldUseEnglishSentenceCompletion(
        contextText: String,
        partialWord: String,
    ): Boolean {
        if (contextText.isBlank() || partialWord.isBlank()) return false

        val beforeCurrentWord = if (contextText.endsWith(partialWord)) {
            contextText.dropLast(partialWord.length).trimEnd()
        } else {
            contextText.substringBeforeLast(partialWord, "").trimEnd()
        }

        if (beforeCurrentWord.length < 3) return false

        val completedWords = beforeCurrentWord
            .split(Regex("\\s+"))
            .count { it.isNotBlank() }

        return completedWords >= 1
    }

    /**
     * After a typing pause, show local words, AI single-word completions,
     * and longer phrase continuations together.
     */
    private fun mergeEnglishPausedSuggestions(
        local: List<SuggestionCandidate>,
        wordCloudItems: List<String>,
        phraseCloudItems: List<String>,
        partialWord: String,
    ): List<SuggestionCandidate> {
        val result = mutableListOf<SuggestionCandidate>()
        val seen = linkedSetOf<String>()

        for (candidate in local.take(4)) {
            val key = candidate.commitText.trim().lowercase()
            if (key.isBlank() || !seen.add(key)) continue
            result.add(candidate)
        }

        for (raw in wordCloudItems) {
            val text = raw.trim()
            if (text.isBlank() || text.contains(' ')) continue
            if (!EnglishSuggestionRanker.isEnglishOnly(text)) continue

            val commit = formatCloudSuggestion(text, partialWord)
            val key = commit.lowercase()
            if (!seen.add(key)) continue

            result.add(
                SuggestionCandidate(
                    display = truncateSuggestionDisplay(commit),
                    commitText = commit,
                    isNextWord = false,
                    isCloud = true,
                )
            )

            if (result.size >= 6) break
        }

        for (raw in phraseCloudItems) {
            val text = raw.trim()
            if (text.isBlank()) continue
            if (!EnglishSuggestionRanker.isEnglishCloudSuggestion(text)) continue

            val commit = formatCloudSuggestion(text, partialWord)
            val key = commit.lowercase()
            if (!seen.add(key)) continue

            result.add(
                SuggestionCandidate(
                    display = truncateSuggestionDisplay(commit),
                    commitText = commit,
                    isNextWord = false,
                    isCloud = true,
                )
            )

            if (result.size >= 8) break
        }

        return result.take(8)
    }

    private fun updateNextWordSuggestions() {
        val ic = currentInputConnection ?: return
        if (sinhalaBuffer.isNotEmpty()) return
        val lastWord = getLastWord(ic)
        if (lastWord.isEmpty()) {
            clearSuggestions(expandToolbar = true)
            return
        }
        val sinhala = language == Language.SINHALA
        val tone = if (sinhala) EnglishTone.PROFESSIONAL else englishTone
        val local = nextWordPredictor.predict(lastWord, sinhala, tone)
        if (local.isEmpty() && !Prefs.isCloudSuggestionsEnabled(this)) {
            renderSuggestions(emptyList()) { candidate ->
                commitNextWord(candidate)
            }
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
            // English cloud next-word prediction also waits for a real typing pause.
            delay(if (sinhala) SINHALA_AI_PAUSE_MS else ENGLISH_AI_PAUSE_MS)
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
            if (getLastWord(currentInputConnection ?: return@launch) != lastWord) return@launch
            cloudResult.onFailure { e ->
                toastOpenRouterFailure(e)
                return@launch
            }
            val cloudWords = cloudResult.getOrNull().orEmpty()
                .let {
                    if (sinhala) {
                        it.filter { w -> containsSinhalaScript(w) }
                    } else {
                        it.filter { w -> EnglishSuggestionRanker.isEnglishCloudSuggestion(w) }
                    }
                }
            if (cloudWords.isEmpty()) return@launch
            if (getLastWord(currentInputConnection ?: return@launch) != lastWord) return@launch
            val merged = mergeCloudSuggestions(
                local = local,
                cloudItems = cloudWords,
                prefixHint = null,
                isNextWord = true,
                sinhalaScript = sinhala,
            )
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
        sinhalaScript: Boolean = false,
    ): List<SuggestionCandidate> {
        /*
         * Sinhala behavior:
         * Preserve the local rank EXACTLY and append unique cloud results.
         * Cloud must never reshuffle the carefully ranked local Sinhala list.
         */
        if (sinhalaScript) {
            val result = mutableListOf<SuggestionCandidate>()
            val seen = linkedSetOf<String>()

            for (candidate in local) {
                val word = candidate.commitText.trim()
                if (word.isBlank()) continue
                if (!candidate.isSinglishRoman && !containsSinhalaScript(word)) continue
                if (!seen.add(word)) continue

                result.add(candidate)
                if (result.size >= 8) return result
            }

            for (raw in cloudItems) {
                val text = raw.trim()
                if (text.isBlank()) continue
                if (!containsSinhalaScript(text)) continue

                val commit = formatCloudSuggestion(text, prefixHint)
                if (!seen.add(commit)) continue

                result.add(
                    SuggestionCandidate(
                        display = truncateSuggestionDisplay(commit),
                        commitText = commit,
                        isNextWord = isNextWord,
                        isCloud = true,
                    ),
                )

                if (result.size >= 8) break
            }

            return result
        }

        /*
         * English behavior stays the same: merge cloud items and then let the
         * English ranker score the complete list.
         */
        val seen = local.map { it.commitText.lowercase() }.toMutableSet()
        val merged = local.toMutableList()

        for (raw in cloudItems) {
            val text = raw.trim()
            if (text.isBlank()) continue
            if (!EnglishSuggestionRanker.isEnglishCloudSuggestion(text)) continue

            val commit = formatCloudSuggestion(text, prefixHint)
            val key = commit.lowercase()
            if (!seen.add(key)) continue

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

        val personalCounts = personalHistory.getCounts(
            merged.map { it.commitText },
            PersonalHistoryDatabase.MODE_ENGLISH,
        )

        return EnglishSuggestionRanker.rank(
            prefix = prefixHint.orEmpty(),
            candidates = merged,
            personalCounts = personalCounts,
            limit = 8,
        )
    }

    private fun containsSinhalaScript(text: String): Boolean =
        text.any { it.code in 0x0D80..0x0DFF }

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
        val filtered = filterSuggestionsForActiveLanguage(items)
        if (filtered.isNotEmpty()) {
            toolbarCompact = true
        }
        val row = suggestionRow ?: return
        for (index in filtered.indices) {
            val chip = if (index < row.childCount) {
                row.getChildAt(index) as TextView
            } else {
                createSuggestionChip().also { row.addView(it) }
            }
            bindSuggestionChip(chip, filtered[index], onPick)
            chip.visibility = View.VISIBLE
        }
        for (index in filtered.size until row.childCount) {
            row.getChildAt(index).visibility = View.GONE
        }
        updateTopBarMode()
    }

    /** Last-line guard: Sinhala mode shows Sinhala script only; English mode shows Latin English only. */
    private fun filterSuggestionsForActiveLanguage(
        items: List<SuggestionCandidate>,
    ): List<SuggestionCandidate> {
        return items.filter { candidate ->
            val text = candidate.commitText.trim()
            when (language) {
                Language.SINHALA ->
                    containsSinhalaScript(text) || candidate.isSinglishRoman
                Language.ENGLISH -> EnglishSuggestionRanker.isEnglishCloudSuggestion(text)
            }
        }
    }

    private fun createSuggestionChip(): TextView =
        TextView(this).apply {
            textSize = 15f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                letterSpacing = 0.05f
            }
            setPadding(20, 8, 20, 8)
            setBackgroundResource(suggestionChipBg)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { marginEnd = 6 }
        }

    private fun bindSuggestionChip(
        chip: TextView,
        candidate: SuggestionCandidate,
        onPick: (SuggestionCandidate) -> Unit,
    ) {
        chip.text = candidate.display
        chip.setBackgroundResource(suggestionChipBg)
        chip.setTextColor(
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
        chip.setOnClickListener {
            hapticKey()
            onPick(candidate)
        }
    }

    private fun updateTopBarMode() {
        val showSuggestionBar = toolbarCompact && !toolbarExpanded
        btnToolbarExpand?.visibility = if (showSuggestionBar) View.VISIBLE else View.GONE
        toolbarRow?.visibility = if (showSuggestionBar) View.GONE else View.VISIBLE
        suggestionScroll?.visibility = if (showSuggestionBar) View.VISIBLE else View.GONE
    }

    private fun updateRecentEmojiBarVisibility() {
        recentEmojiScroll?.visibility = if (keyLayout == KeyLayout.EMOJI) View.GONE else View.VISIBLE
    }

    private fun updateRecentEmojiRow() {
        val row = recentEmojiRow ?: return
        row.removeAllViews()
        val emojis = buildQuickEmojiList()
        val pad = (4 * resources.displayMetrics.density).toInt()
        emojis.forEach { emoji ->
            val cell = TextView(this).apply {
                text = emoji
                textSize = 22f
                gravity = android.view.Gravity.CENTER
                setPadding(pad, 0, pad, 0)
                setOnClickListener {
                    insertEmoji(emoji)
                }
            }
            row.addView(cell)
        }
    }

    private fun buildQuickEmojiList(): List<String> {
        val recent = Prefs.getRecentEmojis(this)
        val merged = recent.toMutableList()
        for (fallback in EmojiData.quickPickDefaults) {
            if (merged.size >= 14) break
            if (!merged.contains(fallback)) merged.add(fallback)
        }
        return merged.take(14)
    }

    private fun clearSuggestions(expandToolbar: Boolean = false) {
        nextWordJob?.cancel()
        englishCloudJob?.cancel()
        sinhalaCloudJob?.cancel()
        sinhalaLocalSuggestJob?.cancel()
        englishLocalSuggestJob?.cancel()
        suggestionRow?.let { row ->
            for (index in 0 until row.childCount) {
                row.getChildAt(index).visibility = View.GONE
            }
        }
        if (expandToolbar) {
            toolbarCompact = false
            toolbarExpanded = false
        }
        updateTopBarMode()
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
        emojisSinceSend.add(emoji)
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

    private fun replaceCurrentWord(
        ic: InputConnection,
        oldWord: String,
        newWord: String,
        trailingSpace: Boolean = false,
    ) {
        val out = if (trailingSpace) "$newWord " else newWord
        if (oldWord.isEmpty()) {
            ic.commitText(out, 1)
            rememberWordCommitted(newWord)
            if (trailingSpace) updateNextWordSuggestions()
            return
        }
        val before = ic.getTextBeforeCursor(oldWord.length + 5, 0)?.toString().orEmpty()
        val toDelete = before.takeLastWhile { !it.isWhitespace() && it != '\n' }
        ic.deleteSurroundingText(toDelete.length, 0)
        ic.commitText(out, 1)
        rememberWordCommitted(newWord)
        if (trailingSpace) updateNextWordSuggestions() else updateEnglishSuggestions()
    }

    private fun setupShiftKey(view: View) {
        view.setOnClickListener(null)
        var lockTriggered = false
        val lockRunnable = Runnable {
            lockTriggered = true
            enableShiftLock()
        }
        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    lockTriggered = false
                    v.postDelayed(lockRunnable, 2000L)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    v.removeCallbacks(lockRunnable)
                    if (!lockTriggered) {
                        onShiftTap()
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    v.removeCallbacks(lockRunnable)
                    true
                }
                else -> false
            }
        }
        updateShiftKeyAppearance()
    }

    private fun enableShiftLock() {
        if (keyLayout != KeyLayout.LETTERS) return
        hapticKey()
        shiftLocked = true
        shiftOn = true
        refreshKeyLabels()
    }

    private fun onShiftTap() {
        if (keyLayout != KeyLayout.LETTERS) return
        hapticKey()
        if (shiftLocked) {
            clearShiftLock()
        } else {
            shiftOn = !shiftOn
            refreshKeyLabels()
        }
    }

    private fun releaseOneShotShift() {
        shiftOn = false
        refreshKeyLabels()
    }

    private fun clearShiftLock() {
        shiftLocked = false
        shiftOn = false
        if (keyLayout == KeyLayout.LETTERS) {
            refreshKeyLabels()
        }
    }

    private fun refreshKeyLabels() {
        val view = keyboardView ?: return
        if (keyLayout != KeyLayout.LETTERS) return
        letterKeyIds.forEachIndexed { index, id ->
            val letter = lettersLower[index]
            view.findViewById<TextView>(id).text =
                if (shiftOn) letter.uppercase() else letter
        }
        updateShiftKeyAppearance()
    }

    private fun updateShiftKeyAppearance() {
        val view = keyboardView ?: return
        if (keyLayout != KeyLayout.LETTERS) return
        view.findViewById<TextView>(R.id.keyShift).apply {
            text = if (shiftLocked) "⇪" else "⇧"
            setTextColor(
                when {
                    shiftLocked -> if (activeTheme == KeyboardTheme.BLACK) {
                        0xFF81C784.toInt()
                    } else {
                        0xFF1565C0.toInt()
                    }
                    shiftOn -> if (activeTheme == KeyboardTheme.BLACK) {
                        0xFF90CAF9.toInt()
                    } else {
                        0xFF1565C0.toInt()
                    }
                    else -> keyTextColor
                },
            )
        }
    }

    private fun toggleLanguage() {
        if (language == Language.SINHALA) {
            keepTypedSinglishWithoutConverting()
            language = Language.ENGLISH
            clearSuggestions(expandToolbar = true)
            updateLanguageUi()
            scheduleEnglishSuggestions()
            return
        }

        language = Language.SINHALA
        adoptCurrentRomanWordIntoBuffer()
        clearSuggestions(expandToolbar = true)
        updateLanguageUi()
        if (sinhalaBuffer.isNotEmpty()) {
            updateComposingText()
            scheduleSinhalaSuggestions()
        }
    }

    private fun updateLanguageUi() {
        val sinhala = language == Language.SINHALA
        val label = if (sinhala) {
            getString(R.string.mode_sinhala_key)
        } else {
            getString(R.string.mode_english)
        }
        val inactiveKeyBg = when (activeTheme) {
            KeyboardTheme.WHITE -> R.drawable.key_bg_light
            KeyboardTheme.BLACK -> R.drawable.key_bg_dark
        }
        val activeKeyBg = when (activeTheme) {
            KeyboardTheme.WHITE -> R.drawable.key_bg_lang_active
            KeyboardTheme.BLACK -> R.drawable.key_bg_lang_active_dark
        }
        keyLangBottom?.apply {
            text = label
            setBackgroundResource(if (sinhala) activeKeyBg else inactiveKeyBg)
            setTextColor(if (sinhala) 0xFFFFFFFF.toInt() else keyTextColor)
            setTypeface(null, if (sinhala) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
            alpha = 1f
        }
        btnFix?.visibility = if (language == Language.ENGLISH) View.VISIBLE else View.GONE
        btnPrompt?.visibility = if (language == Language.ENGLISH) View.VISIBLE else View.GONE
        btnToEnglish?.visibility = if (language == Language.ENGLISH) View.VISIBLE else View.GONE
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
            }.onFailure { e ->
                toastOpenRouterFailure(e)
            }
        }
    }

    private fun enhancePrompt() {
        if (language != Language.ENGLISH) return
        val ic = currentInputConnection ?: return
        val apiKey = Prefs.getApiKey(this)
        if (apiKey.isBlank()) {
            Toast.makeText(this, R.string.api_key_missing, Toast.LENGTH_LONG).show()
            return
        }

        promptJob?.cancel()
        promptJob = scope.launch {
            progress?.visibility = View.VISIBLE
            btnPrompt?.isEnabled = false

            val text = withContext(Dispatchers.Main) { getFieldText(ic) }
            if (text.isBlank()) {
                progress?.visibility = View.GONE
                btnPrompt?.isEnabled = true
                return@launch
            }

            Toast.makeText(this@KeyboardService, R.string.enhancing_prompt, Toast.LENGTH_SHORT).show()

            val result = promptEnhancer.enhancePrompt(text, apiKey)
            progress?.visibility = View.GONE
            btnPrompt?.isEnabled = true

            result.onSuccess { enhanced ->
                replaceFieldText(ic, enhanced)
            }.onFailure { e ->
                toastOpenRouterFailure(e)
            }
        }
    }

    private fun translateSinglishToEnglish() {
        val ic = currentInputConnection ?: return
        val apiKey = Prefs.getApiKey(this)
        if (apiKey.isBlank()) {
            Toast.makeText(this, R.string.api_key_missing, Toast.LENGTH_LONG).show()
            return
        }

        translateJob?.cancel()
        translateJob = scope.launch {
            progress?.visibility = View.VISIBLE
            btnToEnglish?.isEnabled = false

            val text = withContext(Dispatchers.Main) {
                getFieldText(ic).trim()
            }
            if (text.isBlank()) {
                progress?.visibility = View.GONE
                btnToEnglish?.isEnabled = true
                return@launch
            }

            Toast.makeText(this@KeyboardService, R.string.translating_singlish, Toast.LENGTH_SHORT).show()

            val result = singlishTranslator.translateToEnglish(text, apiKey)
            progress?.visibility = View.GONE
            btnToEnglish?.isEnabled = true

            result.onSuccess { translated ->
                replaceFieldText(ic, translated)
                clearSuggestions()
            }.onFailure { e ->
                toastOpenRouterFailure(e)
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
        updateEnterKey(attribute)
        sinhalaBuffer.clear()
        clearComposingText()
        clearSuggestions(expandToolbar = true)
        lastCommittedWord = null
        if (!restarting) {
            keyLayout = defaultLayoutFor(attribute)
            emojisSinceSend.clear()
        }
        englishTone = Prefs.getEnglishTone(this)
        voiceInputHelper?.prepare()
        applyKeyLayout()
        applyTheme()
    }

    /** Numeric / phone fields start on 123; otherwise letters. User layout choice is kept on restart. */
    private fun defaultLayoutFor(info: EditorInfo?): KeyLayout {
        val inputClass = info?.inputType?.and(InputType.TYPE_MASK_CLASS) ?: return KeyLayout.LETTERS
        return when (inputClass) {
            InputType.TYPE_CLASS_NUMBER,
            InputType.TYPE_CLASS_PHONE -> KeyLayout.NUMBERS
            else -> KeyLayout.LETTERS
        }
    }

    override fun onFinishInput() {
        sinhalaBuffer.clear()
        clearComposingText()
        clearSuggestions(expandToolbar = true)
        super.onFinishInput()
    }

    private fun toastOpenRouterFailure(cause: Throwable?) {
        val msg = OpenRouterHelper.userMessageForFailure(cause?.message.orEmpty())
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
    }

    override fun onDestroy() {
        stopRepeat()
        voiceInputHelper?.destroy()
        voiceInputHelper = null
        englishSuggestions.close()
        if (::singlishEngine.isInitialized) singlishEngine.close()
        scope.cancel()
        super.onDestroy()
    }
}
