package com.example.talkmate

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.talkmate.ai.AssistantResponse
import com.example.talkmate.ai.SmartAssistant
import com.example.talkmate.ui.components.CameraOCRScreen
import com.example.talkmate.ui.components.SpeechAssistantScreen
import com.example.talkmate.ui.theme.TTSSTTAppTheme
import com.example.talkmate.utils.OCRHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.*

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
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

                LaunchedEffect(uiState.needsPermission) {
                    if (uiState.needsPermission) {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        viewModel.setPermissionRequested()
                    }
                }

                LaunchedEffect(uiState.pendingIntent) {
                    uiState.pendingIntent?.let { intent ->
                        try {
                            startActivity(intent)
                            viewModel.clearPendingIntent()
                        } catch (e: Exception) {
                            viewModel.setErrorMessage("Could not perform action: ${e.message}")
                        }
                    }
                }

                if (uiState.showCamera) {
                    CameraOCRScreen(
                        onTextExtracted = { extractedText ->
                            viewModel.processExtractedText(extractedText)
                        },
                        onClose = { viewModel.closeCamera() },
                        onSpeakText = viewModel::speakText
                    )
                } else {
                    SpeechAssistantScreen(
                        transcribedText = uiState.currentTranscribedText,
                        assistantResponse = uiState.currentAssistantResponse,
                        messages = uiState.messages,
                        isListening = uiState.isListening,
                        errorMessage = uiState.errorMessage,
                        onStartListening = viewModel::startListening,
                        onStopListening = viewModel::stopListening,
                        onSpeakText = viewModel::speakText,
                        onClearMessages = viewModel::clearMessages,
                        onOpenCamera = viewModel::openCamera
                    )
                }
            }
        }
    }
}

data class MainUiState(
    val messages: List<Pair<String, String>>,
    val currentTranscribedText: String,
    val currentAssistantResponse: String,
    val isListening: Boolean,
    val isSpeaking: Boolean = false,
    val errorMessage: String,
    val needsPermission: Boolean,
    val showCamera: Boolean = false,
    val pendingIntent: Intent? = null
)

class MainViewModel(private val activity: ComponentActivity) : ViewModel() {

    private val _uiState = MutableStateFlow(
        MainUiState(
            messages = emptyList(),
            currentTranscribedText = "",
            currentAssistantResponse = "",
            isListening = false,
            isSpeaking = false,
            errorMessage = "",
            needsPermission = true,
            showCamera = false,
            pendingIntent = null
        )
    )
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private var tts: TextToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var isPermissionGranted = false
    private var isTtsInitialized = false
    private val smartAssistant = SmartAssistant(activity)
    private val ocrHelper = OCRHelper()

