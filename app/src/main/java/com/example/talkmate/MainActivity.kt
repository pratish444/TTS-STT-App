package com.example.talkmate

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.talkmate.ui.components.SpeechAssistantScreen
import com.example.talkmate.ui.theme.TTSSTTAppTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.*

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        // Permission result is handled in the viewModel
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            TTSSTTAppTheme {
                val viewModel: MainViewModel = viewModel {
                    MainViewModel(this@MainActivity)
                }

                LaunchedEffect(Unit) {
                    viewModel.initializeServices(this@MainActivity)
                }

                val uiState by viewModel.uiState.collectAsState()

                // Handle permission requests
                LaunchedEffect(uiState.needsPermission) {
                    if (uiState.needsPermission) {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        viewModel.setPermissionRequested()
                    }
                }

                SpeechAssistantScreen(
                    transcribedText = uiState.currentTranscribedText,
                    assistantResponse = uiState.currentAssistantResponse,
                    messages = uiState.messages,
                    isListening = uiState.isListening,
                    errorMessage = uiState.errorMessage,
                    onStartListening = viewModel::startListening,
                    onStopListening = viewModel::stopListening,
                    onSpeakText = viewModel::speakText,
                    onClearMessages = viewModel::clearMessages
                )
            }
        }
    }
}

data class MainUiState(
    val messages: List<Pair<String, String>>,
    val currentTranscribedText: String,
    val currentAssistantResponse: String,
    val isListening: Boolean,
    val errorMessage: String,
    val needsPermission: Boolean
)

class MainViewModel(private val activity: ComponentActivity) : ViewModel() {

    private val _uiState = MutableStateFlow(
        MainUiState(
            messages = emptyList(),
            currentTranscribedText = "",
            currentAssistantResponse = "",
            isListening = false,
            errorMessage = "",
            needsPermission = true
        )
    )
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private var tts: TextToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var isPermissionGranted = false

    fun initializeServices(activity: ComponentActivity) {
        // Initialize TextToSpeech
        tts = TextToSpeech(activity, object : TextToSpeech.OnInitListener {
            override fun onInit(status: Int) {
                if (status == TextToSpeech.SUCCESS) {
                    val result = tts?.setLanguage(Locale.getDefault())
                    if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        _uiState.update { it.copy(errorMessage = "Text-to-Speech language not supported.") }
                    }
                } else {
                    _uiState.update { it.copy(errorMessage = "Could not initialize Text-to-Speech.") }
                }
            }
        })
    }

    fun setPermissionRequested() {
        isPermissionGranted = true
        setupSpeechRecognizer()
        _uiState.update { it.copy(needsPermission = false) }
    }

    private fun setupSpeechRecognizer() {
        try {
            if (SpeechRecognizer.isRecognitionAvailable(activity)) {
                speechRecognizer?.destroy()
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(activity)
                speechRecognizer?.setRecognitionListener(createRecognitionListener())
                Log.d("MainActivity", "SpeechRecognizer initialized successfully")
            } else {
                _uiState.update { it.copy(errorMessage = "Speech recognition is not available on this device.") }
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(errorMessage = "Failed to initialize Speech Recognizer. Try using a physical device instead of emulator.") }
        }
    }

    private fun createRecognitionListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                _uiState.update { it.copy(isListening = true, errorMessage = "") }
            }

            override fun onBeginningOfSpeech() {}

            override fun onRmsChanged(rmsdB: Float) {}

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                _uiState.update { it.copy(isListening = false) }
            }

            override fun onError(error: Int) {
                _uiState.update {
                    it.copy(
                        isListening = false,
                        errorMessage = when (error) {
                            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error. Try using a physical device."
                            SpeechRecognizer.ERROR_CLIENT -> "Client side error. Try restarting the app."
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission denied."
                            SpeechRecognizer.ERROR_NETWORK -> "Network error. Check your internet connection."
                            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout. Check your connection."
                            SpeechRecognizer.ERROR_NO_MATCH -> "I didn't catch that. Please speak clearly and try again."
                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognition service is busy. Try again in a moment."
                            SpeechRecognizer.ERROR_SERVER -> "Server error. Try again later."
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected. Please try speaking again."
                            else -> "Speech recognition error ($error). Try again."
                        }
                    )
                }
            }

            override fun onResults(results: Bundle?) {
                _uiState.update { it.copy(isListening = false) }
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val userText = matches[0]
                    val assistantResponse = generateResponse(userText)

                    _uiState.update {
                        it.copy(
                            currentTranscribedText = userText,
                            currentAssistantResponse = assistantResponse,
                            messages = it.messages + listOf(
                                "user" to userText,
                                "assistant" to assistantResponse
                            )
                        )
                    }
                    speakText(assistantResponse)
                } else {
                    _uiState.update { it.copy(errorMessage = "No speech was recognized. Please try again.") }
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    _uiState.update { it.copy(currentTranscribedText = matches[0]) }
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
    }

    fun startListening() {
        if (!isPermissionGranted) {
            _uiState.update { it.copy(needsPermission = true) }
            return
        }

        if (speechRecognizer == null) {
            _uiState.update { it.copy(errorMessage = "Speech recognizer not initialized. Try restarting the app.") }
            return
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Say something...")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, activity.packageName)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 3000)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1000)
        }

        try {
            speechRecognizer?.startListening(intent)
        } catch (e: SecurityException) {
            _uiState.update { it.copy(errorMessage = "Microphone permission not granted.") }
        } catch (e: Exception) {
            _uiState.update { it.copy(errorMessage = "Failed to start listening: ${e.message}") }
        }
    }

    fun stopListening() {
        speechRecognizer?.stopListening()
        _uiState.update { it.copy(isListening = false) }
    }

    private fun generateResponse(userInput: String): String {
        val input = userInput.lowercase(Locale.getDefault()).trim()
        return when {
            input.contains("hello") || input.contains("hi") || input.contains("hey") ->
                "Hello there! How can I help you today?"
            input.contains("time") ->
                "The current time is ${java.text.SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())}."
            input.contains("date") ->
                "Today is ${java.text.SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault()).format(Date())}."
            input.contains("joke") ->
                "Why did the scarecrow win an award? Because he was outstanding in his field!"
            input.contains("weather") ->
                "I can't check the weather right now, but I hope it's nice where you are!"
            input.contains("thank") ->
                "You're very welcome! Is there anything else I can help you with?"
            input.contains("bye") || input.contains("goodbye") ->
                "Goodbye! Have a great day!"
            else ->
                "I heard you say: '$userInput'. How can I help you with that?"
        }
    }

    fun speakText(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tts_utterance")
    }

    fun clearMessages() {
        _uiState.update {
            it.copy(
                messages = emptyList(),
                currentTranscribedText = "",
                currentAssistantResponse = ""
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        tts?.stop()
        tts?.shutdown()
        speechRecognizer?.destroy()
    }
}

