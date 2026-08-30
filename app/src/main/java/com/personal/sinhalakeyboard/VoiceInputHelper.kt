package com.personal.sinhalakeyboard

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

class VoiceInputHelper(
    private val context: Context,
    private val onPartial: (String) -> Unit = {},
    private val onFinal: (String) -> Unit,
    private val onError: (String) -> Unit,
    private val onListeningChanged: (Boolean) -> Unit = {},
) : RecognitionListener {

    private var speechRecognizer: SpeechRecognizer? = null
    private var listening = false
    private var continuous = false
    private var languageTag = "en-US"
    private var pendingStart = false

    fun isListening(): Boolean = listening

    fun isContinuous(): Boolean = continuous

    /** Pre-create recognizer so the first mic tap is instant. */
    fun prepare() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) return
        ensureRecognizer()
    }

    fun start(languageTag: String, continuousMode: Boolean = false) {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onError("Speech recognition is not available on this device")
            return
        }
        this.languageTag = languageTag
        continuous = continuousMode
        pendingStart = true
        ensureRecognizer()
        speechRecognizer?.cancel()
        startListeningInternal()
    }

    fun stop() {
        continuous = false
        pendingStart = false
        stopRecognizerOnly()
    }

    private fun ensureRecognizer() {
        if (speechRecognizer != null) return
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(this@VoiceInputHelper)
        }
    }

    private fun stopRecognizerOnly() {
        speechRecognizer?.stopListening()
        speechRecognizer?.cancel()
        if (listening) {
            listening = false
            onListeningChanged(false)
        }
    }

    fun destroy() {
        continuous = false
        pendingStart = false
        stopRecognizerOnly()
        speechRecognizer?.destroy()
        speechRecognizer = null
    }

    private fun startListeningInternal() {
        val recognizer = speechRecognizer ?: return
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
        }
        listening = true
        onListeningChanged(true)
        recognizer.startListening(intent)
    }

    private fun restartIfContinuous() {
        if (!continuous) return
        speechRecognizer?.cancel()
        startListeningInternal()
    }

    override fun onReadyForSpeech(params: Bundle?) {
        if (pendingStart) pendingStart = false
    }

    override fun onBeginningOfSpeech() = Unit

    override fun onRmsChanged(rmsdB: Float) = Unit

    override fun onBufferReceived(buffer: ByteArray?) = Unit

    override fun onEndOfSpeech() {
        if (!continuous) {
            listening = false
            onListeningChanged(false)
        }
    }

    override fun onError(error: Int) {
        if (continuous && (
                error == SpeechRecognizer.ERROR_NO_MATCH ||
                    error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                )
        ) {
            restartIfContinuous()
            return
        }
        listening = false
        pendingStart = false
        onListeningChanged(false)
        val message = when (error) {
            SpeechRecognizer.ERROR_AUDIO -> "Microphone error"
            SpeechRecognizer.ERROR_CLIENT -> "Voice input cancelled"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required"
            SpeechRecognizer.ERROR_NETWORK -> "Network error for voice input"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Voice input timed out"
            SpeechRecognizer.ERROR_NO_MATCH -> "Could not understand speech"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Voice recognizer busy"
            SpeechRecognizer.ERROR_SERVER -> "Voice server error"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected"
            else -> "Voice input failed"
        }
        if (error != SpeechRecognizer.ERROR_CLIENT) {
            if (continuous) {
                continuous = false
            }
            onError(message)
        }
    }

    override fun onResults(results: Bundle?) {
        val text = results
            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()
            ?.trim()
        if (!text.isNullOrEmpty()) onFinal(text)
        if (continuous) {
            restartIfContinuous()
        } else {
            listening = false
            onListeningChanged(false)
        }
    }

    override fun onPartialResults(partialResults: Bundle?) {
        val text = partialResults
            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()
            ?.trim()
        if (!text.isNullOrEmpty()) onPartial(text)
    }

    override fun onEvent(eventType: Int, params: Bundle?) = Unit
}