    fun initializeServices(activity: ComponentActivity) {
        tts = TextToSpeech(activity) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.let { ttsInstance ->
                    val result = ttsInstance.setLanguage(Locale.getDefault())
                    if (result == TextToSpeech.LANG_MISSING_DATA ||
                        result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        _uiState.update {
                            it.copy(errorMessage = "Text-to-Speech language not supported.")
                        }
                    } else {
                        isTtsInitialized = true
                        Log.d("MainActivity", "TTS initialized successfully")

                        ttsInstance.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                            override fun onStart(utteranceId: String?) {
                                _uiState.update { it.copy(isSpeaking = true) }
                                Log.d("MainActivity", "TTS started speaking")
                            }

                            override fun onDone(utteranceId: String?) {
                                _uiState.update { it.copy(isSpeaking = false) }
                                Log.d("MainActivity", "TTS finished speaking")
                            }

                            override fun onError(utteranceId: String?) {
                                _uiState.update {
                                    it.copy(
                                        isSpeaking = false,
                                        errorMessage = "Speech synthesis failed"
                                    )
                                }
                                Log.e("MainActivity", "TTS error")
                            }
                        })
                    }
                }
            } else {
                _uiState.update {
                    it.copy(errorMessage = "Could not initialize Text-to-Speech.")
                }
            }
        }
    }

    fun setPermissionRequested() {
        isPermissionGranted = true
        setupSpeechRecognizer()
        _uiState.update { it.copy(needsPermission = false) }
    }

    fun openCamera() {
        _uiState.update { it.copy(showCamera = true) }
    }

    fun closeCamera() {
        _uiState.update { it.copy(showCamera = false) }
    }

    fun processExtractedText(extractedText: String) {
        viewModelScope.launch {
            Log.d("MainActivity", "Processing extracted text: $extractedText")

            _uiState.update {
                it.copy(
                    currentTranscribedText = extractedText,
                    messages = it.messages + listOf("user" to "Text from image: $extractedText"),
                    showCamera = false
                )
            }

            val response = smartAssistant.processImageText(extractedText)
            _uiState.update {
                it.copy(
                    currentAssistantResponse = response,
                    messages = it.messages + listOf("assistant" to response)
                )
            }

            speakText(response)
        }
    }

    fun setErrorMessage(message: String) {
        _uiState.update { it.copy(errorMessage = message) }
    }

    fun clearPendingIntent() {
        _uiState.update { it.copy(pendingIntent = null) }
    }

    private fun setupSpeechRecognizer() {
        try {
            if (SpeechRecognizer.isRecognitionAvailable(activity)) {
                speechRecognizer?.destroy()
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(activity)
                speechRecognizer?.setRecognitionListener(createRecognitionListener())
                Log.d("MainActivity", "SpeechRecognizer initialized successfully")
            } else {
                _uiState.update {
                    it.copy(errorMessage = "Speech recognition is not available on this device.")
                }
            }
        } catch (e: Exception) {
            _uiState.update {
                it.copy(errorMessage = "Failed to initialize Speech Recognizer: ${e.message}")
            }
        }
    }

    private fun createRecognitionListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                _uiState.update { it.copy(isListening = true, errorMessage = "") }
                Log.d("MainActivity", "Ready for speech")
            }

            override fun onBeginningOfSpeech() {
                Log.d("MainActivity", "Speech began")
            }

            override fun onRmsChanged(rmsdB: Float) {}

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                _uiState.update { it.copy(isListening = false) }
                Log.d("MainActivity", "Speech ended")
            }

            override fun onError(error: Int) {
                val errorMsg = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                    SpeechRecognizer.ERROR_CLIENT -> "Client error"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission denied"
                    SpeechRecognizer.ERROR_NETWORK -> "Network error"
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                    SpeechRecognizer.ERROR_NO_MATCH -> "I didn't catch that. Please try again."
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognition service busy"
                    SpeechRecognizer.ERROR_SERVER -> "Server error"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected"
                    else -> "Speech recognition error ($error)"
                }
                _uiState.update { it.copy(isListening = false, errorMessage = errorMsg) }
                Log.e("MainActivity", "Speech error: $errorMsg")
            }

            override fun onResults(results: Bundle?) {
                _uiState.update { it.copy(isListening = false) }
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val userText = matches[0]
                    Log.d("MainActivity", "Recognized: $userText")

                    viewModelScope.launch {
                        val response = smartAssistant.processCommand(userText)

                        _uiState.update {
                            it.copy(
                                currentTranscribedText = userText,
                                messages = it.messages + listOf("user" to userText)
                            )
                        }

                        handleAssistantResponse(response)
                    }
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

    private fun handleAssistantResponse(response: AssistantResponse) = when (response) {
        is AssistantResponse.Text -> {
            val value = _uiState.update {
                it.copy(
                    currentAssistantResponse = response.message,
                    messages = it.messages + listOf("assistant" to response.message)
                )
            }.speakText(response.message)
        }

        is AssistantResponse.Action -> {
            _uiState.update {
                it.copy(
                    currentAssistantResponse = response.message,
                    messages = it.messages + listOf("assistant" to response.message),
                    pendingIntent = response.intent
                )
            }
            speakText(response.message)
        }
    }

    fun startListening() {
        if (!isPermissionGranted) {
            _uiState.update { it.copy(needsPermission = true) }
            return
        }

        if (_uiState.value.isSpeaking) {
            tts?.stop()
            _uiState.update { it.copy(isSpeaking = false) }
        }

        if (speechRecognizer == null) {
            _uiState.update {
                it.copy(errorMessage = "Speech recognizer not initialized. Try restarting.")
            }
            return
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        try {
            speechRecognizer?.startListening(intent)
            Log.d("MainActivity", "Started listening")
        } catch (e: Exception) {
            _uiState.update {
                it.copy(errorMessage = "Failed to start listening: ${e.message}")
            }
        }
    }

    fun stopListening() {
        speechRecognizer?.stopListening()
        _uiState.update { it.copy(isListening = false) }
    }

    fun speakText(text: String) {
        if (!isTtsInitialized) {
            Log.w("MainActivity", "TTS not initialized yet")
            _uiState.update { it.copy(errorMessage = "Text-to-Speech not ready") }
            return
        }

        if (text.isBlank()) {
            Log.w("MainActivity", "Attempted to speak empty text")
            return
        }

        Log.d("MainActivity", "Speaking: $text")

        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tts_${System.currentTimeMillis()}")
    }

    fun clearMessages() {
        tts?.stop()
        _uiState.update {
            it.copy(
                messages = emptyList(),
                currentTranscribedText = "",
                currentAssistantResponse = "",
                errorMessage = "",
                isSpeaking = false
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        tts?.stop()
        tts?.shutdown()
        speechRecognizer?.destroy()
        ocrHelper.cleanup()
    }
}

private fun Unit.speakText(message: String) {}
