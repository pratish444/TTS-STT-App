package com.example.talkmate.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import java.util.*

class SpeechToTextHelper(
    private val context: Context,
    private val onResult: (String) -> Unit,
    private val onError: (String) -> Unit
) {

    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false

    private val _statusChannel = Channel<STTStatus>(Channel.BUFFERED)
    val statusFlow: Flow<STTStatus> = _statusChannel.receiveAsFlow()

    sealed class STTStatus {
        object Ready : STTStatus()
        object Listening : STTStatus()
        object Processing : STTStatus()
        data class PartialResult(val text: String) : STTStatus()
        data class FinalResult(val text: String) : STTStatus()
        data class Error(val message: String) : STTStatus()
        object Stopped : STTStatus()
    }

    init {
        initializeSpeechRecognizer()
    }

    private fun initializeSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            speechRecognizer?.setRecognitionListener(createRecognitionListener())
            _statusChannel.trySend(STTStatus.Ready)
        } else {
            onError("Speech recognition not available on this device")
            _statusChannel.trySend(STTStatus.Error("Speech recognition not available"))
        }
    }

    private fun createRecognitionListener() = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            _statusChannel.trySend(STTStatus.Listening)
        }

        override fun onBeginningOfSpeech() {
            _statusChannel.trySend(STTStatus.Processing)
        }

        override fun onRmsChanged(rmsdB: Float) {
            // Could be used for volume level indicators
        }

        override fun onBufferReceived(buffer: ByteArray?) {
            // Audio buffer received
        }

        override fun onEndOfSpeech() {
            _statusChannel.trySend(STTStatus.Processing)
        }

        override fun onError(error: Int) {
            isListening = false
            val errorMessage = when (error) {
                SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                SpeechRecognizer.ERROR_CLIENT -> "Client side error"
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
                SpeechRecognizer.ERROR_NETWORK -> "Network error"
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                SpeechRecognizer.ERROR_NO_MATCH -> "No speech input detected"
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognition service busy"
                SpeechRecognizer.ERROR_SERVER -> "Server error"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input"
                else -> "Unknown error occurred"
            }
            onError(errorMessage)
            _statusChannel.trySend(STTStatus.Error(errorMessage))
        }

        override fun onResults(results: Bundle?) {
            isListening = false
            results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.let { matches ->
                if (matches.isNotEmpty()) {
                    val recognizedText = matches[0]
                    onResult(recognizedText)
                    _statusChannel.trySend(STTStatus.FinalResult(recognizedText))
                }
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.let { matches ->
                if (matches.isNotEmpty()) {
                    val partialText = matches[0]
                    _statusChannel.trySend(STTStatus.PartialResult(partialText))
                }
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {
            // Handle additional events if needed
        }
    }

    fun startListening() {
        if (isListening) {
            return
        }

        if (speechRecognizer == null) {
            initializeSpeechRecognizer()
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1500)
        }

        try {
            speechRecognizer?.startListening(intent)
            isListening = true
        } catch (e: Exception) {
            onError("Failed to start speech recognition: ${e.message}")
            _statusChannel.trySend(STTStatus.Error("Failed to start recognition"))
        }
    }

    fun stopListening() {
        if (isListening) {
            speechRecognizer?.stopListening()
            isListening = false
            _statusChannel.trySend(STTStatus.Stopped)
        }
    }

    fun cancelListening() {
        if (isListening) {
            speechRecognizer?.cancel()
            isListening = false
            _statusChannel.trySend(STTStatus.Stopped)
        }
    }

    fun isCurrentlyListening(): Boolean {
        return isListening
    }

    fun destroy() {
        stopListening()
        speechRecognizer?.destroy()
        speechRecognizer = null
        _statusChannel.close()
    }
}