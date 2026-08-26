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

    fun isListening(): Boolean = listening

    fun start(languageTag: String) {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onError("Speech recognition is not available on this device")
            return
        }
        stop()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(this@VoiceInputHelper)
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }
        speechRecognizer?.startListening(intent)
        listening = true
        onListeningChanged(true)
    }

    fun stop() {
        if (!listening) return
        speechRecognizer?.stopListening()
        listening = false
        onListeningChanged(false)
    }

    fun destroy() {
        stop()
        speechRecognizer?.destroy()
        speechRecognizer = null
    }

    override fun onReadyForSpeech(params: Bundle?) = Unit

    override fun onBeginningOfSpeech() = Unit

    override fun onRmsChanged(rmsdB: Float) = Unit

    override fun onBufferReceived(buffer: ByteArray?) = Unit

    override fun onEndOfSpeech() {
        listening = false
        onListeningChanged(false)
    }

    override fun onError(error: Int) {
        listening = false
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
            onError(message)
        }
    }

    override fun onResults(results: Bundle?) {
        listening = false
        onListeningChanged(false)
        val text = results
            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()
            ?.trim()
        if (!text.isNullOrEmpty()) onFinal(text)
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
